import * as React from "react";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper, Stack, TextField, Typography } from "@mui/material";
import ApartmentOutlinedIcon from "@mui/icons-material/ApartmentOutlined";
import ArticleOutlinedIcon from "@mui/icons-material/ArticleOutlined";
import CallOutlinedIcon from "@mui/icons-material/CallOutlined";
import CurrencyRupeeOutlinedIcon from "@mui/icons-material/CurrencyRupeeOutlined";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import LanguageOutlinedIcon from "@mui/icons-material/LanguageOutlined";
import MedicalServicesOutlinedIcon from "@mui/icons-material/MedicalServicesOutlined";
import PendingActionsOutlinedIcon from "@mui/icons-material/PendingActionsOutlined";
import PersonOutlineOutlinedIcon from "@mui/icons-material/PersonOutlineOutlined";
import PhotoCameraIcon from "@mui/icons-material/PhotoCamera";
import PhotoLibraryOutlinedIcon from "@mui/icons-material/PhotoLibraryOutlined";
import PublishOutlinedIcon from "@mui/icons-material/PublishOutlined";
import ScheduleOutlinedIcon from "@mui/icons-material/ScheduleOutlined";
import TaskAltOutlinedIcon from "@mui/icons-material/TaskAltOutlined";
import TravelExploreOutlinedIcon from "@mui/icons-material/TravelExploreOutlined";
import VisibilityOutlinedIcon from "@mui/icons-material/VisibilityOutlined";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import { DoctorCard } from "../../components/DiscoveryComponents";
import { LandingPageRenderer } from "../../components/landing/LandingPageRenderer";
import { PublicMediaImage } from "../../components/landing/PublicMediaImage";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { DISCOVER_ROUTES } from "../../routes";
import { buildPublicAddressView, resolveClinicEstablishedYear } from "../../utils/publicProfileFormatting";
import { getProviderConsentPresentation } from "../../utils/providerConsentPresentation";
import {
  ProviderEditorFooter,
  ProviderEditorSectionCard,
  ProviderFeeEditor,
  ProviderTagListEditor,
  ProviderWeeklyScheduleEditor,
  PublicAddressPreview,
  normalizeEditorList,
  normalizeFeeValue,
  normalizeWeeklyScheduleContent,
} from "../../components/provider-profile-editor/ProviderProfileEditorControls";
import { HospitalDoctorManagementSection } from "./HospitalDoctorManagementSection";
import {
  createProviderPublicProfileDraft,
  loadProviderPublicProfileDraft,
  loadProviderPublicProfileDraftPreview,
  loadProviderPublicProfileModeration,
  saveProviderPublicProfileDraftSection,
  uploadProviderPublicProfileDraftMedia,
  providerPublicProfileDraftMediaContentPath,
  submitProviderPublicProfileForReview,
  type ProviderPublicProfileDraft,
  type ProviderPublicProfileModeration,
  type ProviderPublicProfileDraftMediaUploadResponse,
  type ProviderPublicProfileDraftMediaType,
} from "../../api/providerPublicProfileDraft";
import {
  fetchPublicJson,
  type PublicDoctorDetailResponse,
  type PublicDoctorSummaryResponse,
} from "../../api/publicCatalog";
import {
  loadProviderHospitalDoctors,
  type ProviderHospitalDoctorAssociationResponse,
} from "../../api/providerHospitalDoctors";
import type { LandingPageRenderable, LandingProfile, LandingSnapshot, LandingTheme } from "../../api/providerLandingPage";

const SECTION_ORDER = ["overview", "about", "medical_team", "contact", "services", "specialities", "facilities", "timings", "fees", "languages", "media", "seo", "preview", "readiness"];

const SERVICE_SUGGESTIONS = [
  "General Consultation",
  "Family Medicine",
  "Internal Medicine",
  "Diabetes Management",
  "Preventive Health Check-ups",
  "Vaccination",
  "Minor Procedures",
  "Women’s Health",
  "Pediatric Care",
];

const SPECIALITY_SUGGESTIONS = [
  "General Medicine",
  "Family Medicine",
  "Internal Medicine",
  "Pediatrics",
  "Gynecology",
  "Dermatology",
  "ENT",
  "Cardiology",
  "Orthopedics",
];

const FACILITY_SUGGESTIONS = [
  "Wheelchair accessibility",
  "Accessible washroom",
  "Pharmacy",
  "Laboratory",
  "Imaging",
  "Parking",
  "Digital payments",
  "Waiting area",
  "Emergency first aid",
];

const LANGUAGE_SUGGESTIONS = [
  "English",
  "Hindi",
  "Marathi",
  "Gujarati",
  "Kannada",
  "Tamil",
  "Telugu",
  "Punjabi",
];

function formatDateTime(value?: string | null) {
  if (!value) {
    return "Not yet saved";
  }
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function normalizeList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean);
  }
  if (typeof value === "string") {
    return value.split(",").map((item) => item.trim()).filter(Boolean);
  }
  return [];
}

function listValue(value: unknown) {
  return normalizeList(value).join(", ");
}

function textValue(value: unknown): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return null;
}

function sectionContent(draft: ProviderPublicProfileDraft | null, key: string) {
  return draft?.sections.find((section) => section.key === key)?.content ?? {};
}

function updateSectionContent(draft: ProviderPublicProfileDraft, key: string, updater: (current: Record<string, unknown>) => Record<string, unknown>) {
  return {
    ...draft,
    sections: draft.sections.map((section) => section.key === key ? { ...section, content: updater(section.content) } : section),
  };
}

function sectionRoute(profileReference: string, section: string) {
  return DISCOVER_ROUTES.providerPublicProfileDraft.path
    .replace(":profileReference", encodeURIComponent(profileReference))
    .replace(":section", encodeURIComponent(section));
}

function sectionLabel(key: string) {
  switch (key) {
    case "overview":
      return "Overview";
    case "about":
      return "About";
    case "medical_team":
      return "Doctors / Medical Team";
    case "contact":
      return "Contact";
    case "services":
      return "Services";
    case "specialities":
      return "Specialities";
    case "facilities":
      return "Facilities";
    case "timings":
      return "Timings";
    case "fees":
      return "Fees";
    case "languages":
      return "Languages";
    case "media":
      return "Media";
    case "seo":
      return "SEO";
    case "preview":
      return "Preview";
    case "readiness":
      return "Readiness";
    default:
      return key.replaceAll("_", " ").replace(/\b\w/g, (char) => char.toUpperCase());
  }
}

function sectionNavigatorMeta(key: string, completenessPercentage: number, missingFields: string[], invalidFields: string[]) {
  const hasIssue = missingFields.some((field) => missingFieldSection(field) === key) || invalidFields.some((field) => missingFieldSection(field) === key);
  const isOptional = key === "fees" || key === "seo";
  const isPreview = key === "preview";
  const isReadiness = key === "readiness";
  const sectionIcon = (() => {
    switch (key) {
      case "overview":
        return <ArticleOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "about":
        return <InfoOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "medical_team":
        return <MedicalServicesOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "contact":
        return <CallOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "services":
        return <MedicalServicesOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "specialities":
        return <MedicalServicesOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "facilities":
        return <ApartmentOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "timings":
        return <ScheduleOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "fees":
        return <CurrencyRupeeOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "languages":
        return <LanguageOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "media":
        return <PhotoLibraryOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "seo":
        return <TravelExploreOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "preview":
        return <VisibilityOutlinedIcon fontSize="small" aria-hidden="true" />;
      case "readiness":
        return <TaskAltOutlinedIcon fontSize="small" aria-hidden="true" />;
      default:
        return <ArticleOutlinedIcon fontSize="small" aria-hidden="true" />;
    }
  })();
  const icon = hasIssue
    ? <WarningAmberOutlinedIcon fontSize="small" aria-hidden="true" />
    : !isOptional && !isPreview && !isReadiness
      ? <TaskAltOutlinedIcon fontSize="small" aria-hidden="true" />
      : sectionIcon;
  const badge = hasIssue ? "Needs attention" : isOptional ? "Optional" : isReadiness ? `${completenessPercentage}%` : null;
  const state = hasIssue ? "issue" : isOptional ? "optional" : isPreview ? "preview" : isReadiness ? "readiness" : "complete";
  return { icon, badge, state, hasIssue };
}

type DraftMediaMetadata = {
  mediaType?: string | null;
  originalFilename?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  uploadedAt?: string | null;
  storageKey?: string | null;
};

function mediaSectionContent(draft: ProviderPublicProfileDraft | null) {
  return sectionContent(draft, "media");
}

function mediaReferences(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => typeof item === "string" ? item.trim() : "").filter(Boolean);
}

function mediaMetadataMap(media: Record<string, unknown>) {
  const value = media.mediaMetadataByDocumentId;
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, DraftMediaMetadata> : {};
}

function mediaAltTextMap(media: Record<string, unknown>) {
  const value = media.galleryAltTextByDocumentId;
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, string> : {};
}

function mediaPrimaryReference(media: Record<string, unknown>) {
  return typeof media.primaryGalleryDocumentId === "string" && media.primaryGalleryDocumentId.trim() ? media.primaryGalleryDocumentId.trim() : null;
}

function mediaDocumentReference(media: Record<string, unknown>, key: "logoDocumentId" | "coverDocumentId") {
  return typeof media[key] === "string" && media[key].trim() ? media[key].trim() : null;
}

function mediaContentPath(publicProfileReference: string, mediaReference: string) {
  return providerPublicProfileDraftMediaContentPath(publicProfileReference, mediaReference);
}

function previewHospitalDoctorSummary(
  detail: PublicDoctorDetailResponse,
  hospitalDisplayName: string,
): PublicDoctorSummaryResponse {
  return {
    publicDoctorId: detail.publicDoctorId,
    doctorSlug: detail.doctorSlug,
    publicPath: detail.publicPath ?? undefined,
    doctorDisplayName: detail.doctorDisplayName,
    photoUrl: detail.photoUrl,
    contactPhone: detail.contactPhone ?? null,
    speciality: detail.primarySpeciality ?? detail.specialities[0] ?? null,
    yearsOfExperience: detail.yearsOfExperience,
    consultationFee: null,
    languages: detail.languages ?? [],
    clinicDisplayName: detail.clinics[0]?.clinicDisplayName ?? hospitalDisplayName,
    clinicSlug: detail.clinics.length === 1 ? detail.clinics[0].clinicSlug : "",
    area: detail.area ?? null,
    city: detail.city ?? null,
    bookingMode: detail.bookingMode ?? null,
    subtitle: detail.subtitle ?? null,
    summary: detail.summary ?? detail.biography ?? null,
    availableToday: detail.availableToday,
    nextAvailableSlotSummary: detail.nextAvailableSlots[0] ?? null,
    distanceKm: null,
  };
}

function sectionMissingLabel(field: string) {
  switch (field) {
    case "displayName":
      return "Edit display name";
    case "description":
      return "Add a description";
    case "addressLine1":
      return "Edit contact address";
    case "city":
      return "Edit contact location";
    case "state":
      return "Edit contact location";
    case "country":
      return "Edit contact location";
    case "publicContact":
      return "Add a public contact method";
    case "specialities":
      return "Add specialities";
    case "services":
      return "Add services";
    case "timings":
      return "Add timings";
    case "logo":
      return "Upload logo";
    case "cover":
      return "Upload cover image";
    default:
      return field.replaceAll("_", " ");
  }
}

function missingFieldSection(field: string) {
  switch (field) {
    case "displayName":
    case "description":
    case "establishedYear":
      return "about";
    case "addressLine1":
    case "city":
    case "state":
    case "country":
    case "publicContact":
      return "contact";
    case "specialities":
      return "specialities";
    case "services":
      return "services";
    case "timings":
      return "timings";
    case "logo":
    case "cover":
      return "media";
    default:
      return "overview";
  }
}

function readableLifecycleLabel(code: string) {
  switch (code) {
    case "DRAFT":
      return "Draft";
    case "DRAFT_INCOMPLETE":
      return "Draft";
    case "READY_FOR_REVIEW":
      return "Content Ready";
    case "NOT_SUBMITTED":
      return "Draft";
    case "INCOMPLETE":
      return "Profile needs more information";
    case "READY":
      return "Content Ready";
    case "PROFILE_INCOMPLETE":
      return "Profile needs more information";
    case "TENANT_CONSENT_REQUIRED":
      return "Clinic has not enabled Discover publishing yet";
    case "PUBLISHED":
      return "Published";
    default:
      return code.replaceAll("_", " ").replace(/\b\w/g, (char) => char.toUpperCase());
  }
}

function publicationBlockSummary(readiness: ProviderPublicProfileDraft["readiness"], moderation: ProviderPublicProfileModeration | null) {
  const blockers: string[] = [];
  if (readiness.ready && readiness.missingMandatoryFields.length === 0 && readiness.invalidFields.length === 0) {
    blockers.push("Content complete");
  } else if (readiness.missingMandatoryFields.length || readiness.invalidFields.length) {
    blockers.push("Profile needs more information");
  }
  if (!blockers.length && moderation?.submissionEligible) {
    blockers.push("Content ready");
  }
  return blockers.length ? blockers.join(" · ") : "Submission becomes available once all required items are completed.";
}

function submissionBlockerMessage(blockers: readonly string[] | null | undefined) {
  const blocker = blockers?.[0];
  switch (blocker) {
    case "OWNERSHIP_NOT_VERIFIED":
      return "Verified ownership is required before submitting for platform review.";
    case "PROFILE_INCOMPLETE":
      return "Complete the remaining profile requirements before submitting.";
    case "TENANT_CONSENT_REQUIRED":
      return "Enable Discover participation before submitting for platform review.";
    case "ACTIVE_SUBMISSION_EXISTS":
      return "A platform review is already in progress.";
    case "RESUBMISSION_REQUIRED":
      return "Make a fresh draft change before resubmitting.";
    default:
      return "Submission blocked";
  }
}

function publicationCardMessage(
  readiness: ProviderPublicProfileDraft["readiness"],
  moderation: ProviderPublicProfileModeration | null,
  tenantConsentStatus: string | null | undefined,
) {
  const consent = getProviderConsentPresentation({ tenantConsentStatus, submissionEligible: moderation?.submissionEligible });
  if (consent.isBlocked) {
    return "Enable Discover participation before submitting for platform review.";
  }
  if (consent.visible) {
    return consent.message;
  }
  if (moderation?.allowedActions.includes("SUBMIT_FOR_REVIEW")) {
    return "Ready to submit for platform review.";
  }
  if (!moderation?.submissionEligible) {
    return submissionBlockerMessage(moderation?.submissionBlockers);
  }
  return publicationBlockSummary(readiness, moderation);
}

function missingFieldCategory(field: string) {
  switch (field) {
    case "displayName":
    case "description":
    case "establishedYear":
      return "About";
    case "addressLine1":
    case "addressLine2":
    case "area":
    case "city":
    case "state":
    case "country":
    case "postalCode":
    case "publicContact":
      return "Contact";
    case "specialities":
      return "Specialities";
    case "services":
      return "Services";
    case "timings":
      return "Timings";
    case "logo":
    case "cover":
      return "Media";
    case "slug":
      return "SEO";
    default:
      return "Other";
  }
}

function missingFieldCategorySection(category: string) {
  switch (category) {
    case "About":
      return "about";
    case "Contact":
      return "contact";
    case "Specialities":
      return "specialities";
    case "Services":
      return "services";
    case "Timings":
      return "timings";
    case "Media":
      return "media";
    case "SEO":
      return "seo";
    default:
      return "overview";
  }
}

function missingFieldChipLabel(field: string, isHospitalProfile: boolean) {
  switch (field) {
    case "displayName":
      return isHospitalProfile ? "Add hospital name" : "Add clinic name";
    case "description":
      return "Add description";
    case "addressLine1":
    case "addressLine2":
    case "area":
    case "city":
    case "state":
    case "country":
    case "postalCode":
      return "Edit contact location";
    case "publicContact":
      return "Add contact methods";
    case "specialities":
      return "Add specialities";
    case "services":
      return "Add services";
    case "timings":
      return "Add timings";
    case "logo":
      return "Upload logo";
    case "cover":
      return "Upload cover image";
    case "slug":
      return "Add public slug";
    default:
      return field.replaceAll("_", " ");
  }
}

function draftToLandingPage(draft: ProviderPublicProfileDraft): { page: LandingPageRenderable; snapshot: LandingSnapshot } {
  const about = sectionContent(draft, "about");
  const contact = sectionContent(draft, "contact");
  const services = normalizeEditorList(sectionContent(draft, "services").items);
  const specialities = normalizeEditorList(sectionContent(draft, "specialities").items);
  const languages = normalizeEditorList(sectionContent(draft, "languages").items);
  const facilities = normalizeEditorList(sectionContent(draft, "facilities").items);
  const timings = sectionContent(draft, "timings");
  const media = mediaSectionContent(draft);
  const seo = sectionContent(draft, "seo");
  const logoReference = mediaDocumentReference(media, "logoDocumentId");
  const coverReference = mediaDocumentReference(media, "coverDocumentId");
  const galleryReferences = mediaReferences(media.gallery);
  const metadataByReference = mediaMetadataMap(media);
  const altTextByReference = mediaAltTextMap(media);
  const primaryReference = mediaPrimaryReference(media);
  const displayName = textValue(about.displayName) || draft.displayName || "Public profile";
  const slug = textValue(seo.slug) || draft.canonicalSlug || displayName.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  const clinicEstablishedYear = draft.publicProfileType === "CLINIC"
    ? resolveClinicEstablishedYear(about.establishedYear, about.registrationNumber)
    : null;
  const doctorYearsOfExperience = null;
  const addressView = buildPublicAddressView({
    addressLine1: textValue(contact.addressLine1),
    addressLine2: textValue(contact.addressLine2),
    area: textValue(contact.area),
    city: textValue(contact.city),
    state: textValue(contact.state),
    country: textValue(contact.country),
    postalCode: textValue(contact.postalCode),
  });
  const gallery = galleryReferences.map((reference, index) => ({
    documentId: reference,
    caption: altTextByReference[reference] || metadataByReference[reference]?.originalFilename || `Gallery image ${index + 1}`,
  }));
  const galleryImageUrls = galleryReferences.map((reference) => mediaContentPath(draft.publicProfileReference, reference));
  const profile: LandingProfile = {
    providerId: draft.publicProfileReference,
    providerType: draft.publicProfileType,
    referenceNumber: draft.publicProfileReference,
    displayName,
    legalName: null,
    canonicalSlug: slug,
    summary: textValue(about.shortTagline) || draft.displayName || "",
    biography: textValue(about.description) || "",
    qualification: textValue(about.qualification),
    medicalCouncil: textValue(about.medicalCouncil),
    yearsOfExperience: doctorYearsOfExperience,
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
    locations: [
      {
        label: displayName,
        addressLine1: textValue(contact.addressLine1),
        addressLine2: textValue(contact.addressLine2),
        address: addressView.singleLine || null,
        area: textValue(contact.area),
        city: textValue(contact.city),
        state: textValue(contact.state),
        country: textValue(contact.country),
        pinCode: textValue(contact.postalCode),
        postalCode: textValue(contact.postalCode),
        workingHours: normalizeWeeklyScheduleContent(timings).intervals.length ? "Weekly timings configured" : null,
        parkingAvailable: facilities.includes("parking"),
        accessibilityAvailable: facilities.includes("wheelchair access"),
        latitude: null,
        longitude: null,
      },
    ],
    gallery,
    galleryImageUrls,
    imageUrl: logoReference ? mediaContentPath(draft.publicProfileReference, logoReference) : null,
    coverUrl: coverReference ? mediaContentPath(draft.publicProfileReference, coverReference) : null,
    logoUrl: logoReference ? mediaContentPath(draft.publicProfileReference, logoReference) : null,
    contactPhone: textValue(contact.publicPhone),
    contactEmail: textValue(contact.publicEmail),
    website: textValue(contact.website),
    city: textValue(contact.city),
    area: textValue(contact.area),
    state: textValue(contact.state),
    country: textValue(contact.country),
    primarySpeciality: specialities[0] || null,
    ownership: draft.ownershipStatus,
    hospitalType: null,
    medicalDirector: null,
    beds: null,
    emergencyAvailable: Boolean(about.emergencyAvailability),
    establishedYear: clinicEstablishedYear,
    registrationNumber: textValue(about.registrationNumber),
    clinicPhilosophy: textValue(about.philosophy),
    reviewsComingSoon: true,
    publishedAt: draft.updatedAt,
    publishedVersionNumber: draft.currentVersion,
    slug,
    previousSlug: null,
    canonical: true,
    publicPath: draft.publicProfilePath || `/discover/${draft.publicProfileType.toLowerCase()}s/${slug}`,
    weeklyTimings: normalizeWeeklyScheduleContent(timings).intervals,
    timezone: textValue(timings.timezone),
  };
  const snapshot: LandingSnapshot = {
    templateKey: "provider-draft",
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
  return { page: { displayName, canonicalSlug: slug, publicPath: profile.publicPath, profile, draft: snapshot, publishedSnapshot: null }, snapshot };
}

export function ProviderPublicProfileDraftPage() {
  const params = useParams<{ profileReference?: string; section?: string }>();
  const profileReference = params.profileReference?.trim() ?? "";
  const section = (params.section || "overview").toLowerCase();
  const [draft, setDraft] = useState<ProviderPublicProfileDraft | null>(null);
  const [previewDraft, setPreviewDraft] = useState<ProviderPublicProfileDraft | null>(null);
  const [previewHospitalDoctors, setPreviewHospitalDoctors] = useState<PublicDoctorSummaryResponse[]>([]);
  const [previewHospitalDoctorsLoading, setPreviewHospitalDoctorsLoading] = useState(false);
  const [previewHospitalDoctorsError, setPreviewHospitalDoctorsError] = useState<string | null>(null);
  const [moderation, setModeration] = useState<ProviderPublicProfileModeration | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploadingMedia, setUploadingMedia] = useState<string | null>(null);
  const [mediaMessage, setMediaMessage] = useState<string | null>(null);
  const [removeTarget, setRemoveTarget] = useState<{ documentReference: string; label: string } | null>(null);
  const [copyStatus, setCopyStatus] = useState<string | null>(null);

  useEffect(() => {
    if (!profileReference) {
      return;
    }
    let active = true;
    const bootstrap = async () => {
      setLoading(true);
      setError(null);
      setDraft(null);
      setPreviewDraft(null);
      setModeration(null);
      try {
        const nextModeration = await loadProviderPublicProfileModeration(profileReference);
        if (!active) {
          return;
        }
        setModeration(nextModeration);
        if (["SUBMITTED", "UNDER_REVIEW"].includes(nextModeration.moderationStatus)) {
          return;
        }
        try {
          const createdDraft = await createProviderPublicProfileDraft(profileReference);
          if (active) {
            setDraft(createdDraft);
          }
        } catch (createError) {
          try {
            const loadedDraft = await loadProviderPublicProfileDraft(profileReference);
            if (active) {
              setDraft(loadedDraft);
            }
          } catch (loadError) {
            if (active) {
              setError(loadError instanceof Error ? loadError.message : createError instanceof Error ? createError.message : "Could not load the draft.");
            }
          }
        }
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Could not load the draft.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };
    void bootstrap();
    return () => {
      active = false;
    };
  }, [profileReference]);

  useEffect(() => {
    if (section === "preview" && profileReference) {
      loadProviderPublicProfileDraftPreview(profileReference).then(setPreviewDraft).catch(() => setPreviewDraft(null));
    }
  }, [profileReference, section]);

  useEffect(() => {
    const previewProfileType = draft?.publicProfileType ?? previewDraft?.publicProfileType;
    if (section !== "preview" || previewProfileType !== "HOSPITAL" || !profileReference) {
      setPreviewHospitalDoctors([]);
      setPreviewHospitalDoctorsLoading(false);
      setPreviewHospitalDoctorsError(null);
      return;
    }

    let active = true;
    setPreviewHospitalDoctors([]);
    setPreviewHospitalDoctorsLoading(true);
    setPreviewHospitalDoctorsError(null);

    void loadProviderHospitalDoctors(profileReference)
      .then(async (associations) => {
        const previewDoctors = await Promise.all(associations.map(async (association: ProviderHospitalDoctorAssociationResponse) => {
          if (!association.publicPath) {
            return null;
          }
          try {
            const detail = await fetchPublicJson<PublicDoctorDetailResponse>(association.publicPath);
            return previewHospitalDoctorSummary(detail, draft?.displayName ?? "This hospital");
          } catch {
            return null;
          }
        }));
        if (active) {
          setPreviewHospitalDoctors(previewDoctors.filter((item): item is PublicDoctorSummaryResponse => Boolean(item)));
        }
      })
      .catch((loadError: unknown) => {
        if (active) {
          setPreviewHospitalDoctorsError(loadError instanceof Error ? loadError.message : "Could not load draft doctors.");
        }
      })
      .finally(() => {
        if (active) {
          setPreviewHospitalDoctorsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [draft?.displayName, draft?.publicProfileType, previewDraft?.publicProfileType, profileReference, section]);

  useEffect(() => {
    if (!profileReference) {
      return;
    }
    loadProviderPublicProfileModeration(profileReference).then(setModeration).catch(() => setModeration(null));
  }, [profileReference, draft?.currentVersion]);

  const currentSection = useMemo(() => SECTION_ORDER.includes(section) ? section : "overview", [section]);
  const activeDraft = draft ?? previewDraft;
  const currentMedia = mediaSectionContent(activeDraft);
  const currentGalleryReferences = mediaReferences(currentMedia.gallery);
  const currentGalleryMetadata = { ...mediaMetadataMap(currentMedia) };
  const currentGalleryAltTexts = { ...mediaAltTextMap(currentMedia) };
  const currentLogoReference = mediaDocumentReference(currentMedia, "logoDocumentId");
  const currentCoverReference = mediaDocumentReference(currentMedia, "coverDocumentId");
  const currentPrimaryGalleryReference = mediaPrimaryReference(currentMedia);
  const mediaUploadAccept = ".png,.jpg,.jpeg,.webp,image/png,image/jpeg,image/webp";

  async function saveCurrentSection() {
    if (!draft || !profileReference) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await saveProviderPublicProfileDraftSection(profileReference, {
        sectionKey: currentSection,
        content: sectionContent(draft, currentSection),
        expectedVersion: draft.currentVersion,
        changeSummary: `Updated ${currentSection}`,
      });
      setDraft(updated);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Could not save the draft.");
    } finally {
      setSaving(false);
    }
  }

  async function submitCurrentDraft() {
    if (!profileReference) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const updated = await submitProviderPublicProfileForReview(profileReference);
      setModeration(updated);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not submit the draft.");
    } finally {
      setSubmitting(false);
    }
  }

  function updateSection(next: Record<string, unknown>) {
    if (!draft) {
      return;
    }
    setDraft(updateSectionContent(draft, currentSection, () => next));
  }

  async function persistMediaSection(nextMedia: Record<string, unknown>, changeSummary: string) {
    if (!draft || !profileReference) {
      return;
    }
    try {
      setSaving(true);
      setError(null);
      const updated = await saveProviderPublicProfileDraftSection(profileReference, {
        sectionKey: "media",
        content: nextMedia,
        expectedVersion: draft.currentVersion,
        changeSummary,
      });
      setDraft(updated);
      setMediaMessage("Media changes saved.");
    } catch (mediaSaveError) {
      setError(mediaSaveError instanceof Error ? mediaSaveError.message : "Could not save media changes.");
    } finally {
      setSaving(false);
    }
  }

  async function handleMediaUpload(mediaType: ProviderPublicProfileDraftMediaType, files: FileList | null) {
    const selectedFiles = Array.from(files ?? []).filter(Boolean);
    if (!selectedFiles.length || !profileReference) {
      return;
    }
    setUploadingMedia(mediaType);
    setSaving(true);
    setError(null);
    setMediaMessage(null);
    try {
      let nextDraft = draft;
      for (const file of selectedFiles) {
        const result: ProviderPublicProfileDraftMediaUploadResponse = await uploadProviderPublicProfileDraftMedia(profileReference, mediaType, file);
        nextDraft = result.draft;
      }
      if (nextDraft) {
        setDraft(nextDraft);
        setMediaMessage(mediaType === "GALLERY_IMAGE" && selectedFiles.length > 1
          ? `${selectedFiles.length} gallery images uploaded.`
          : `${selectedFiles[0].name} uploaded.`);
      }
    } catch (mediaUploadError) {
      setError(mediaUploadError instanceof Error ? mediaUploadError.message : "Could not upload media.");
    } finally {
      setUploadingMedia(null);
      setSaving(false);
    }
  }

  async function saveMediaField(nextMedia: Record<string, unknown>, changeSummary: string) {
    await persistMediaSection(nextMedia, changeSummary);
  }

  async function removeMedia(documentReference: string) {
    if (!draft) {
      return;
    }
    const nextMedia = { ...currentMedia };
    if (currentLogoReference === documentReference) {
      nextMedia.logoDocumentId = null;
    }
    if (currentCoverReference === documentReference) {
      nextMedia.coverDocumentId = null;
    }
    const nextGallery = currentGalleryReferences.filter((ref) => ref !== documentReference);
    nextMedia.gallery = nextGallery;
    const nextAltTexts = { ...currentGalleryAltTexts };
    delete nextAltTexts[documentReference];
    nextMedia.galleryAltTextByDocumentId = nextAltTexts;
    if (currentPrimaryGalleryReference === documentReference) {
      nextMedia.primaryGalleryDocumentId = nextGallery[0] || null;
    }
    const nextMetadata = { ...currentGalleryMetadata };
    delete nextMetadata[documentReference];
    nextMedia.mediaMetadataByDocumentId = nextMetadata;
    setRemoveTarget(null);
    await persistMediaSection(nextMedia, "Removed media");
  }

  async function reorderGallery(documentReference: string, direction: "up" | "down") {
    if (!draft) {
      return;
    }
    const gallery = [...currentGalleryReferences];
    const index = gallery.indexOf(documentReference);
    if (index < 0) {
      return;
    }
    const swapIndex = direction === "up" ? index - 1 : index + 1;
    if (swapIndex < 0 || swapIndex >= gallery.length) {
      return;
    }
    [gallery[index], gallery[swapIndex]] = [gallery[swapIndex], gallery[index]];
    const nextMedia = {
      ...currentMedia,
      gallery,
      primaryGalleryDocumentId: currentPrimaryGalleryReference && gallery.includes(currentPrimaryGalleryReference)
        ? currentPrimaryGalleryReference
        : gallery[0] || null,
    };
    await persistMediaSection(nextMedia, "Reordered gallery images");
  }

  async function setPrimaryGallery(documentReference: string) {
    if (!draft) {
      return;
    }
    const gallery = currentGalleryReferences.filter((ref) => ref !== documentReference);
    gallery.unshift(documentReference);
    const nextMedia = {
      ...currentMedia,
      gallery,
      primaryGalleryDocumentId: documentReference,
    };
    await persistMediaSection(nextMedia, "Updated primary gallery image");
  }

  async function updateGalleryAltText(documentReference: string, altText: string) {
    if (!draft) {
      return;
    }
    const nextAltTexts = { ...currentGalleryAltTexts };
    if (altText.trim()) {
      nextAltTexts[documentReference] = altText.trim();
    } else {
      delete nextAltTexts[documentReference];
    }
    const nextMedia = {
      ...currentMedia,
      galleryAltTextByDocumentId: nextAltTexts,
    };
    await persistMediaSection(nextMedia, "Updated gallery alt text");
  }

  if (!profileReference) {
    return (
      <section className="page-section">
        <DiscoverEmptyState
          icon="!"
          title="Public profile reference missing"
          description="Open the profile from your provider workspace."
          primaryAction="Provider workspace"
          primaryTo={DISCOVER_ROUTES.providerWorkspace.path}
        />
      </section>
    );
  }

  const lockedForReview = Boolean(moderation && !moderation.editable);
  if (error && !activeDraft && !lockedForReview) {
    return (
      <section className="page-section">
        <DiscoverEmptyState
          icon="!"
          title="We could not load the draft"
          description={error}
          primaryAction="Back to workspace"
          primaryTo={DISCOVER_ROUTES.providerWorkspace.path}
        />
      </section>
    );
  }

  if (!activeDraft && lockedForReview) {
    const reviewPath = DISCOVER_ROUTES.providerPublicProfileReview.path
      .replace(":profileReference", encodeURIComponent(profileReference));
    const publicProfilePath = moderation?.publicUrl ?? DISCOVER_ROUTES.providerWorkspace.path;
    const publicProfileActionLabel = moderation?.publicUrl ? "View public profile" : "Back to workspace";
    const submittedVersion = moderation?.submittedDraftVersion ? `Draft Version ${moderation.submittedDraftVersion}` : "The submitted draft";
    const livePublicationLine = moderation?.publicationStatus === "PUBLISHED"
      ? "Your currently published profile remains visible to patients while this update is being reviewed."
      : null;
    return (
      <section className="page-section">
        <DiscoverEmptyState
          icon="!"
          title="Profile under platform review"
          description={`${submittedVersion} has been submitted for platform review. Editing is temporarily locked while the submission is being reviewed.${livePublicationLine ? ` ${livePublicationLine}` : ""}`}
          primaryAction="View submitted preview"
          primaryTo={reviewPath}
          secondaryAction={publicProfileActionLabel}
          secondaryTo={publicProfilePath}
        />
        {moderation?.publicUrl ? (
          <Stack direction="row" justifyContent="center" sx={{ mt: 2 }}>
            <Button component={Link} to={DISCOVER_ROUTES.providerWorkspace.path} variant="outlined" size="small">
              Back to workspace
            </Button>
          </Stack>
        ) : null}
      </section>
    );
  }

  if (!activeDraft) {
    return (
      <section className="page-section">
        <div className="provider-dashboard-skeleton" role="status" aria-label="Loading draft">
          <span />
          <span />
          <span />
        </div>
      </section>
    );
  }

  const currentDraft = activeDraft;
  const { page, snapshot } = draftToLandingPage(currentDraft);
  const isPreview = currentSection === "preview";
  const isHospitalProfile = currentDraft.publicProfileType === "HOSPITAL";
  const isCompletionReady = currentDraft.readiness.ready && !currentDraft.readiness.invalidFields.length;
  const currentModeration = moderation;
  const consentPresentation = getProviderConsentPresentation({
    tenantConsentStatus: currentDraft.tenantConsentStatus,
    submissionEligible: currentModeration?.submissionEligible,
    submissionBlockers: currentModeration?.submissionBlockers,
    contentStatus: currentDraft.contentStatus,
    readinessStatus: currentDraft.readinessStatus,
  });
  const canSubmit = Boolean(currentModeration?.allowedActions.includes("SUBMIT_FOR_REVIEW"));
  const consentBlocked = consentPresentation.isBlocked;
  const previewTarget = currentDraft.publicProfileStatus === "PUBLISHED"
    ? (currentDraft.publicProfilePath ?? page.publicPath)
    : sectionRoute(profileReference, "preview");
  const missingMandatoryFields = currentDraft.readiness.missingMandatoryFields ?? [];
  const groupedMissingFields = (() => {
    const groups = new Map<string, string[]>();
    for (const field of missingMandatoryFields) {
      const category = missingFieldCategory(field);
      const label = missingFieldChipLabel(field, isHospitalProfile);
      const items = groups.get(category) ?? [];
      if (!items.includes(label)) {
        items.push(label);
        groups.set(category, items);
      }
    }
    return [...groups.entries()];
  })();
  const currentSectionIndex = SECTION_ORDER.indexOf(currentSection);
  const nextSection = SECTION_ORDER[currentSectionIndex + 1] || "preview";
  const sectionStatus = (item: string) => {
    if (item === "fees" || item === "seo") {
      return "Optional";
    }
    if (item === "preview" || item === "readiness") {
      return item === "readiness" ? `${currentDraft.completenessPercentage}%` : "Preview";
    }
    const mappedMissing = missingMandatoryFields.some((field) => missingFieldSection(field) === item);
    if (mappedMissing) {
      return "Needs attention";
    }
    return currentDraft.readiness.invalidFields.some((field) => missingFieldSection(field) === item) ? "Needs attention" : "Complete";
  };
  const workflowNextStep = consentBlocked
    ? "Enable Discover"
    : currentModeration?.allowedActions.includes("SUBMIT_FOR_REVIEW")
      ? "Submit for Platform Review"
      : "Continue editing";
  const workflowNextStepDetails = consentBlocked
    ? "Enable Discover participation before submitting for platform review."
    : currentModeration?.allowedActions.includes("SUBMIT_FOR_REVIEW")
      ? "Ready when all publication gates are satisfied."
      : submissionBlockerMessage(currentModeration?.submissionBlockers);

  function renderSectionEditor() {
    if (isPreview) {
      return (
        <Box>
          <Box className="provider-preview-banner">
            <div className="provider-preview-banner-copy">
              <span className="eyebrow">Draft Preview – Not Public</span>
              <Typography variant="body2" color="text.secondary">
                This preview shows your current unpublished changes. The live public profile will not change until review and publication are complete.
              </Typography>
            </div>
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" className="provider-preview-banner-actions">
              <Button component={Link} to={sectionRoute(profileReference, "overview")} variant="outlined" size="small">Back to editing</Button>
              <Button component={Link} to={currentDraft.publicProfilePath ?? page.publicPath} variant="outlined" size="small">
                View live profile
              </Button>
              <Button component={Link} to={DISCOVER_ROUTES.providerWorkspace.path} variant="outlined" size="small">Back to Workspace</Button>
              <Button
                variant="outlined"
                size="small"
                onClick={async () => {
                  try {
                    if (navigator.clipboard?.writeText) {
                      await navigator.clipboard.writeText(page.publicPath);
                      setCopyStatus("Public URL copied.");
                    } else {
                      setCopyStatus("Copy is not available in this browser.");
                    }
                  } catch {
                    setCopyStatus("Could not copy the URL. You can copy it manually.");
                  }
                }}
              >
                Copy Public URL
              </Button>
            </Stack>
          </Box>
          {copyStatus ? (
            <Alert severity={copyStatus.startsWith("Public URL copied") ? "success" : "warning"} variant="outlined" sx={{ mb: 1.5 }}>
              {copyStatus}
            </Alert>
          ) : null}
          <LandingPageRenderer page={page} snapshot={snapshot} renderMode="PROVIDER_DRAFT_PREVIEW" />
          {currentDraft.publicProfileType === "HOSPITAL" ? (
            <section className="provider-preview-section provider-preview-section--doctors" id="doctors">
              <div className="provider-preview-section-heading">
                <span className="eyebrow">Doctors</span>
                <h2>Doctors at this hospital</h2>
                <p>These doctors are associated with this hospital and shown on its public profile.</p>
              </div>
              {previewHospitalDoctorsLoading ? (
                <div className="provider-preview-empty-state">
                  <strong>Loading draft doctors…</strong>
                  <p>The draft association list is loading.</p>
                </div>
              ) : previewHospitalDoctors.length ? (
                <div className="public-directory-grid provider-preview-doctor-grid">
                  {previewHospitalDoctors.map((doctor) => (
                    <DoctorCard
                      key={doctor.publicDoctorId}
                      doctor={doctor}
                      context="hospital"
                      hostProviderName={currentDraft.displayName ?? page.displayName}
                    />
                  ))}
                </div>
              ) : (
                <div className="provider-preview-empty-state">
                  <strong>No doctors are associated in this draft yet.</strong>
                  <p>Hospital doctor listings follow the explicit draft associations that will be published if this version is approved.</p>
                </div>
              )}
              {previewHospitalDoctorsError ? (
                <Alert severity="warning" variant="outlined" sx={{ mt: 1.5 }}>
                  {previewHospitalDoctorsError}
                </Alert>
              ) : null}
            </section>
          ) : null}
        </Box>
      );
    }

    const sectionData = sectionContent(currentDraft, currentSection);
    switch (currentSection) {
      case "overview":
        return (
          <ProviderEditorSectionCard
            title="Overview"
            description="Confirm the profile identity and see the current lifecycle state at a glance."
            action={<Chip size="small" label={sectionStatus("overview")} color={sectionStatus("overview") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <Stack spacing={2}>
              <TextField
                label="Display name"
                value={String(sectionData.displayName || currentDraft.displayName || "")}
                onChange={(event) => updateSection({ ...sectionData, displayName: event.target.value })}
                helperText={isHospitalProfile ? "Use the hospital name patients know." : "Use the clinic name patients know."}
                fullWidth
              />
              <TextField
                label="Short tagline"
                value={String(sectionData.shortTagline || "")}
                onChange={(event) => updateSection({ ...sectionData, shortTagline: event.target.value })}
                helperText="A concise one-line summary for the hero area."
                fullWidth
              />
              <TextField
                label="Summary status"
                value={readableLifecycleLabel(currentDraft.contentStatus)}
                InputProps={{ readOnly: true }}
                helperText="This lifecycle state is managed by the system."
                fullWidth
              />
              <Box sx={{ p: 2, borderRadius: 3, border: 1, borderColor: "divider", backgroundColor: "background.paper" }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.5 }}>Preview snippet</Typography>
                <Typography variant="body2" color="text.secondary">
                  {String(sectionData.displayName || currentDraft.displayName || (isHospitalProfile ? "Hospital profile" : "Clinic profile"))}
                </Typography>
                <Typography variant="body2">{String(sectionData.shortTagline || "No tagline provided.")}</Typography>
              </Box>
            </Stack>
          </ProviderEditorSectionCard>
        );
      case "about":
        return (
          <ProviderEditorSectionCard
            title="About"
            description={isHospitalProfile ? "Describe the hospital in plain language. These fields power the public profile preview." : "Describe the clinic in plain language. These fields power the public profile preview."}
            action={<Chip size="small" label={sectionStatus("about")} color={sectionStatus("about") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <Stack spacing={2}>
              <TextField label="Display name" value={String(sectionData.displayName || "")} onChange={(event) => updateSection({ ...sectionData, displayName: event.target.value })} helperText={isHospitalProfile ? "Hospital name shown publicly." : "Clinic name shown publicly."} fullWidth />
              <TextField label="Short tagline" value={String(sectionData.shortTagline || "")} onChange={(event) => updateSection({ ...sectionData, shortTagline: event.target.value })} helperText="Short, public-friendly summary." fullWidth />
              <TextField label="Description" multiline minRows={5} value={String(sectionData.description || "")} onChange={(event) => updateSection({ ...sectionData, description: event.target.value })} helperText={isHospitalProfile ? "Write 2-4 short paragraphs that explain the hospital’s care approach." : "Write 2-4 short paragraphs that explain the clinic’s care approach."} fullWidth />
              <TextField label={isHospitalProfile ? "Hospital philosophy" : "Clinic philosophy"} multiline minRows={3} value={String(sectionData.philosophy || "")} onChange={(event) => updateSection({ ...sectionData, philosophy: event.target.value })} helperText="Optional guidance for the public profile." fullWidth />
              <Stack spacing={1} direction={{ xs: "column", md: "row" }}>
                <TextField label="Established year" value={String(sectionData.establishedYear || "")} onChange={(event) => updateSection({ ...sectionData, establishedYear: event.target.value })} helperText="Four-digit year only." fullWidth />
                <TextField label="Registration number" value={String(sectionData.registrationNumber || "")} onChange={(event) => updateSection({ ...sectionData, registrationNumber: event.target.value })} helperText="Provider-facing registration reference." fullWidth />
              </Stack>
              <TextField label="Emergency availability" value={String(sectionData.emergencyAvailability || "")} onChange={(event) => updateSection({ ...sectionData, emergencyAvailability: event.target.value })} helperText={isHospitalProfile ? "Use a short business description such as Available during hospital hours." : "Use a short business description such as Available during clinic hours."} fullWidth />
            </Stack>
          </ProviderEditorSectionCard>
        );
      case "medical_team":
        return isHospitalProfile ? (
          <HospitalDoctorManagementSection
            profileReference={profileReference}
            hospitalDisplayName={currentDraft.displayName ?? page.displayName}
            city={currentDraft.city}
          />
        ) : (
          <ProviderEditorSectionCard
            title="Doctors / Medical Team"
            description="This section is available for hospital profiles only."
          >
            <Typography variant="body2" color="text.secondary">
              Open a hospital profile to manage associated doctors.
            </Typography>
          </ProviderEditorSectionCard>
        );
      case "contact": {
        const addressView = buildPublicAddressView({
          addressLine1: textValue(sectionData.addressLine1),
          addressLine2: textValue(sectionData.addressLine2),
          area: textValue(sectionData.area),
          city: textValue(sectionData.city),
          state: textValue(sectionData.state),
          country: textValue(sectionData.country),
          postalCode: textValue(sectionData.postalCode),
        });
        return (
          <ProviderEditorSectionCard
            title="Contact"
            description="Set the public phone, email, website, WhatsApp and address shown on the draft preview."
            action={<Chip size="small" label={sectionStatus("contact")} color={sectionStatus("contact") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <Stack spacing={2}>
              <Stack spacing={1.5} direction={{ xs: "column", lg: "row" }}>
                <Stack spacing={1.5} flex={1}>
                  <TextField label="Public phone" value={String(sectionData.publicPhone || "")} onChange={(event) => updateSection({ ...sectionData, publicPhone: event.target.value })} helperText="This number can be shown publicly." fullWidth />
                  <TextField label="Public email" value={String(sectionData.publicEmail || "")} onChange={(event) => updateSection({ ...sectionData, publicEmail: event.target.value })} helperText="Contact email shown publicly." fullWidth />
                  <TextField label="Website" value={String(sectionData.website || "")} onChange={(event) => updateSection({ ...sectionData, website: event.target.value })} helperText="Use a full URL such as https://example.com." fullWidth />
                  <TextField label="WhatsApp number" value={String(sectionData.whatsappNumber || "")} onChange={(event) => updateSection({ ...sectionData, whatsappNumber: event.target.value })} helperText="Include country code if you want to surface WhatsApp contact." fullWidth />
                </Stack>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 3, minWidth: { lg: 320 } }}>
                  <PublicAddressPreview address={addressView} />
                </Paper>
              </Stack>
              <Stack spacing={1.5} direction={{ xs: "column", md: "row" }}>
                <TextField label="Address line 1" value={String(sectionData.addressLine1 || "")} onChange={(event) => updateSection({ ...sectionData, addressLine1: event.target.value })} fullWidth />
                <TextField label="Address line 2" value={String(sectionData.addressLine2 || "")} onChange={(event) => updateSection({ ...sectionData, addressLine2: event.target.value })} fullWidth />
              </Stack>
              <Stack spacing={1.5} direction={{ xs: "column", md: "row" }}>
                <TextField label="Area" value={String(sectionData.area || "")} onChange={(event) => updateSection({ ...sectionData, area: event.target.value })} fullWidth />
                <TextField label="City" value={String(sectionData.city || "")} onChange={(event) => updateSection({ ...sectionData, city: event.target.value })} fullWidth />
                <TextField label="State" value={String(sectionData.state || "")} onChange={(event) => updateSection({ ...sectionData, state: event.target.value })} fullWidth />
              </Stack>
              <Stack spacing={1.5} direction={{ xs: "column", md: "row" }}>
                <TextField label="Country" value={String(sectionData.country || "")} onChange={(event) => updateSection({ ...sectionData, country: event.target.value })} fullWidth />
                <TextField label="Postal code" value={String(sectionData.postalCode || "")} onChange={(event) => updateSection({ ...sectionData, postalCode: event.target.value })} fullWidth />
              </Stack>
            </Stack>
          </ProviderEditorSectionCard>
        );
      }
      case "services":
        return (
          <ProviderEditorSectionCard
            title="Services"
            description="Add the services patients can select from the public profile."
            action={<Chip size="small" label={sectionStatus("services")} color={sectionStatus("services") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <ProviderTagListEditor
              title="Services offered"
              values={normalizeEditorList(sectionData.items)}
              suggestions={SERVICE_SUGGESTIONS}
              onChange={(values) => updateSection({ ...sectionData, items: values })}
              emptyState="No services have been configured."
              addLabel="Add service"
              placeholder="Search or add a service"
              helperText="Services are ordered and shown in the public preview."
            />
          </ProviderEditorSectionCard>
        );
      case "specialities":
        return (
          <ProviderEditorSectionCard
            title="Specialities"
            description="Choose the main speciality first and keep the list free of duplicates."
            action={<Chip size="small" label={sectionStatus("specialities")} color={sectionStatus("specialities") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <ProviderTagListEditor
              title="Specialities"
              values={normalizeEditorList(sectionData.items)}
              suggestions={SPECIALITY_SUGGESTIONS}
              onChange={(values) => updateSection({ ...sectionData, items: values })}
              emptyState="No specialities added yet."
              addLabel="Add speciality"
              placeholder="Search or add a speciality"
              helperText="The first speciality appears as the primary speciality in preview."
              primaryValue={normalizeEditorList(sectionData.items)[0] ?? null}
              onPrimaryValueChange={(primary) => {
                if (!primary) {
                  return;
                }
                const values = normalizeEditorList(sectionData.items).filter((item) => item.toLowerCase() !== primary.toLowerCase());
                updateSection({ ...sectionData, items: [primary, ...values] });
              }}
            />
          </ProviderEditorSectionCard>
        );
      case "facilities":
        return (
          <ProviderEditorSectionCard
            title="Facilities"
            description={isHospitalProfile ? "Select the facilities available at the hospital." : "Select the facilities available at the clinic."}
            action={<Chip size="small" label={sectionStatus("facilities")} color={sectionStatus("facilities") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <ProviderTagListEditor
              title="Facilities"
              values={normalizeEditorList(sectionData.items)}
              suggestions={FACILITY_SUGGESTIONS}
              onChange={(values) => updateSection({ ...sectionData, items: values })}
              emptyState="No facilities configured."
              addLabel="Add facility"
              placeholder="Search or add a facility"
              helperText="Facilities appear as chips in the public profile."
            />
          </ProviderEditorSectionCard>
        );
      case "timings":
        return (
          <ProviderEditorSectionCard
            title="Timings"
            description="Use the visual weekly schedule editor instead of JSON."
            action={<Chip size="small" label={sectionStatus("timings")} color={sectionStatus("timings") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <ProviderWeeklyScheduleEditor value={sectionData} onChange={(nextValue) => updateSection(nextValue as Record<string, unknown>)} />
          </ProviderEditorSectionCard>
        );
      case "fees":
        return (
          <ProviderEditorSectionCard
            title="Fees"
            description="Informational consultation fees that can be shown in the profile preview."
            action={<Chip size="small" label={sectionStatus("fees")} variant="outlined" />}
          >
            <ProviderFeeEditor
              value={normalizeFeeValue(sectionData as { currency?: string; rows?: any[] } | null | undefined)}
              onChange={(nextValue) => updateSection(nextValue as Record<string, unknown>)}
            />
          </ProviderEditorSectionCard>
        );
      case "languages":
        return (
          <ProviderEditorSectionCard
            title="Languages"
            description={isHospitalProfile ? "Add the languages the hospital can support." : "Add the languages the clinic can support."}
            action={<Chip size="small" label={sectionStatus("languages")} color={sectionStatus("languages") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <ProviderTagListEditor
              title="Languages"
              values={normalizeEditorList(sectionData.items)}
              suggestions={LANGUAGE_SUGGESTIONS}
              onChange={(values) => updateSection({ ...sectionData, items: values })}
              emptyState="No languages added yet."
              addLabel="Add language"
              placeholder="Search or add a language"
              helperText="Languages should use the canonical display label."
            />
          </ProviderEditorSectionCard>
        );
      case "media":
        return (
          <ProviderEditorSectionCard
            title="Media"
            description="Upload logo, cover and gallery media. Internal document references stay hidden."
            action={<Chip size="small" label={sectionStatus("media")} color={sectionStatus("media") === "Needs attention" ? "warning" : "default"} variant="outlined" />}
          >
            <Stack spacing={2}>
              <Alert severity="info" variant="outlined">
                Upload images here. Internal media references stay hidden and the draft preview updates from persisted media only.
              </Alert>
              {/* existing media controls remain below */}
              <Paper variant="outlined" className="provider-media-panel">
                <Stack spacing={1.25}>
                  <Typography variant="subtitle1">Logo</Typography>
                  {currentLogoReference ? (
                    <Box className="provider-media-preview">
                      <PublicMediaImage
                        src={mediaContentPath(profileReference, currentLogoReference)}
                        alt={`${page.displayName} logo`}
                        className="provider-media-preview__image provider-media-preview__image--logo"
                        objectFit="contain"
                        fallback={<div className="provider-media-empty-state">No logo uploaded.</div>}
                        loading="eager"
                      />
                    </Box>
                  ) : (
                    <Box className="provider-media-empty-state">No logo uploaded.</Box>
                  )}
                  <Typography variant="body2" color="text.secondary">
                    Recommended: PNG, JPG, JPEG, or WEBP. Keep the logo crisp and under 5 MB.
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button component="label" variant="contained" startIcon={<PhotoCameraIcon />}>
                      {uploadingMedia === "LOGO" ? "Uploading..." : currentLogoReference ? "Replace logo" : "Upload logo"}
                      <input
                        hidden
                        type="file"
                        accept={mediaUploadAccept}
                        onChange={(event) => {
                          const input = event.currentTarget;
                          void handleMediaUpload("LOGO", input.files).finally(() => {
                            input.value = "";
                          });
                        }}
                      />
                    </Button>
                    <Button variant="outlined" onClick={() => setRemoveTarget(currentLogoReference ? { documentReference: currentLogoReference, label: "logo" } : null)} disabled={!currentLogoReference}>Remove logo</Button>
                  </Stack>
                </Stack>
              </Paper>
              <Paper variant="outlined" className="provider-media-panel">
                <Stack spacing={1.25}>
                  <Typography variant="subtitle1">Cover image</Typography>
                  {currentCoverReference ? (
                    <Box className="provider-media-preview">
                      <PublicMediaImage
                        src={mediaContentPath(profileReference, currentCoverReference)}
                        alt={`${page.displayName} cover`}
                        className="provider-media-preview__image provider-media-preview__image--cover"
                        objectFit="cover"
                        fallback={<div className="provider-media-empty-state">No cover image uploaded.</div>}
                        loading="eager"
                      />
                    </Box>
                  ) : (
                    <Box className="provider-media-empty-state">No cover image uploaded.</Box>
                  )}
                  <Typography variant="body2" color="text.secondary">
                    Recommended: wide landscape image, PNG/JPG/JPEG/WEBP, under 10 MB.
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button component="label" variant="contained" startIcon={<PhotoCameraIcon />}>
                      {uploadingMedia === "COVER_IMAGE" ? "Uploading..." : currentCoverReference ? "Replace cover" : "Upload cover image"}
                      <input
                        hidden
                        type="file"
                        accept={mediaUploadAccept}
                        onChange={(event) => {
                          const input = event.currentTarget;
                          void handleMediaUpload("COVER_IMAGE", input.files).finally(() => {
                            input.value = "";
                          });
                        }}
                      />
                    </Button>
                    <Button variant="outlined" onClick={() => setRemoveTarget(currentCoverReference ? { documentReference: currentCoverReference, label: "cover image" } : null)} disabled={!currentCoverReference}>Remove cover</Button>
                  </Stack>
                </Stack>
              </Paper>
              <Paper variant="outlined" className="provider-media-panel">
                <Stack spacing={1.25}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} flexWrap="wrap">
                    <div>
                      <Typography variant="subtitle1">Gallery</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {currentGalleryReferences.length} of 20 images uploaded
                      </Typography>
                    </div>
                    <Button component="label" variant="contained" startIcon={<PhotoCameraIcon />}>
                      {uploadingMedia === "GALLERY_IMAGE" ? "Uploading..." : "Add more images"}
                      <input
                        hidden
                        type="file"
                        accept={mediaUploadAccept}
                        multiple
                        onChange={(event) => {
                          const input = event.currentTarget;
                          void handleMediaUpload("GALLERY_IMAGE", input.files).finally(() => {
                            input.value = "";
                          });
                        }}
                      />
                    </Button>
                  </Stack>
                  {currentGalleryReferences.length ? (
                    <Stack spacing={1.25}>
                      {currentGalleryReferences.map((reference, index) => {
                        const metadata = currentGalleryMetadata[reference];
                        const altText = currentGalleryAltTexts[reference] ?? "";
                        const isPrimary = currentPrimaryGalleryReference === reference;
                        return (
                          <Paper key={reference} variant="outlined" className="provider-media-gallery-item">
                            <Stack spacing={1.25}>
                              <Box className="provider-media-gallery-item__preview">
                                <PublicMediaImage
                                  src={mediaContentPath(profileReference, reference)}
                                  alt={altText.trim() || metadata?.originalFilename || `Gallery image ${index + 1}`}
                                  className="provider-media-preview__image provider-media-preview__image--gallery"
                                  objectFit="cover"
                                  fallback={<div className="provider-media-empty-state">No gallery image preview available.</div>}
                                />
                              </Box>
                              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                <Typography variant="subtitle2">
                                  {metadata?.originalFilename || `Gallery image ${index + 1}`}
                                  {isPrimary ? " · Primary" : ""}
                                </Typography>
                                <Stack direction="row" spacing={0.5}>
                                  <IconButton aria-label={`Move gallery image ${index + 1} up`} size="small" onClick={() => void reorderGallery(reference, "up")} disabled={index === 0}>
                                    <ArrowUpwardIcon fontSize="small" />
                                  </IconButton>
                                  <IconButton aria-label={`Move gallery image ${index + 1} down`} size="small" onClick={() => void reorderGallery(reference, "down")} disabled={index === currentGalleryReferences.length - 1}>
                                    <ArrowDownwardIcon fontSize="small" />
                                  </IconButton>
                                  <IconButton aria-label={`Remove gallery image ${index + 1}`} size="small" onClick={() => setRemoveTarget({ documentReference: reference, label: metadata?.originalFilename || `gallery image ${index + 1}` })}>
                                    <DeleteOutlineIcon fontSize="small" />
                                  </IconButton>
                                </Stack>
                              </Stack>
                              <TextField
                                label="Alt text"
                                value={altText}
                                onChange={(event) => updateSection({
                                  ...currentMedia,
                                  galleryAltTextByDocumentId: {
                                    ...currentGalleryAltTexts,
                                    [reference]: event.target.value,
                                  },
                                })}
                                onBlur={(event) => void updateGalleryAltText(reference, event.target.value)}
                                helperText="Describe the image for screen readers."
                                fullWidth
                              />
                              <Stack direction="row" spacing={1} flexWrap="wrap">
                                <Button variant={isPrimary ? "contained" : "outlined"} size="small" onClick={() => void setPrimaryGallery(reference)}>
                                  {isPrimary ? "Primary image" : "Set as primary"}
                                </Button>
                              </Stack>
                            </Stack>
                          </Paper>
                        );
                      })}
                    </Stack>
                  ) : (
                    <Box className="provider-media-empty-state">No gallery images uploaded.</Box>
                  )}
                </Stack>
              </Paper>
            </Stack>
          </ProviderEditorSectionCard>
        );
      case "seo":
        return (
          <ProviderEditorSectionCard
            title="SEO"
            description="Set the public slug and metadata for the future public URL."
            action={<Chip size="small" label={sectionStatus("seo")} variant="outlined" />}
          >
            <Stack spacing={1.5}>
              <TextField
                label="Slug"
                value={String(sectionData.slug || "")}
                onChange={(event) => updateSection({
                  ...sectionData,
                  slug: event.target.value,
                  canonicalPublicPath: isHospitalProfile
                    ? `/discover/hospitals/${event.target.value}`
                    : `/discover/clinics/${event.target.value}`,
                })}
                helperText="The slug forms the public URL path."
                fullWidth
              />
              <TextField label="Meta title" value={String(sectionData.metaTitle || "")} onChange={(event) => updateSection({ ...sectionData, metaTitle: event.target.value })} helperText="Title shown in search and social previews." fullWidth />
              <TextField label="Meta description" multiline minRows={3} value={String(sectionData.metaDescription || "")} onChange={(event) => updateSection({ ...sectionData, metaDescription: event.target.value })} helperText="Short, readable description for search previews." fullWidth />
            </Stack>
          </ProviderEditorSectionCard>
        );
      case "readiness":
        return (
          <ProviderEditorSectionCard
            title="Readiness"
            description="Review the backend-authoritative content completion status."
            action={<Chip size="small" label={`${currentDraft.completenessPercentage}%`} color={currentDraft.readiness.ready ? "success" : "default"} variant="outlined" />}
          >
            <Stack spacing={1.5}>
              <Typography variant="body2" color="text.secondary">Ready for review: {currentDraft.readiness.ready ? "Yes" : "No"}</Typography>
              <Typography variant="body2" color="text.secondary">Blocking reasons: {currentDraft.readiness.blockingReasons.map((item) => readableLifecycleLabel(item)).join(" · ") || "None"}</Typography>
              <Typography variant="body2" color="text.secondary">Missing fields: {currentDraft.readiness.missingMandatoryFields.map((item) => missingFieldChipLabel(item, isHospitalProfile)).join(" · ") || "None"}</Typography>
            </Stack>
          </ProviderEditorSectionCard>
        );
      default:
        return (
          <ProviderEditorSectionCard title={sectionLabel(currentSection)} description="This section is available in the profile editor.">
            <Typography variant="body2" color="text.secondary">Select a different section to continue editing.</Typography>
          </ProviderEditorSectionCard>
        );
    }
  }

  return (
    <section className="page-section provider-status-page">
      <header className="provider-editor-page-header">
          <div className="provider-editor-page-header__copy">
          <span className="eyebrow">Public Profile</span>
          <h1>Ownership verified</h1>
          <p>Your ownership of {page.displayName} has been verified.</p>
          {consentPresentation.visible ? <p>{consentPresentation.message}</p> : null}
        </div>
      </header>

      <div className="provider-status-layout provider-editor-layout">
        <main className="provider-status-main provider-editor-main">
          <article className="provider-status-panel">
            <div className="provider-status-panel-heading">
              <div>
                <span className="eyebrow">Profile completeness</span>
                <h2>{currentDraft.completenessPercentage}% complete</h2>
              </div>
              <span>{isCompletionReady ? "Content Ready" : "Profile needs more information"}</span>
            </div>
            <Stack spacing={1.25}>
              <Typography variant="body2" color="text.secondary">
                {currentDraft.readiness.missingMandatoryFields.length || currentDraft.readiness.invalidFields.length ? "Required before review:" : "No blocking content items remain."}
              </Typography>
              <Typography variant="subtitle2">Profile readiness</Typography>
              <Stack spacing={1} useFlexGap flexWrap="wrap">
                {groupedMissingFields.length ? groupedMissingFields.map(([category, items]) => (
                  <Stack key={category} spacing={0.75}>
                    <Typography variant="caption" color="text.secondary">{category}</Typography>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      {items.map((item) => (
                        <Chip
                          key={`${category}-${item}`}
                          label={item}
                          component={Link}
                          to={`${DISCOVER_ROUTES.providerPublicProfileDraft.path.replace(":profileReference", encodeURIComponent(profileReference)).replace(":section", missingFieldCategorySection(category))}`}
                          clickable
                          variant="outlined"
                        />
                      ))}
                    </Stack>
                  </Stack>
                )) : (
                  <Typography variant="body2" color="text.secondary">No blocking content items remain.</Typography>
                )}
              </Stack>
              {consentPresentation.isBlocked ? (
                <Alert severity="info" variant="outlined">
                  Tenant consent is separate from content completion. Draft editing stays available while consent is disabled.
                </Alert>
              ) : null}
            </Stack>
          </article>

          <article className="provider-status-panel">
            <div className="provider-status-panel-heading">
              <div>
                <span className="eyebrow">Publication</span>
                <h2>Draft</h2>
              </div>
              <span>{currentDraft.publicProfileStatus === "PUBLISHED" ? "Published" : "Not Published"}</span>
            </div>
            <Stack spacing={1.25}>
              <Typography variant="body2" color="text.secondary">Public URL: {page.publicPath}</Typography>
              <Typography variant="body2" color="text.secondary">
                {publicationCardMessage(currentDraft.readiness, currentModeration, currentDraft.tenantConsentStatus)}
              </Typography>
              {consentPresentation.isBlocked ? (
                <Alert severity="warning" variant="outlined">
                  {consentPresentation.message}
                </Alert>
              ) : null}
              <Stack direction="row" spacing={1}>
                <Button variant="contained" onClick={() => void submitCurrentDraft()} disabled={!canSubmit || submitting}>
                  {submitting ? "Submitting..." : "Submit for Platform Review"}
                </Button>
              </Stack>
            </Stack>
          </article>

          <article className="provider-status-panel">
            <div className="provider-status-panel-heading">
              <div>
                <span className="eyebrow">Section editor</span>
                <h2>{sectionLabel(currentSection)}</h2>
              </div>
              <span>{formatDateTime(currentDraft.lastSavedAt ?? currentDraft.updatedAt)}</span>
            </div>
            <Stack spacing={2}>
              {renderSectionEditor()}
              {!isPreview ? (
                <ProviderEditorFooter>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1} useFlexGap flexWrap="wrap" alignItems={{ xs: "stretch", sm: "center" }}>
                {currentSection !== "medical_team" ? (
                  <Button variant="contained" onClick={() => void saveCurrentSection()} disabled={saving}>Save changes</Button>
                ) : null}
                <Button component={Link} to={previewTarget} variant="outlined">{currentDraft.publicProfileStatus === "PUBLISHED" ? "View Public Profile" : "Preview profile"}</Button>
                <Button component={Link} to={sectionRoute(profileReference, nextSection)} variant="outlined">Continue</Button>
                    <Button component={Link} to={DISCOVER_ROUTES.providerWorkspace.path} variant="text">Back to workspace</Button>
                  </Stack>
                </ProviderEditorFooter>
              ) : null}
            </Stack>
          </article>
        </main>

        <aside className="provider-status-side provider-editor-sidebar">
          <ProviderEditorSectionCard
            title="Profile status"
            description="Quick summary of the current draft."
          >
            <div className="provider-status-workflow-card">
              <div className="provider-status-workflow-heading">
                <div>
                  <span className="eyebrow">Profile status</span>
                  <h2>Draft Version {currentDraft.currentVersion}</h2>
                </div>
                <PendingActionsOutlinedIcon fontSize="small" aria-hidden="true" className="provider-status-workflow-heading-icon" />
              </div>
              <dl className="provider-status-definition-list provider-status-definition-list--summary provider-status-sidebar-metrics">
                <div>
                  <dt><ArticleOutlinedIcon fontSize="small" aria-hidden="true" /> Last saved</dt>
                  <dd>{formatDateTime(activeDraft.lastSavedAt ?? activeDraft.updatedAt)}</dd>
                </div>
                <div>
                  <dt><TaskAltOutlinedIcon fontSize="small" aria-hidden="true" /> Content</dt>
                  <dd>{isCompletionReady ? "Complete" : "Needs more information"}</dd>
                </div>
                <div>
                  <dt><PublishOutlinedIcon fontSize="small" aria-hidden="true" /> Publication</dt>
                  <dd>{consentBlocked ? "Enable Discover participation before submitting for platform review." : currentModeration?.submissionEligible ? "Ready to submit for platform review." : submissionBlockerMessage(currentModeration?.submissionBlockers)}</dd>
                </div>
                <div>
                  <dt><PersonOutlineOutlinedIcon fontSize="small" aria-hidden="true" /> Owner</dt>
                  <dd>{page.displayName}</dd>
                </div>
              </dl>
              <div className="provider-status-workflow-next-step">
                <span className="eyebrow">Next step</span>
                <strong>{workflowNextStep}</strong>
                <span>{workflowNextStepDetails}</span>
              </div>
            </div>
          </ProviderEditorSectionCard>
          <ProviderEditorSectionCard
            title="Profile Sections"
            description="Jump to any section without losing your place."
          >
            <Stack spacing={0.5}>
              {SECTION_ORDER.map((item) => (
                (() => {
                  const meta = sectionNavigatorMeta(item, currentDraft.completenessPercentage, missingMandatoryFields, currentDraft.readiness.invalidFields);
                  return (
                    <Button
                      key={item}
                      component={Link}
                      to={sectionRoute(profileReference, item)}
                      variant="text"
                      size="small"
                      aria-current={item === currentSection ? "page" : undefined}
                      className={`provider-editor-nav-item${item === currentSection ? " is-active" : ""}`}
                      startIcon={meta.icon}
                      endIcon={meta.badge ? <span className={`provider-editor-nav-badge is-${meta.state}`}>{meta.badge}</span> : null}
                    >
                      <span className="provider-editor-nav-label">{sectionLabel(item)}</span>
                    </Button>
                  );
                })()
              ))}
            </Stack>
          </ProviderEditorSectionCard>
        </aside>
      </div>

      <Dialog open={Boolean(removeTarget)} onClose={() => setRemoveTarget(null)}>
        <DialogTitle>Remove media?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            Remove {removeTarget?.label || "this media"} from the current draft?
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRemoveTarget(null)}>Cancel</Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => void (removeTarget ? removeMedia(removeTarget.documentReference) : Promise.resolve())}
            disabled={!removeTarget}
          >
            Remove
          </Button>
        </DialogActions>
      </Dialog>

      {error ? <p className="autosave-row" role="status">{error}</p> : null}
      {mediaMessage ? <p className="autosave-row" role="status">{mediaMessage}</p> : null}
      {loading ? <p className="autosave-row" role="status">Loading draft…</p> : null}
      {saving ? <p className="autosave-row" role="status">Saving draft…</p> : null}
      {submitting ? <p className="autosave-row" role="status">Submitting for review…</p> : null}
    </section>
  );
}
