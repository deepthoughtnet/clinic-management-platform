import { type CSSProperties, type ReactNode, useEffect, useState } from "react";
import type { LandingPageRenderable, LandingProfile, LandingSection, LandingSnapshot, LandingTheme } from "../../api/providerLandingPage";
import { initials } from "../DiscoveryComponents";
import { LocationDisplayMap } from "../location";
import { PublicMediaImage } from "./PublicMediaImage";
import {
  ApartmentOutlined,
  ArticleOutlined,
  CallOutlined,
  ChevronLeftRounded,
  ChevronRightRounded,
  CloseRounded,
  EmailOutlined,
  InfoOutlined,
  LanguageOutlined,
  LocationOnOutlined,
  MedicalServicesOutlined,
  PendingActionsOutlined,
  PhotoLibraryOutlined,
  ScheduleOutlined,
  TaskAltOutlined,
  TravelExploreOutlined,
  VisibilityOutlined,
} from "@mui/icons-material";
import {
  buildPublicAddressView,
  formatWeeklyTimings,
  normalizeDisplayList,
  resolveClinicEstablishedYear,
} from "../../utils/publicProfileFormatting";

function themeVars(theme: LandingTheme): CSSProperties {
  return {
    ["--landing-primary" as never]: theme.primaryColor,
    ["--landing-accent" as never]: theme.accentColor,
    ["--landing-radius" as never]: theme.borderRadiusPreset === "small" ? "14px" : theme.borderRadiusPreset === "large" ? "24px" : "18px",
    ["--landing-typeface" as never]: theme.typographyPreset === "warm"
      ? '"Source Sans 3", "Aptos", "Segoe UI", sans-serif'
      : theme.typographyPreset === "institutional"
        ? '"IBM Plex Sans", "Aptos", "Segoe UI", sans-serif'
        : '"Inter", "Aptos", "Segoe UI", sans-serif',
    ["--landing-button-style" as never]: theme.buttonStyle,
  };
}

function safeSections(snapshot: LandingSnapshot | null | undefined) {
  return [...(snapshot?.sections ?? [])].filter((section) => section.enabled).sort((left, right) => left.displayOrder - right.displayOrder);
}

function sectionLabel(section: LandingSection) {
  return section.title?.trim() || section.key.replaceAll("_", " ");
}

function sectionIcon(sectionKey: string) {
  switch (sectionKey) {
    case "HERO":
      return <VisibilityOutlined fontSize="small" aria-hidden="true" />;
    case "ABOUT":
      return <InfoOutlined fontSize="small" aria-hidden="true" />;
    case "SERVICES":
      return <MedicalServicesOutlined fontSize="small" aria-hidden="true" />;
    case "DOCTORS":
      return <ArticleOutlined fontSize="small" aria-hidden="true" />;
    case "DEPARTMENTS":
      return <ApartmentOutlined fontSize="small" aria-hidden="true" />;
    case "FACILITIES":
      return <ApartmentOutlined fontSize="small" aria-hidden="true" />;
    case "WORKING_HOURS":
      return <ScheduleOutlined fontSize="small" aria-hidden="true" />;
    case "GALLERY":
      return <PhotoLibraryOutlined fontSize="small" aria-hidden="true" />;
    case "FAQ":
      return <InfoOutlined fontSize="small" aria-hidden="true" />;
    case "CONTACT":
      return <CallOutlined fontSize="small" aria-hidden="true" />;
    case "CTA":
      return <TaskAltOutlined fontSize="small" aria-hidden="true" />;
    default:
      return <TravelExploreOutlined fontSize="small" aria-hidden="true" />;
  }
}

function locationText(profile: LandingProfile) {
  const first = profile.locations?.[0];
  const view = buildPublicAddressView(first ?? {
    addressLine1: null,
    addressLine2: null,
    address: null,
    area: profile.area,
    city: profile.city,
    state: profile.state,
    country: profile.country,
    pinCode: null,
    postalCode: null,
  });
  return view.compact || first?.label || "Location not pinned.";
}

function publicRouteLabel(profile: LandingProfile) {
  return profile.publicPath || `/discover/${profile.providerType.toLowerCase()}s/${profile.canonicalSlug}`;
}

function publicRouteHref(profile: LandingProfile) {
  return profile.publicPath || `/discover/${profile.providerType.toLowerCase()}s/${profile.canonicalSlug}`;
}

function sanitizeText(value: unknown, fallback: string) {
  const text = typeof value === "string" ? value.trim() : "";
  return text || fallback;
}

function summaryChips(profile: LandingProfile) {
  const chips = [
    profile.primarySpeciality,
    profile.yearsOfExperience != null && Number.isFinite(profile.yearsOfExperience) && profile.yearsOfExperience >= 0 ? `${profile.yearsOfExperience} years experience` : null,
    profile.onlineConsultation ? "Online consultations" : null,
    profile.emergencyAvailable ? "Emergency care" : null,
  ];
  return normalizeDisplayList(chips);
}

function safeList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function safeLocationList(profile: LandingProfile) {
  return Array.isArray(profile.locations) ? profile.locations : [];
}

function sectionCard(title: string, description?: string | null, children?: ReactNode, icon?: ReactNode) {
  return (
    <section className="landing-section-card">
      <div className="landing-section-heading">
        <div className="landing-section-heading-row">
          <div className="landing-section-heading-badge">
            {icon ?? <TravelExploreOutlined fontSize="small" aria-hidden="true" />}
          </div>
          <h2>{title}</h2>
        </div>
        {description ? <p>{description}</p> : null}
      </div>
      {children}
    </section>
  );
}

function landingImageAlt(profile: LandingProfile, kind: "logo" | "cover" | "gallery", index?: number) {
  if (kind === "cover") {
    return `${profile.displayName} cover image`;
  }
  if (kind === "gallery") {
    return `${profile.displayName} gallery image ${index ?? 1}`;
  }
  return `${profile.displayName} logo`;
}

export function LandingHero({ profile, section, renderMode = "PUBLIC_PROFILE" }: { profile: LandingProfile; section: LandingSection; renderMode?: "PROVIDER_DRAFT_PREVIEW" | "PROVIDER_REVIEW_STATUS" | "PLATFORM_REVIEW_PREVIEW" | "PUBLIC_PROFILE" }) {
  const avatarUrl = profile.providerType === "INDIVIDUAL_DOCTOR" ? profile.imageUrl || profile.logoUrl : profile.logoUrl || profile.imageUrl;
  const specialities = normalizeDisplayList(safeList(profile.specialities));
  const locationView = buildPublicAddressView(profile.locations?.[0] ?? {
    addressLine1: null,
    addressLine2: null,
    address: null,
    area: profile.area,
    city: profile.city,
    state: profile.state,
    country: profile.country,
    pinCode: null,
    postalCode: null,
  });
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-hero">
      <div className="landing-hero-media">
        <div className="landing-cover-frame">
          <PublicMediaImage
            src={profile.coverUrl}
            alt={landingImageAlt(profile, "cover")}
            className="landing-cover-image"
            objectFit="cover"
            fallback={<div className="landing-cover-fallback" aria-hidden="true" />}
            loading="eager"
          />
        </div>
        <div className="landing-avatar-frame">
          <PublicMediaImage
            src={avatarUrl}
            alt={landingImageAlt(profile, "logo")}
            className="landing-avatar-image"
            objectFit="contain"
            fallback={(
              <div className="landing-avatar-fallback" aria-hidden="true">
                <span>{initials(profile.displayName)}</span>
              </div>
            )}
            loading="eager"
          />
        </div>
      </div>
      <div className="landing-hero-body">
        <div className="landing-hero-copy">
          <span className="landing-eyebrow">{profile.providerType === "INDIVIDUAL_DOCTOR" ? "Doctor profile" : profile.providerType === "CLINIC" ? "Clinic profile" : "Hospital profile"}</span>
          <h1>{profile.displayName}</h1>
          <p>{profile.summary || profile.biography || "Description not provided."}</p>
          <div className="landing-chip-row">
            {summaryChips(profile).map((chip) => (
              <span className="landing-chip" key={chip}>{chip}</span>
            ))}
          </div>
        </div>
        <div className="landing-hero-panel">
          <div className="landing-mini-card landing-mini-card--icon">
            <div className="landing-mini-card__heading">
              <span className="landing-mini-card__icon"><LocationOnOutlined fontSize="small" aria-hidden="true" /></span>
              <strong>Location</strong>
            </div>
            <span>{locationView.compact || "Location not pinned."}</span>
          </div>
          {specialities.length ? (
            <div className="landing-mini-card landing-mini-card--icon">
              <div className="landing-mini-card__heading">
                <span className="landing-mini-card__icon"><MedicalServicesOutlined fontSize="small" aria-hidden="true" /></span>
                <strong>Primary care</strong>
              </div>
              <span>{specialities.slice(0, 3).join("\n")}</span>
            </div>
          ) : null}
          {profile.contactPhone ? (
            <div className="landing-mini-card landing-mini-card--icon">
              <div className="landing-mini-card__heading">
                <span className="landing-mini-card__icon"><CallOutlined fontSize="small" aria-hidden="true" /></span>
                <strong>Phone</strong>
              </div>
              <span>{profile.contactPhone}</span>
            </div>
          ) : null}
          {profile.contactEmail ? (
            <div className="landing-mini-card landing-mini-card--icon">
              <div className="landing-mini-card__heading">
                <span className="landing-mini-card__icon"><EmailOutlined fontSize="small" aria-hidden="true" /></span>
                <strong>Email</strong>
              </div>
              <span>{profile.contactEmail}</span>
            </div>
          ) : null}
          {profile.website ? (
            <div className="landing-mini-card landing-mini-card--icon">
              <div className="landing-mini-card__heading">
                <span className="landing-mini-card__icon"><LanguageOutlined fontSize="small" aria-hidden="true" /></span>
                <strong>Website</strong>
              </div>
              <span>{profile.website}</span>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  ), sectionIcon(section.key));
}

export function LandingAbout({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-prose-grid">
      <p>{profile.biography || profile.summary || "Not provided."}</p>
      <dl className="landing-info-list">
        {profile.providerType === "INDIVIDUAL_DOCTOR" ? (
          <>
            <div>
              <dt>Qualification</dt>
              <dd>{profile.qualification?.trim() || "Not provided"}</dd>
            </div>
            <div>
              <dt>Medical council</dt>
              <dd>{profile.medicalCouncil?.trim() || "Not provided"}</dd>
            </div>
            <div>
              <dt>Experience</dt>
              <dd>{profile.yearsOfExperience != null && Number.isFinite(profile.yearsOfExperience) && profile.yearsOfExperience >= 0 ? `${profile.yearsOfExperience} years experience` : "Not provided"}</dd>
            </div>
          </>
        ) : profile.providerType === "CLINIC" ? (
          <>
            <div>
              <dt>Established year</dt>
              <dd>{resolveClinicEstablishedYear(profile.establishedYear, profile.registrationNumber) ?? "Not provided"}</dd>
            </div>
            <div>
              <dt>Registration number</dt>
              <dd>{profile.registrationNumber?.trim() || "Not provided"}</dd>
            </div>
            <div>
              <dt>Clinic philosophy</dt>
              <dd>{profile.clinicPhilosophy?.trim() || "Not provided"}</dd>
            </div>
            <div>
              <dt>Emergency availability</dt>
              <dd>{profile.emergencyAvailability?.trim() || (profile.emergencyAvailable ? "Available" : "Not provided")}</dd>
            </div>
          </>
        ) : (
          <>
            <div>
              <dt>Hospital facts</dt>
              <dd>{profile.summary || "Not provided"}</dd>
            </div>
            <div>
              <dt>Emergency availability</dt>
              <dd>{profile.emergencyAvailable ? "Available" : "Not provided"}</dd>
            </div>
          </>
        )}
      </dl>
    </div>
  ), sectionIcon(section.key));
}

export function LandingServices({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const services = normalizeDisplayList(safeList(profile.services));
  return sectionCard(sectionLabel(section), section.description, (
    services.length ? (
      <div className="landing-chip-grid">
        {services.map((service) => (
          <span key={service} className="landing-pill">{service}</span>
        ))}
      </div>
    ) : (
      <p className="landing-empty-state">No services added yet.</p>
    )
  ), sectionIcon(section.key));
}

export function LandingDoctors({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const specialities = normalizeDisplayList(safeList(profile.specialities));
  const teams = profile.providerType === "INDIVIDUAL_DOCTOR"
    ? [
        profile.displayName,
        profile.qualification,
        profile.primarySpeciality,
      ]
    : specialities.slice(0, 6);
  return sectionCard(sectionLabel(section), section.description, (
    teams.filter(Boolean).length ? (
      <div className="landing-doctor-grid">
        {teams.filter(Boolean).map((entry) => (
          <article className="landing-mini-card" key={entry as string}>
            <strong>{entry as string}</strong>
            <span>{profile.providerType === "INDIVIDUAL_DOCTOR" ? "Lead practitioner" : "Associated care team"}</span>
          </article>
        ))}
      </div>
    ) : (
      <p className="landing-empty-state">{profile.providerType === "INDIVIDUAL_DOCTOR" ? "Qualifications not provided." : "No specialities added yet."}</p>
    )
  ), sectionIcon(section.key));
}

export function LandingDepartments({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const departments = normalizeDisplayList(safeList(profile.departments));
  return sectionCard(sectionLabel(section), section.description, (
    departments.length ? (
      <div className="landing-chip-grid">
        {departments.map((item) => (
          <span key={item} className="landing-pill">{item}</span>
        ))}
      </div>
    ) : (
      <p className="landing-empty-state">No specialities added yet.</p>
    )
  ), sectionIcon(section.key));
}

export function LandingFacilities({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const facilities = normalizeDisplayList(safeList(profile.facilities));
  return sectionCard(sectionLabel(section), section.description, (
    facilities.length ? (
      <div className="landing-chip-grid">
        {facilities.map((item) => (
          <span key={item} className="landing-pill">{item}</span>
        ))}
      </div>
    ) : (
      <p className="landing-empty-state">No facilities configured.</p>
    )
  ), sectionIcon(section.key));
}

export function LandingHours({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const weekly = formatWeeklyTimings(profile.weeklyTimings, profile.timezone);
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-hours-list">
      {weekly.rows.length ? (
        <table className="landing-hours-table">
          <thead>
            <tr>
              <th scope="col">Day</th>
              <th scope="col">Hours</th>
            </tr>
          </thead>
          <tbody>
            {weekly.rows.map((item) => (
              <tr key={item.day} className={`landing-hours-table__row${item.closed ? " is-closed" : ""}${item.dayOfWeek === "SATURDAY" || item.dayOfWeek === "SUNDAY" ? " is-weekend" : ""}`}>
                <th scope="row">{item.day}</th>
                <td>
                  {item.closed ? (
                    <span className="landing-hours-table__closed">Closed</span>
                  ) : (
                    <span className="landing-hours-table__hours">{item.sessions.join(" · ")}</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="landing-empty-state">Working hours have not been configured.</p>
      )}
      {weekly.warnings.length ? <p className="landing-empty-state landing-empty-state--warning">{weekly.warnings[0]}</p> : null}
      {weekly.timezone ? <p className="landing-hours-table__timezone">Timezone: {weekly.timezone}</p> : null}
    </div>
  ), sectionIcon(section.key));
}

export function LandingGallery({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const galleryImageUrls = Array.isArray(profile.galleryImageUrls) ? profile.galleryImageUrls : [];
  const gallery = Array.isArray(profile.gallery) ? profile.gallery : [];
  const [selectedGalleryIndex, setSelectedGalleryIndex] = useState<number | null>(null);
  const galleryImages = galleryImageUrls.length
    ? galleryImageUrls.map((url, index) => {
        const item = gallery[index] ?? { caption: null };
        const fallbackCaption = `Clinic gallery image ${index + 1}`;
        const caption = sanitizeText(item.caption, fallbackCaption);
        return {
          url,
          caption,
          alt: sanitizeText((item as { alt?: string | null }).alt, caption),
          position: `${index + 1} of ${galleryImageUrls.length}`,
        };
      })
    : [];

  useEffect(() => {
    if (selectedGalleryIndex == null) {
      return;
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setSelectedGalleryIndex(null);
      }
      if (event.key === "ArrowLeft") {
        setSelectedGalleryIndex((current) => (current == null ? null : (current - 1 + galleryImages.length) % galleryImages.length));
      }
      if (event.key === "ArrowRight") {
        setSelectedGalleryIndex((current) => (current == null ? null : (current + 1) % galleryImages.length));
      }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [galleryImages.length, selectedGalleryIndex]);

  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-gallery-grid">
      {galleryImages.length ? galleryImages.map((item, index) => (
        <article className="landing-gallery-card" key={`${item.url}-${index}`}>
          <button
            className="landing-gallery-card__trigger"
            type="button"
            onClick={() => setSelectedGalleryIndex(index)}
            aria-label={`Open ${item.caption}`}
          >
            <div className="landing-gallery-media">
              <PublicMediaImage
                src={item.url}
                alt={item.alt}
                className="landing-gallery-image"
                objectFit="cover"
                fallback={<div className="landing-gallery-fallback" aria-hidden="true" />}
              />
            </div>
            <div className="landing-gallery-card__meta">
              <strong>{item.caption}</strong>
              <span className="landing-gallery-position">{item.position}</span>
            </div>
          </button>
        </article>
      )) : <p className="landing-gallery-empty">No gallery images uploaded.</p>}
      {selectedGalleryIndex != null && galleryImages[selectedGalleryIndex] ? (
        <div className="provider-preview-gallery-lightbox" role="dialog" aria-modal="true" aria-label={galleryImages[selectedGalleryIndex].caption}>
          <button
            className="provider-preview-gallery-lightbox__backdrop"
            type="button"
            aria-label="Close gallery preview"
            onClick={() => setSelectedGalleryIndex(null)}
          />
          <div className="provider-preview-gallery-lightbox__dialog">
            <div className="provider-preview-gallery-lightbox__header">
              <strong>{galleryImages[selectedGalleryIndex].caption}</strong>
              <button className="icon-button" type="button" aria-label="Close preview" onClick={() => setSelectedGalleryIndex(null)}>
                <CloseRounded fontSize="small" aria-hidden="true" />
              </button>
            </div>
            <div className="provider-preview-gallery-lightbox__media">
              <button
                className="icon-button provider-preview-gallery-lightbox__nav"
                type="button"
                aria-label="Previous image"
                onClick={() => setSelectedGalleryIndex((current) => (current == null ? null : (current - 1 + galleryImages.length) % galleryImages.length))}
              >
                <ChevronLeftRounded fontSize="small" aria-hidden="true" />
              </button>
              <PublicMediaImage
                src={galleryImages[selectedGalleryIndex].url}
                alt={galleryImages[selectedGalleryIndex].alt}
                className="provider-preview-gallery-lightbox__image"
                objectFit="contain"
                fallback={<div className="landing-gallery-fallback" aria-hidden="true" />}
              />
              <button
                className="icon-button provider-preview-gallery-lightbox__nav"
                type="button"
                aria-label="Next image"
                onClick={() => setSelectedGalleryIndex((current) => (current == null ? null : (current + 1) % galleryImages.length))}
              >
                <ChevronRightRounded fontSize="small" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  ), sectionIcon(section.key));
}

export function LandingInsurance({ profile: _profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-prose-grid">
      <p>Consultation fees have not been configured.</p>
    </div>
  ), sectionIcon(section.key));
}

export function LandingAwards({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-chip-grid">
      {profile.emergencyAvailable ? (
        <span className="landing-pill">Emergency care</span>
      ) : (
        <span className="landing-empty-state">No awards added.</span>
      )}
    </div>
  ), sectionIcon(section.key));
}

export function LandingFAQ({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const services = safeList(profile.services);
  const items = [
    ["How do I book an appointment?", "Use the appointment call to action or contact the practice directly."],
    ["What services are available?", services.length ? services.slice(0, 4).join(", ") : "No services added yet."],
    ["Where is the practice located?", locationText(profile)],
  ] as const;
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-faq-list">
      {items.map(([question, answer]) => (
        <details key={question} className="landing-faq-item">
          <summary>{question}</summary>
          <p>{answer}</p>
        </details>
      ))}
    </div>
  ), sectionIcon(section.key));
}

export function LandingContact({ profile, section, renderMode = "PUBLIC_PROFILE" }: { profile: LandingProfile; section: LandingSection; renderMode?: "PROVIDER_DRAFT_PREVIEW" | "PROVIDER_REVIEW_STATUS" | "PLATFORM_REVIEW_PREVIEW" | "PUBLIC_PROFILE" }) {
  const [copyFeedback, setCopyFeedback] = useState<string | null>(null);
  const firstLocation = safeLocationList(profile)[0];
  const addressView = buildPublicAddressView(firstLocation ?? {
    addressLine1: null,
    addressLine2: null,
    address: null,
    area: profile.area,
    city: profile.city,
    state: profile.state,
    country: profile.country,
    pinCode: null,
    postalCode: null,
  });
  const phoneHref = profile.contactPhone ? `tel:${profile.contactPhone.replace(/[^\d+]/g, "")}` : null;
  const whatsappHref = profile.contactPhone ? `https://wa.me/${profile.contactPhone.replace(/\D/g, "")}` : null;
  const publicUrl = publicRouteHref(profile);
  const handleCopyPublicUrl = async () => {
    try {
      if (typeof navigator === "undefined" || !navigator.clipboard?.writeText) {
        throw new Error("clipboard unavailable");
      }
      await navigator.clipboard.writeText(publicUrl);
      setCopyFeedback("Public URL copied.");
    } catch {
      setCopyFeedback("Could not copy the Public URL.");
    }
  };
  return sectionCard(sectionLabel(section), section.description, (
    <>
      <div className="landing-contact-grid">
        <div className="landing-mini-card landing-contact-card">
          <div className="landing-mini-card__heading">
            <span className="landing-mini-card__icon"><CallOutlined fontSize="small" aria-hidden="true" /></span>
            <strong>Contact methods</strong>
          </div>
          {profile.contactPhone ? <div className="landing-contact-row"><span>Phone</span><a href={phoneHref ?? undefined}>{profile.contactPhone}</a></div> : null}
          {profile.contactEmail ? <div className="landing-contact-row"><span>Email</span><a href={`mailto:${profile.contactEmail}`}>{profile.contactEmail}</a></div> : null}
          {profile.website ? <div className="landing-contact-row"><span>Website</span><a href={profile.website} target="_blank" rel="noopener noreferrer">{profile.website}</a></div> : null}
          {profile.contactPhone && whatsappHref ? <div className="landing-contact-row"><span>WhatsApp</span><a href={whatsappHref} target="_blank" rel="noopener noreferrer">{profile.contactPhone}</a></div> : null}
          {!profile.contactPhone && !profile.contactEmail && !profile.website ? <span>Contact details not configured.</span> : null}
        </div>
        <div className="landing-mini-card landing-contact-card">
          <div className="landing-mini-card__heading">
            <span className="landing-mini-card__icon"><LocationOnOutlined fontSize="small" aria-hidden="true" /></span>
            <strong>Public address</strong>
          </div>
          {addressView.lines.length ? addressView.lines.map((line) => <div key={line}>{line}</div>) : <span>Location not pinned. Pinning an exact location will help patients find the clinic.</span>}
        </div>
          <div className="landing-mini-card landing-contact-card landing-contact-card--wide">
            <div className="landing-mini-card__heading">
              <span className="landing-mini-card__icon"><TravelExploreOutlined fontSize="small" aria-hidden="true" /></span>
              <strong>Public URL</strong>
            </div>
          {renderMode === "PROVIDER_REVIEW_STATUS" ? (
            <div className="landing-review-immutable-state">
              <strong>Submission under Platform review</strong>
              <span>Public visibility: Not published</span>
            </div>
          ) : (
            <div className="landing-public-url-row">
              {renderMode === "PUBLIC_PROFILE" ? (
                <a href={publicUrl} className="landing-route-link">{publicUrl}</a>
              ) : (
                <span className="landing-route-link">{publicUrl}</span>
              )}
              {renderMode === "PUBLIC_PROFILE" ? (
                <button type="button" className="secondary-button landing-public-url-copy" onClick={() => void handleCopyPublicUrl()}>
                  Copy
                </button>
              ) : null}
            </div>
          )}
          {copyFeedback ? <p className="landing-public-url-feedback" aria-live="polite">{copyFeedback}</p> : null}
        </div>
      </div>
      {safeLocationList(profile).length ? (
        <LocationDisplayMap
          providerName={profile.displayName}
          locations={safeLocationList(profile)}
          compact
          title="Find us on the map"
          directionsLabel="Get directions"
        />
      ) : <p className="landing-empty-state">Location not pinned. Pinning an exact location will help patients find the clinic.</p>}
    </>
  ), sectionIcon(section.key));
}

export function LandingCTA({ profile, section, renderMode = "PUBLIC_PROFILE" }: { profile: LandingProfile; section: LandingSection; renderMode?: "PROVIDER_DRAFT_PREVIEW" | "PROVIDER_REVIEW_STATUS" | "PLATFORM_REVIEW_PREVIEW" | "PUBLIC_PROFILE" }) {
  return sectionCard(sectionLabel(section), section.description, (
    renderMode === "PUBLIC_PROFILE" ? (
      <div className="landing-cta-band">
        <div>
          <strong>Ready to book?</strong>
          <p>{profile.contactPhone ? "Use the public profile or call the practice to continue." : "Public contact methods have not been configured."}</p>
        </div>
        <div className="cta-row">
          <a className="primary-button" href={profile.publicPath}>
            Open profile
          </a>
          {profile.contactPhone ? <a className="secondary-button" href={`tel:${profile.contactPhone}`}>Call now</a> : null}
        </div>
      </div>
    ) : (
      <div className="landing-cta-band landing-cta-band--preview">
        <div>
          <strong>Submission under Platform review</strong>
          <p>Later draft changes will not affect this submitted snapshot.</p>
        </div>
        <div className="cta-row">
          <span className="landing-hero-meta">Public visibility: Not published</span>
        </div>
      </div>
    )
  ), sectionIcon(section.key));
}

function renderSection(profile: LandingProfile, section: LandingSection, renderMode: "PROVIDER_DRAFT_PREVIEW" | "PROVIDER_REVIEW_STATUS" | "PLATFORM_REVIEW_PREVIEW" | "PUBLIC_PROFILE") {
  const renderedSection = (() => {
    switch (section.key) {
      case "HERO":
        return <LandingHero profile={profile} section={section} />;
      case "ABOUT":
        return <LandingAbout profile={profile} section={section} />;
      case "SERVICES":
        return <LandingServices profile={profile} section={section} />;
      case "DOCTORS":
        return <LandingDoctors profile={profile} section={section} />;
      case "DEPARTMENTS":
        return <LandingDepartments profile={profile} section={section} />;
      case "FACILITIES":
        return <LandingFacilities profile={profile} section={section} />;
      case "WORKING_HOURS":
        return <LandingHours profile={profile} section={section} />;
      case "GALLERY":
        return <LandingGallery profile={profile} section={section} />;
      case "INSURANCE":
        return <LandingInsurance profile={profile} section={section} />;
      case "AWARDS":
        return <LandingAwards profile={profile} section={section} />;
      case "FAQ":
        return <LandingFAQ profile={profile} section={section} />;
      case "CONTACT":
        return <LandingContact profile={profile} section={section} renderMode={renderMode} />;
      case "CTA":
        return <LandingCTA profile={profile} section={section} renderMode={renderMode} />;
      default:
        return sectionCard(sectionLabel(section), section.description, <p>{section.title || "Configured section"}</p>);
    }
  })();
  switch (section.key) {
    default:
      return renderedSection;
  }
}

export function LandingPageRenderer({ page, snapshot, renderMode = "PUBLIC_PROFILE" }: { page: LandingPageRenderable; snapshot?: LandingSnapshot | null; renderMode?: "PROVIDER_DRAFT_PREVIEW" | "PROVIDER_REVIEW_STATUS" | "PLATFORM_REVIEW_PREVIEW" | "PUBLIC_PROFILE" }) {
  const activeSnapshot = snapshot ?? page.publishedSnapshot ?? page.draft;
  if (!activeSnapshot) {
    return (
      <div className="landing-page">
        <div className="landing-page-shell">
          <header className="landing-page-header">
            <div>
              <span className="landing-eyebrow">Structured landing page</span>
              <h1>{page.displayName}</h1>
              <p>No landing page content is available yet.</p>
            </div>
          </header>
        </div>
      </div>
    );
  }
  const sections = safeSections(activeSnapshot);
  return (
    <div className="landing-page" style={themeVars(activeSnapshot.theme)}>
      <div className="landing-page-shell">
        <header className="landing-page-header">
          <div>
            <span className="landing-eyebrow">Structured landing page</span>
            <h1>{page.displayName}</h1>
            <p>{page.profile?.summary || page.profile?.biography || "A structured landing page built from the published profile and template settings."}</p>
          </div>
          {renderMode === "PUBLIC_PROFILE" || renderMode === "PLATFORM_REVIEW_PREVIEW" ? (
            <div className="landing-page-meta">
              <span>{page.profile?.providerType?.replaceAll("_", " ").toLowerCase() || "provider"}</span>
              <span>{page.canonicalSlug || "unassigned slug"}</span>
              <span>{page.publicPath || "public path pending"}</span>
            </div>
          ) : null}
        </header>
        <div className="landing-page-sections">
          {sections.map((section) => (
            <div
              key={section.key}
              id={`landing-section-${section.key}`}
              style={{
                scrollMarginTop: 96,
                outline: renderMode === "PLATFORM_REVIEW_PREVIEW" ? "none" : undefined,
              }}
            >
              {renderSection(page.profile, section, renderMode)}
            </div>
          ))}
          {renderMode === "PLATFORM_REVIEW_PREVIEW" ? (
            <section className="landing-review-metadata" aria-label="Reviewer-only metadata">
              <div className="landing-review-metadata__card">
                <div className="landing-mini-card__heading">
                  <strong>Reviewer-only metadata</strong>
                </div>
                <div className="landing-prose-grid">
                  <p>Canonical slug: {page.profile?.canonicalSlug || "Not set"}</p>
                  <p>Canonical path: {page.profile?.publicPath || "Not set"}</p>
                  <p>Template: {activeSnapshot.templateKey} v{activeSnapshot.templateVersion}</p>
                  <p>Submitted version: {page.profile?.publishedVersionNumber ?? "Not set"}</p>
                  <p>Public URL: {page.profile?.slug ? `/discover/${page.profile.providerType.toLowerCase()}s/${page.profile.slug}` : page.profile?.publicPath || "Not set"}</p>
                </div>
              </div>
            </section>
          ) : null}
        </div>
      </div>
    </div>
  );
}
