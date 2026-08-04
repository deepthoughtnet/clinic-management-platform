import * as React from "react";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, IconButton, Paper, Stack, TextField, Typography } from "@mui/material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import PhotoCameraIcon from "@mui/icons-material/PhotoCamera";
import { LandingPageRenderer } from "../../components/landing/LandingPageRenderer";
import { PublicMediaImage } from "../../components/landing/PublicMediaImage";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { DISCOVER_ROUTES } from "../../routes";
import { buildPublicAddressView, resolveClinicEstablishedYear } from "../../utils/publicProfileFormatting";
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
import type { LandingPageRenderable, LandingProfile, LandingSnapshot, LandingTheme } from "../../api/providerLandingPage";

const SECTION_ORDER = ["overview", "about", "contact", "services", "specialities", "facilities", "timings", "fees", "languages", "media", "seo", "preview", "readiness"];

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
  if (readiness.blockingReasons.includes("TENANT_CONSENT_DISABLED")) {
    blockers.push("Waiting for the clinic administrator to enable Discover publishing.");
  }
  if (!blockers.length && moderation?.submissionEligible) {
    blockers.push("Content ready");
  }
  return blockers.length ? blockers.join(" · ") : "Submission becomes available once all required items are completed.";
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

function missingFieldChipLabel(field: string) {
  switch (field) {
    case "displayName":
      return "Add clinic name";
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
  const services = normalizeList(sectionContent(draft, "services").items);
  const specialities = normalizeList(sectionContent(draft, "specialities").items);
  const languages = normalizeList(sectionContent(draft, "languages").items);
  const facilities = normalizeList(sectionContent(draft, "facilities").items);
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
        address: addressView.lines.join("\n") || null,
        area: textValue(contact.area),
        city: textValue(contact.city),
        state: textValue(contact.state),
        country: textValue(contact.country),
        pinCode: textValue(contact.postalCode),
        postalCode: textValue(contact.postalCode),
        workingHours: Array.isArray(timings.weekly) && timings.weekly.length ? "Weekly timings configured" : null,
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
    weeklyTimings: timings.weekly,
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
    setLoading(true);
    setError(null);
    createProviderPublicProfileDraft(profileReference)
      .then(setDraft)
      .catch((createError) => {
        void loadProviderPublicProfileDraft(profileReference)
          .then(setDraft)
          .catch((loadError) => {
            setError(loadError instanceof Error ? loadError.message : createError instanceof Error ? createError.message : "Could not load the draft.");
          });
      })
      .finally(() => setLoading(false));
  }, [profileReference]);

  useEffect(() => {
    if (section === "preview" && profileReference) {
      loadProviderPublicProfileDraftPreview(profileReference).then(setPreviewDraft).catch(() => setPreviewDraft(null));
    }
  }, [profileReference, section]);

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

  if (error && !activeDraft) {
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
  const currentModeration = moderation;
  const canSubmit = Boolean(currentModeration?.allowedActions.includes("SUBMIT_FOR_REVIEW"));
  const consentBlocked = currentModeration?.submissionBlockers.includes("TENANT_CONSENT_REQUIRED");
  const previewTarget = currentDraft.publicProfileStatus === "PUBLISHED"
    ? (currentDraft.publicProfilePath ?? page.publicPath)
    : sectionRoute(profileReference, "preview");
  const missingMandatoryFields = currentDraft.readiness.missingMandatoryFields ?? [];
  const groupedMissingFields = (() => {
    const groups = new Map<string, string[]>();
    for (const field of missingMandatoryFields) {
      const category = missingFieldCategory(field);
      const label = missingFieldChipLabel(field);
      const items = groups.get(category) ?? [];
      if (!items.includes(label)) {
        items.push(label);
        groups.set(category, items);
      }
    }
    return [...groups.entries()];
  })();

  return (
    <section className="page-section provider-status-page">
      <header className="provider-status-hero">
        <div>
          <span className="eyebrow">Public Profile</span>
          <h1>Ownership verified</h1>
          <p>Your ownership of {page.displayName} has been verified.</p>
          <p>Healthcare tenant consent is currently disabled. You can continue preparing the draft, but submission remains unavailable.</p>
        </div>
        <aside className="provider-status-hero-card">
          <div className="provider-status-hero-card-row">
            <span className="provider-account-status-pill">Draft</span>
            <span className="provider-account-status-pill">Version {currentDraft.currentVersion}</span>
            <span className="provider-account-status-pill">{currentDraft.readiness.ready && !currentDraft.readiness.invalidFields.length ? "Content complete" : "Profile needs more information"}</span>
            <span className="provider-account-status-pill">{consentBlocked ? "Consent required" : "Consent enabled"}</span>
          </div>
          <div className="provider-status-hero-card-meta">
            <span>Last saved {formatDateTime(activeDraft.lastSavedAt ?? activeDraft.updatedAt)}</span>
            <span>{consentBlocked ? "Submission will become available after the clinic enables Discover publishing." : currentModeration?.submissionEligible ? "Submission available." : "Submission blocked."}</span>
          </div>
        </aside>
      </header>

      <div className="provider-status-layout">
        <div className="provider-status-main">
          <article className="provider-status-panel">
            <div className="provider-status-panel-heading">
              <div>
                <span className="eyebrow">Profile completeness</span>
                <h2>{currentDraft.completenessPercentage}% complete</h2>
              </div>
              <span>{currentDraft.readiness.ready && !currentDraft.readiness.invalidFields.length ? "Content Ready" : "Profile needs more information"}</span>
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
              <Alert severity="info" variant="outlined">
                Tenant consent is separate from content completion. Draft editing stays available while consent is disabled.
              </Alert>
            </Stack>
          </article>

          <article className="provider-status-panel">
              <div className="provider-status-panel-heading">
              <div>
                  <span className="eyebrow">Submission</span>
                  <h2>Draft</h2>
                </div>
              <span>{currentDraft.readiness.ready && !currentDraft.readiness.invalidFields.length ? "Content complete" : "Profile needs more information"}</span>
              </div>
            <Stack spacing={1.25}>
              <Typography variant="body2" color="text.secondary">
                {publicationBlockSummary(currentDraft.readiness, currentModeration)}
              </Typography>
              {consentBlocked ? (
                <Alert severity="warning" variant="outlined">
                  Healthcare tenant consent is currently disabled. You can continue preparing the draft, but submission remains unavailable.
                </Alert>
              ) : null}
              <Stack direction="row" spacing={1}>
                <Button variant="contained" onClick={() => void submitCurrentDraft()} disabled={!canSubmit || submitting}>
                  {submitting ? "Submitting..." : "Submit for review"}
                </Button>
                <Button component={Link} to={previewTarget} variant="outlined">{currentDraft.publicProfileStatus === "PUBLISHED" ? "View Public Profile" : "Preview Draft"}</Button>
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

            <Stack spacing={1.25}>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {SECTION_ORDER.map((item) => (
                  <Button
                    key={item}
                    component={Link}
                    to={sectionRoute(profileReference, item)}
                    variant={item === currentSection ? "contained" : "outlined"}
                    size="small"
                  >
                    {sectionLabel(item)}
                  </Button>
                ))}
              </Stack>

              {!isPreview ? (
                <Stack spacing={1.5}>
                  {currentSection === "overview" ? (
                    <Stack spacing={1.5}>
                      <TextField label="Display name" value={String(sectionContent(currentDraft, "about").displayName || currentDraft.displayName || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "overview"), displayName: event.target.value })} fullWidth />
                      <TextField label="Short tagline" value={String(sectionContent(currentDraft, "about").shortTagline || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "overview"), shortTagline: event.target.value })} fullWidth />
                      <TextField label="Summary status" value={readableLifecycleLabel(currentDraft.contentStatus)} InputProps={{ readOnly: true }} fullWidth />
                    </Stack>
                  ) : null}

                  {currentSection === "about" ? (
                    <Stack spacing={1.5}>
                      <TextField label="Display name" value={String(sectionContent(currentDraft, "about").displayName || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "about"), displayName: event.target.value })} fullWidth />
                      <TextField label="Short tagline" value={String(sectionContent(currentDraft, "about").shortTagline || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "about"), shortTagline: event.target.value })} fullWidth />
                      <TextField label="Description" multiline minRows={5} value={String(sectionContent(currentDraft, "about").description || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "about"), description: event.target.value })} fullWidth />
                      <TextField label="Clinic philosophy" multiline minRows={3} value={String(sectionContent(currentDraft, "about").philosophy || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "about"), philosophy: event.target.value })} fullWidth />
                      <TextField label="Established year" value={String(sectionContent(currentDraft, "about").establishedYear || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "about"), establishedYear: event.target.value })} fullWidth />
                      <TextField label="Registration number" value={String(sectionContent(currentDraft, "about").registrationNumber || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "about"), registrationNumber: event.target.value })} fullWidth />
                      <TextField label="Emergency availability" value={String(sectionContent(currentDraft, "about").emergencyAvailability || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "about"), emergencyAvailability: event.target.value })} fullWidth />
                    </Stack>
                  ) : null}

                  {currentSection === "contact" ? (
                    <Stack spacing={1.5}>
                      <TextField label="Public phone" value={String(sectionContent(currentDraft, "contact").publicPhone || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), publicPhone: event.target.value })} fullWidth />
                      <TextField label="Public email" value={String(sectionContent(currentDraft, "contact").publicEmail || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), publicEmail: event.target.value })} fullWidth />
                      <TextField label="Website" value={String(sectionContent(currentDraft, "contact").website || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), website: event.target.value })} fullWidth />
                      <TextField label="WhatsApp number" value={String(sectionContent(currentDraft, "contact").whatsappNumber || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), whatsappNumber: event.target.value })} fullWidth />
                      <TextField label="Address line 1" value={String(sectionContent(currentDraft, "contact").addressLine1 || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), addressLine1: event.target.value })} fullWidth />
                      <TextField label="Address line 2" value={String(sectionContent(currentDraft, "contact").addressLine2 || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), addressLine2: event.target.value })} fullWidth />
                      <TextField label="Area" value={String(sectionContent(currentDraft, "contact").area || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), area: event.target.value })} fullWidth />
                      <TextField label="City" value={String(sectionContent(currentDraft, "contact").city || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), city: event.target.value })} fullWidth />
                      <TextField label="State" value={String(sectionContent(currentDraft, "contact").state || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), state: event.target.value })} fullWidth />
                      <TextField label="Country" value={String(sectionContent(currentDraft, "contact").country || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), country: event.target.value })} fullWidth />
                      <TextField label="Postal code" value={String(sectionContent(currentDraft, "contact").postalCode || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "contact"), postalCode: event.target.value })} fullWidth />
                    </Stack>
                  ) : null}

                  {["services", "specialities", "facilities", "languages"].includes(currentSection) ? (
                    <Stack spacing={1.5}>
                      <TextField
                        label={`${sectionLabel(currentSection)} list`}
                        multiline
                        minRows={4}
                        value={listValue(sectionContent(currentDraft, currentSection).items)}
                        onChange={(event) => updateSection({ ...sectionContent(currentDraft, currentSection), items: normalizeList(event.target.value) })}
                        helperText="Separate entries with commas."
                        fullWidth
                      />
                    </Stack>
                  ) : null}

                  {currentSection === "timings" ? (
                    <Stack spacing={1.5}>
                      <TextField
                        label="Weekly timings"
                        multiline
                        minRows={6}
                        value={JSON.stringify(sectionContent(currentDraft, "timings").weekly ?? [], null, 2)}
                        onChange={(event) => {
                          try {
                            updateSection({ ...sectionContent(currentDraft, "timings"), weekly: JSON.parse(event.target.value) });
                          } catch {
                            updateSection({ ...sectionContent(currentDraft, "timings"), weekly: [] });
                          }
                        }}
                        helperText="JSON array of day intervals."
                        fullWidth
                      />
                      <TextField label="Timezone" value={String(sectionContent(currentDraft, "timings").timezone || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "timings"), timezone: event.target.value })} fullWidth />
                    </Stack>
                  ) : null}

                  {currentSection === "fees" ? (
                    <Stack spacing={1.5}>
                      <TextField label="Currency" value={String(sectionContent(currentDraft, "fees").currency || "INR")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "fees"), currency: event.target.value })} fullWidth />
                      <TextField label="In-clinic fee" value={String(sectionContent(currentDraft, "fees").inClinic || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "fees"), inClinic: event.target.value })} fullWidth />
                      <TextField label="Video fee" value={String(sectionContent(currentDraft, "fees").video || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "fees"), video: event.target.value })} fullWidth />
                      <TextField label="Home visit fee" value={String(sectionContent(currentDraft, "fees").homeVisit || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "fees"), homeVisit: event.target.value })} fullWidth />
                      <TextField label="Emergency fee" value={String(sectionContent(currentDraft, "fees").emergency || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "fees"), emergency: event.target.value })} fullWidth />
                    </Stack>
                  ) : null}

                  {currentSection === "media" ? (
                    <Stack spacing={2}>
                      <Alert severity="info" variant="outlined">
                        Upload images here. Internal media references stay hidden and the draft preview updates from persisted media only.
                      </Alert>

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
                            <Button
                              variant="outlined"
                              onClick={() => setRemoveTarget(currentLogoReference ? { documentReference: currentLogoReference, label: "logo" } : null)}
                              disabled={!currentLogoReference}
                            >
                              Remove logo
                            </Button>
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
                            <Button
                              variant="outlined"
                              onClick={() => setRemoveTarget(currentCoverReference ? { documentReference: currentCoverReference, label: "cover image" } : null)}
                              disabled={!currentCoverReference}
                            >
                              Remove cover
                            </Button>
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
                                          <IconButton
                                            aria-label={`Move gallery image ${index + 1} up`}
                                            size="small"
                                            onClick={() => void reorderGallery(reference, "up")}
                                            disabled={index === 0}
                                          >
                                            <ArrowUpwardIcon fontSize="small" />
                                          </IconButton>
                                          <IconButton
                                            aria-label={`Move gallery image ${index + 1} down`}
                                            size="small"
                                            onClick={() => void reorderGallery(reference, "down")}
                                            disabled={index === currentGalleryReferences.length - 1}
                                          >
                                            <ArrowDownwardIcon fontSize="small" />
                                          </IconButton>
                                          <IconButton
                                            aria-label={`Remove gallery image ${index + 1}`}
                                            size="small"
                                            onClick={() => setRemoveTarget({ documentReference: reference, label: metadata?.originalFilename || `gallery image ${index + 1}` })}
                                          >
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
                                        <Button
                                          variant={isPrimary ? "contained" : "outlined"}
                                          size="small"
                                          onClick={() => void setPrimaryGallery(reference)}
                                        >
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
                  ) : null}

                  {currentSection === "seo" ? (
                    <Stack spacing={1.5}>
                      <TextField label="Slug" value={String(sectionContent(currentDraft, "seo").slug || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "seo"), slug: event.target.value, canonicalPublicPath: `/discover/clinics/${event.target.value}` })} fullWidth />
                      <TextField label="Meta title" value={String(sectionContent(currentDraft, "seo").metaTitle || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "seo"), metaTitle: event.target.value })} fullWidth />
                      <TextField label="Meta description" multiline minRows={3} value={String(sectionContent(currentDraft, "seo").metaDescription || "")} onChange={(event) => updateSection({ ...sectionContent(currentDraft, "seo"), metaDescription: event.target.value })} fullWidth />
                    </Stack>
                  ) : null}

                  {currentSection === "readiness" ? (
                    <Stack spacing={1.5}>
                      <Typography variant="body2" color="text.secondary">Ready for review: {currentDraft.readiness.ready ? "Yes" : "No"}</Typography>
                      <Typography variant="body2" color="text.secondary">Blocking reasons: {currentDraft.readiness.blockingReasons.map((item) => readableLifecycleLabel(item)).join(" · ") || "None"}</Typography>
                      <Typography variant="body2" color="text.secondary">Missing fields: {currentDraft.readiness.missingMandatoryFields.map((item) => missingFieldChipLabel(item)).join(" · ") || "None"}</Typography>
                    </Stack>
                  ) : null}

                  <Stack direction="row" spacing={1}>
                    <Button variant="contained" onClick={() => void saveCurrentSection()} disabled={saving}>Save draft</Button>
                    <Button component={Link} to={previewTarget} variant="outlined">{currentDraft.publicProfileStatus === "PUBLISHED" ? "View Public Profile" : "Preview Draft"}</Button>
                    {currentModeration?.allowedActions.includes("SUBMIT_FOR_REVIEW") ? (
                      <Button variant="outlined" onClick={() => void submitCurrentDraft()} disabled={submitting}>
                        {submitting ? "Submitting..." : "Submit for review"}
                      </Button>
                    ) : null}
                    <Button component={Link} to={DISCOVER_ROUTES.providerWorkspace.path} variant="text">Back to workspace</Button>
                  </Stack>
                </Stack>
              ) : (
                <Box>
                  <Alert severity="info" variant="outlined" sx={{ mb: 2 }}>
                    DRAFT PREVIEW - NOT PUBLIC
                  </Alert>
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 2 }}>
                    <Button component={Link} to={sectionRoute(profileReference, "overview")} variant="outlined">Back to editor</Button>
                    <Button component={Link} to={DISCOVER_ROUTES.providerWorkspace.path} variant="outlined">Back to workspace</Button>
                    <Button
                      variant="outlined"
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
                  {copyStatus ? (
                    <Alert severity={copyStatus.startsWith("Public URL copied") ? "success" : "warning"} variant="outlined" sx={{ mb: 2 }}>
                      {copyStatus}
                    </Alert>
                  ) : null}
                  <LandingPageRenderer page={page} snapshot={snapshot} renderMode="PROVIDER_DRAFT_PREVIEW" />
                </Box>
              )}
            </Stack>
          </article>
      </div>
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
