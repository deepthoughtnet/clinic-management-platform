import {
  AssignmentOutlined,
  ChevronLeftRounded,
  ChevronRightRounded,
  CheckCircleOutlined,
  CloseRounded,
  DirectionsCarOutlined,
  LanguageOutlined,
  MedicalServicesOutlined,
  PlaceOutlined,
  SchoolOutlined,
} from "@mui/icons-material";
import { type ReactNode, useEffect, useState } from "react";
import { PublicMediaImage } from "../landing/PublicMediaImage";
import { LocationDisplayMap } from "../location";
import type { LocationDisplayMapProps } from "../location/LocationDisplayMap";
import {
  BookingCapabilityBadge,
  type BookingMode,
} from "./BookingCapability";

type ProviderType = "INDIVIDUAL_DOCTOR" | "CLINIC" | "HOSPITAL";

export type PublicProviderProfileDefinitionItem = {
  label: string;
  value: string;
  wide?: boolean;
};

export type PublicProviderProfileGalleryItem = {
  url: string;
  caption: string;
  alt: string;
};

export type PublicProviderVerificationBadge = {
  key: string;
  label: string;
  icon: ReactNode;
  tone?: "default" | "success" | "info" | "muted" | "warning";
  tooltip?: string;
};

export type PublicProviderServiceCard = {
  key: string;
  title: string;
  description: string;
  icon: ReactNode;
};

export type PublicProviderScheduleDay = {
  day: string;
  hours: string;
  current?: boolean;
  closed?: boolean;
};

export type PublicProviderProfileProps = {
  providerType: ProviderType;
  displayName: string;
  profileEyebrow?: string | null;
  heroSummary?: string | null;
  tagline?: string | null;
  bookingMode?: BookingMode;
  coverImageUrl?: string | null;
  avatarImageUrl?: string | null;
  imageToken?: string | null;
  primarySpeciality?: string | null;
  locationSummary?: string | null;
  yearsOfExperience?: number | null;
  consultationFeeLabel?: string | null;
  languages?: string[];
  teleconsultationAvailable?: boolean;
  heroSupplement?: ReactNode;
  verificationBadges?: PublicProviderVerificationBadge[];
  bookingUrl: string;
  bookingLabel?: string;
  callHref?: string | null;
  callLabel?: string | null;
  biographyTitle: string;
  biography?: string | null;
  biographyEmptyTitle?: string;
  biographyEmptyDescription?: string;
  afterBiographyContent?: ReactNode;
  professionalInformation: PublicProviderProfileDefinitionItem[];
  professionalInformationTitle?: string;
  services: string[];
  servicesTitle?: string;
  servicesEmptyTitle?: string;
  serviceCards?: PublicProviderServiceCard[];
  facilitiesTitle?: string | null;
  facilities?: string[];
  galleryTitle?: string;
  galleryItems: PublicProviderProfileGalleryItem[];
  galleryInteractive?: boolean;
  galleryEmptyTitle?: string;
  galleryEmptyActionLabel?: string | null;
  onGalleryEmptyAction?: (() => void) | null;
  locationTitle?: string;
  locationName?: string | null;
  locationAddress?: string | null;
  locationWorkingHours?: string | null;
  workingHoursSchedule?: PublicProviderScheduleDay[];
  locationFacilities?: string[];
  locations?: LocationDisplayMapProps["locations"];
  locationEmptyTitle?: string;
  locationEmptyDescription?: string;
  trustTitle?: string;
  trustIndicators?: string[];
  trustSupportingCopy?: string | null;
  trustEmptyTitle?: string;
  appointmentTitle?: string;
  consultationModes?: string[];
  appointmentEmptyFeeText?: string;
  appointmentEmptyModesText?: string;
  showAppointmentSection?: boolean;
  className?: string;
  dataTestId?: string;
  preview?: boolean;
};

function providerTypeLabel(providerType: ProviderType) {
  switch (providerType) {
    case "INDIVIDUAL_DOCTOR":
      return "Doctor";
    case "CLINIC":
      return "Clinic";
    case "HOSPITAL":
      return "Hospital";
    default:
      return "Provider";
  }
}

function providerFallbackBadge(providerType: ProviderType) {
  switch (providerType) {
    case "INDIVIDUAL_DOCTOR":
      return "DR";
    case "CLINIC":
      return "CL";
    case "HOSPITAL":
      return "HS";
    default:
      return "PR";
  }
}

export function PublicProviderProfile({
  providerType,
  displayName,
  profileEyebrow,
  heroSummary,
  tagline,
  bookingMode,
  coverImageUrl,
  avatarImageUrl,
  imageToken,
  primarySpeciality,
  locationSummary,
  yearsOfExperience,
  consultationFeeLabel,
  languages = [],
  teleconsultationAvailable = false,
  heroSupplement,
  verificationBadges = [],
  bookingUrl,
  bookingLabel = "Book Appointment",
  callHref,
  callLabel,
  biographyTitle,
  biography,
  biographyEmptyTitle = "Biography not added yet.",
  biographyEmptyDescription = "Provider background information will appear here when it is shared publicly.",
  afterBiographyContent,
  professionalInformation,
  professionalInformationTitle = "Professional Information",
  services,
  servicesTitle = "Services",
  servicesEmptyTitle = "No services selected yet.",
  serviceCards,
  facilitiesTitle,
  facilities = [],
  galleryTitle = "Clinic image gallery",
  galleryItems,
  galleryInteractive = false,
  galleryEmptyTitle = "No clinic gallery images have been added yet.",
  galleryEmptyActionLabel,
  onGalleryEmptyAction,
  locationTitle = "Location and access",
  locationName,
  locationAddress,
  locationWorkingHours,
  workingHoursSchedule,
  locationFacilities = [],
  locations,
  locationEmptyTitle = "Location details are missing.",
  locationEmptyDescription = "Address and map information will appear here when available.",
  trustTitle = "Trust and Verification",
  trustIndicators = [],
  trustSupportingCopy,
  trustEmptyTitle = "Public verification indicators are not available yet.",
  appointmentTitle = "Book Appointment",
  consultationModes = [],
  appointmentEmptyFeeText = "Consultation fee will appear once added.",
  appointmentEmptyModesText = "Consultation modes will appear once configured.",
  showAppointmentSection = true,
  className,
  dataTestId,
  preview = false,
}: PublicProviderProfileProps) {
  const [selectedGalleryIndex, setSelectedGalleryIndex] = useState<number | null>(null);
  const rootClassName = [
    "provider-preview-profile",
    "profile-preview-card",
    "provider-public-preview",
    className,
  ]
    .filter(Boolean)
    .join(" ");
  const locationAvailable = Boolean(locationName || locationAddress || (locations?.length ?? 0) > 0);
  const selectedGalleryItem = selectedGalleryIndex == null ? null : galleryItems[selectedGalleryIndex] ?? null;

  useEffect(() => {
    if (selectedGalleryIndex == null) {
      return;
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setSelectedGalleryIndex(null);
      }
      if (event.key === "ArrowLeft") {
        setSelectedGalleryIndex((current) => (current == null ? null : (current - 1 + galleryItems.length) % galleryItems.length));
      }
      if (event.key === "ArrowRight") {
        setSelectedGalleryIndex((current) => (current == null ? null : (current + 1) % galleryItems.length));
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [galleryItems.length, selectedGalleryIndex]);

  return (
    <article className={rootClassName} data-testid={dataTestId}>
      <section className="provider-preview-hero-shell">
        <div className={`provider-public-hero${preview ? " provider-public-hero--preview" : ""}`}>
          <div className="provider-public-hero-media">
            <div className="provider-public-cover-frame">
              <PublicMediaImage
                src={coverImageUrl}
                alt={`${displayName} cover image`}
                className="landing-cover-image"
                objectFit="cover"
                fallback={<div className="landing-cover-fallback" aria-hidden="true" />}
                loading="eager"
                token={imageToken}
              />
            </div>
            <div className="provider-public-avatar-frame">
              <PublicMediaImage
                src={avatarImageUrl}
                alt={`${displayName} ${providerType === "INDIVIDUAL_DOCTOR" ? "photo" : "logo"}`}
                className="landing-avatar-image"
                objectFit={providerType === "INDIVIDUAL_DOCTOR" ? "cover" : "contain"}
                fallback={(
                  <div className="landing-avatar-fallback" aria-hidden="true">
                    <span>{providerFallbackBadge(providerType)}</span>
                  </div>
                )}
                token={imageToken}
              />
            </div>
          </div>
          <div className="provider-preview-hero-copy">
            <div className="provider-preview-hero-heading">
              <span className="eyebrow">{profileEyebrow ?? `${providerTypeLabel(providerType)} profile`}</span>
              <h2>{displayName}</h2>
              {heroSummary ? <p>{heroSummary}</p> : null}
              {tagline ? <small>{tagline}</small> : null}
            </div>
            <div className="provider-preview-hero-facts">
              {primarySpeciality ? <span className="provider-preview-fact-chip"><MedicalServicesOutlined fontSize="small" aria-hidden="true" />{primarySpeciality}</span> : null}
              {locationSummary ? <span className="provider-preview-fact-chip"><PlaceOutlined fontSize="small" aria-hidden="true" />{locationSummary}</span> : null}
              {yearsOfExperience != null && !Number.isNaN(yearsOfExperience) ? <span className="provider-preview-fact-chip"><AssignmentOutlined fontSize="small" aria-hidden="true" />{yearsOfExperience} Years Experience</span> : null}
              {consultationFeeLabel ? <span className="provider-preview-fact-chip"><SchoolOutlined fontSize="small" aria-hidden="true" />{consultationFeeLabel} Consultation</span> : null}
              {languages.length ? <span className="provider-preview-fact-chip"><LanguageOutlined fontSize="small" aria-hidden="true" />{languages.join(" • ")}</span> : null}
              {teleconsultationAvailable ? <span className="provider-preview-fact-chip"><DirectionsCarOutlined fontSize="small" aria-hidden="true" />Teleconsultation available</span> : null}
              {bookingMode ? <BookingCapabilityBadge mode={bookingMode} compact /> : null}
            </div>
            {heroSupplement ? <div className="provider-preview-hero-supplement">{heroSupplement}</div> : null}
            <div className="provider-preview-hero-actions">
              <a className="primary-button" href={bookingUrl}>
                {bookingLabel}
              </a>
              {callHref && callLabel ? (
                <a className="secondary-button" href={callHref}>
                  {callLabel}
                </a>
              ) : null}
            </div>
          </div>
        </div>
      </section>

      <section className="provider-preview-section provider-preview-section--about">
        <div className="provider-preview-section-heading">
          <span className="eyebrow">About</span>
          <h2>{biographyTitle}</h2>
        </div>
        {biography?.trim() ? (
          <p className="provider-preview-biography">{biography}</p>
        ) : (
          <div className="provider-preview-empty-state">
            <strong>{biographyEmptyTitle}</strong>
            <p>{biographyEmptyDescription}</p>
          </div>
        )}
        {afterBiographyContent ? <div className="provider-preview-after-biography">{afterBiographyContent}</div> : null}
      </section>

      <div className="provider-preview-grid provider-preview-grid--paired">
        <section className="provider-preview-card">
          <div className="provider-preview-section-heading">
            <span className="eyebrow">Professional Information</span>
            <h2>{professionalInformationTitle}</h2>
          </div>
          {professionalInformation.length ? (
            <dl className="provider-preview-definition-list">
              {professionalInformation.map((item) => (
                <div className={item.wide ? "provider-preview-definition-list__item provider-preview-definition-list__item--wide" : "provider-preview-definition-list__item"} key={item.label}>
                  <dt>{item.label}</dt>
                  <dd>{item.value}</dd>
                </div>
              ))}
            </dl>
          ) : (
            <div className="provider-preview-empty-state">
              <strong>Professional details are still being added.</strong>
            </div>
          )}
        </section>

        <section className="provider-preview-card">
          <div className="provider-preview-section-heading">
            <span className="eyebrow">Services</span>
            <h2>{servicesTitle}</h2>
          </div>
          {services.length ? (
            serviceCards?.length ? (
              <div className="provider-preview-service-card-grid">
                {serviceCards.map((item) => (
                  <article className="provider-preview-service-card" key={item.key}>
                    <div className="provider-preview-service-card__icon" aria-hidden="true">
                      {item.icon}
                    </div>
                    <strong>{item.title}</strong>
                    <p>{item.description}</p>
                  </article>
                ))}
              </div>
            ) : (
              <div className="provider-preview-pill-grid">
                {services.map((item) => (
                  <span className="provider-preview-service-pill" key={item}>
                    <MedicalServicesOutlined fontSize="small" aria-hidden="true" />
                    {item}
                  </span>
                ))}
              </div>
            )
          ) : (
            <div className="provider-preview-empty-state">
              <strong>{servicesEmptyTitle}</strong>
            </div>
          )}
          {facilitiesTitle && facilities.length ? (
            <div className="provider-preview-subsection">
              <h3>{facilitiesTitle}</h3>
              <div className="provider-preview-pill-grid">
                {facilities.map((item) => (
                  <span className="provider-preview-facility-pill" key={item}>
                    <CheckCircleOutlined fontSize="small" aria-hidden="true" />
                    {item}
                  </span>
                ))}
              </div>
            </div>
          ) : null}
        </section>
      </div>

      <div className="provider-preview-grid provider-preview-grid--paired">
        <section className="provider-preview-card">
          <div className="provider-preview-section-heading">
            <span className="eyebrow">Gallery</span>
            <h2>{galleryTitle}</h2>
          </div>
          {galleryItems.length ? (
            <div className="provider-preview-gallery-grid">
              {galleryItems.map((item, index) => (
                <article className="provider-preview-gallery-card" key={`${item.url}-${index}`}>
                  <button
                    className={`provider-preview-gallery-card__trigger${galleryInteractive ? "" : " is-static"}`}
                    type="button"
                    onClick={galleryInteractive ? () => setSelectedGalleryIndex(index) : undefined}
                    aria-label={galleryInteractive ? `Open ${item.caption}` : item.caption}
                    disabled={!galleryInteractive}
                  >
                    <div className="landing-gallery-media">
                      <PublicMediaImage
                        src={item.url}
                        alt={item.alt}
                        className="landing-gallery-image"
                        objectFit="cover"
                        fallback={<div className="landing-gallery-fallback" aria-hidden="true" />}
                        token={imageToken}
                      />
                    </div>
                    <div className="provider-preview-gallery-card-copy">
                      <strong>{item.caption}</strong>
                      {galleryInteractive ? <span className="text-button">Open image</span> : <span className="provider-preview-gallery-caption">Published image</span>}
                    </div>
                  </button>
                </article>
              ))}
            </div>
          ) : (
            <div className="provider-preview-gallery-empty" role="status" aria-live="polite">
              <p>{galleryEmptyTitle}</p>
              {galleryEmptyActionLabel && onGalleryEmptyAction ? (
                <button className="secondary-button" type="button" onClick={onGalleryEmptyAction}>
                  {galleryEmptyActionLabel}
                </button>
              ) : null}
            </div>
          )}
        </section>

        <section className="provider-preview-card">
          <div className="provider-preview-section-heading">
            <span className="eyebrow">Location</span>
            <h2>{locationTitle}</h2>
          </div>
          {locationAvailable ? (
            <div className="provider-preview-location">
              <div className="provider-preview-location-copy">
                <strong>{locationName ?? displayName}</strong>
                <p>{locationAddress || "Address will appear once location details are completed."}</p>
                {workingHoursSchedule?.length ? (
                  <div className="provider-preview-schedule" aria-label="Weekly working hours">
                    {workingHoursSchedule.map((item) => (
                      <div className={`provider-preview-schedule-row${item.current ? " is-current" : ""}${item.closed ? " is-closed" : ""}`} key={item.day}>
                        <strong>{item.day}</strong>
                        <span>{item.hours}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="provider-preview-location-meta">
                    {locationWorkingHours?.trim() ? <span>Working hours: {locationWorkingHours.trim()}</span> : null}
                  </div>
                )}
                {locationFacilities.length ? (
                  <div className="provider-preview-pill-grid">
                    {locationFacilities.map((item) => <span className="provider-preview-facility-pill" key={`location-${item}`}>{item}</span>)}
                  </div>
                ) : null}
              </div>
              <LocationDisplayMap
                providerName={displayName}
                locations={locations}
                compact
                title="Find this location"
                directionsLabel="Get directions"
              />
            </div>
          ) : (
            <div className="provider-preview-empty-state">
              <strong>{locationEmptyTitle}</strong>
              <p>{locationEmptyDescription}</p>
            </div>
          )}
        </section>
      </div>

      <div className="provider-preview-grid provider-preview-grid--paired">
        <section className="provider-preview-card">
          <div className="provider-preview-section-heading">
            <span className="eyebrow">Trust and Verification</span>
            <h2>{trustTitle}</h2>
          </div>
          {verificationBadges.length ? (
            <div className="provider-preview-badge-grid">
              {verificationBadges.map((badge) => (
                <span
                  className={`provider-preview-verification-badge provider-preview-verification-badge--${badge.tone ?? "default"}`}
                  key={badge.key}
                  title={badge.tooltip ?? badge.label}
                >
                  {badge.icon}
                  <span>{badge.label}</span>
                </span>
              ))}
            </div>
          ) : trustIndicators.length ? (
            <div className="provider-preview-subsection provider-preview-subsection--trust">
              {trustSupportingCopy ? (
                <p className="provider-preview-supporting-copy">{trustSupportingCopy}</p>
              ) : null}
              <div className="provider-preview-pill-grid">
                {trustIndicators.map((item) => (
                  <span className="provider-preview-trust-pill" key={item}>
                    <CheckCircleOutlined fontSize="small" aria-hidden="true" />
                    {item}
                  </span>
                ))}
              </div>
            </div>
          ) : (
            <div className="provider-preview-empty-state">
              <strong>{trustEmptyTitle}</strong>
            </div>
          )}
        </section>
        {showAppointmentSection ? (
          <section className="provider-preview-card provider-preview-appointment-card">
            <div className="provider-preview-section-heading">
              <span className="eyebrow">Appointment</span>
              <h2>{appointmentTitle}</h2>
            </div>
            <div className="provider-preview-appointment-summary">
              <div>
                <strong>Consultation fee</strong>
                <span>{consultationFeeLabel ?? appointmentEmptyFeeText}</span>
              </div>
              <div>
                <strong>Consultation modes</strong>
                <span>{consultationModes.length ? consultationModes.join(" · ") : appointmentEmptyModesText}</span>
              </div>
            </div>
            <div className="provider-preview-appointment-actions">
              <a className="primary-button" href={bookingUrl}>
                {bookingLabel}
              </a>
              {callHref && callLabel ? (
                <a className="secondary-button" href={callHref}>
                  {callLabel}
                </a>
              ) : null}
            </div>
          </section>
        ) : null}
      </div>
      {selectedGalleryItem ? (
        <div className="provider-preview-gallery-lightbox" role="dialog" aria-modal="true" aria-label={selectedGalleryItem.caption}>
          <button
            className="provider-preview-gallery-lightbox__backdrop"
            type="button"
            aria-label="Close gallery preview"
            onClick={() => setSelectedGalleryIndex(null)}
          />
          <div className="provider-preview-gallery-lightbox__dialog">
            <div className="provider-preview-gallery-lightbox__header">
              <strong>{selectedGalleryItem.caption}</strong>
              <button className="icon-button" type="button" aria-label="Close preview" onClick={() => setSelectedGalleryIndex(null)}>
                <CloseRounded fontSize="small" aria-hidden="true" />
              </button>
            </div>
            <div className="provider-preview-gallery-lightbox__media">
              <button
                className="icon-button provider-preview-gallery-lightbox__nav"
                type="button"
                aria-label="Previous image"
                onClick={() => setSelectedGalleryIndex((current) => (current == null ? null : (current - 1 + galleryItems.length) % galleryItems.length))}
              >
                <ChevronLeftRounded fontSize="small" aria-hidden="true" />
              </button>
              <PublicMediaImage
                src={selectedGalleryItem.url}
                alt={selectedGalleryItem.alt}
                className="provider-preview-gallery-lightbox__image"
                objectFit="contain"
                fallback={<div className="landing-gallery-fallback" aria-hidden="true" />}
                loading="eager"
                token={imageToken}
              />
              <button
                className="icon-button provider-preview-gallery-lightbox__nav"
                type="button"
                aria-label="Next image"
                onClick={() => setSelectedGalleryIndex((current) => (current == null ? null : (current + 1) % galleryItems.length))}
              >
                <ChevronRightRounded fontSize="small" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </article>
  );
}
