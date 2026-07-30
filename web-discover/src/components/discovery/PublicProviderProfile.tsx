import {
  AssignmentOutlined,
  CheckCircleOutlined,
  DirectionsCarOutlined,
  LanguageOutlined,
  MedicalServicesOutlined,
  PlaceOutlined,
  SchoolOutlined,
} from "@mui/icons-material";
import { PublicMediaImage } from "../landing/PublicMediaImage";
import { LocationDisplayMap } from "../location";
import type { LocationDisplayMapProps } from "../location/LocationDisplayMap";

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

export type PublicProviderProfileProps = {
  providerType: ProviderType;
  displayName: string;
  profileEyebrow?: string | null;
  heroSummary?: string | null;
  tagline?: string | null;
  coverImageUrl?: string | null;
  avatarImageUrl?: string | null;
  imageToken?: string | null;
  primarySpeciality?: string | null;
  locationSummary?: string | null;
  yearsOfExperience?: number | null;
  consultationFeeLabel?: string | null;
  languages?: string[];
  teleconsultationAvailable?: boolean;
  bookingUrl: string;
  bookingLabel?: string;
  callHref?: string | null;
  callLabel?: string | null;
  biographyTitle: string;
  biography?: string | null;
  biographyEmptyTitle?: string;
  biographyEmptyDescription?: string;
  professionalInformation: PublicProviderProfileDefinitionItem[];
  professionalInformationTitle?: string;
  services: string[];
  servicesTitle?: string;
  servicesEmptyTitle?: string;
  facilitiesTitle?: string | null;
  facilities?: string[];
  galleryTitle?: string;
  galleryItems: PublicProviderProfileGalleryItem[];
  galleryEmptyTitle?: string;
  galleryEmptyActionLabel?: string | null;
  onGalleryEmptyAction?: (() => void) | null;
  locationTitle?: string;
  locationName?: string | null;
  locationAddress?: string | null;
  locationWorkingHours?: string | null;
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
  coverImageUrl,
  avatarImageUrl,
  imageToken,
  primarySpeciality,
  locationSummary,
  yearsOfExperience,
  consultationFeeLabel,
  languages = [],
  teleconsultationAvailable = false,
  bookingUrl,
  bookingLabel = "Book Appointment",
  callHref,
  callLabel,
  biographyTitle,
  biography,
  biographyEmptyTitle = "Biography not added yet.",
  biographyEmptyDescription = "Provider background information will appear here when it is shared publicly.",
  professionalInformation,
  professionalInformationTitle = "Professional Information",
  services,
  servicesTitle = "Services",
  servicesEmptyTitle = "No services selected yet.",
  facilitiesTitle,
  facilities = [],
  galleryTitle = "Clinic image gallery",
  galleryItems,
  galleryEmptyTitle = "No clinic gallery images have been added yet.",
  galleryEmptyActionLabel,
  onGalleryEmptyAction,
  locationTitle = "Location and access",
  locationName,
  locationAddress,
  locationWorkingHours,
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
  className,
  dataTestId,
  preview = false,
}: PublicProviderProfileProps) {
  const rootClassName = [
    "provider-preview-profile",
    "profile-preview-card",
    "provider-public-preview",
    className,
  ]
    .filter(Boolean)
    .join(" ");
  const locationAvailable = Boolean(locationName || locationAddress || (locations?.length ?? 0) > 0);

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
            </div>
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
            <div className="provider-preview-pill-grid">
              {services.map((item) => (
                <span className="provider-preview-service-pill" key={item}>
                  <MedicalServicesOutlined fontSize="small" aria-hidden="true" />
                  {item}
                </span>
              ))}
            </div>
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
                    <a className="text-button" href={item.url} target="_blank" rel="noreferrer">
                      Open image
                    </a>
                  </div>
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
                <div className="provider-preview-location-meta">
                  {locationWorkingHours?.trim() ? <span>Working hours: {locationWorkingHours.trim()}</span> : null}
                </div>
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
          {trustIndicators.length ? (
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
      </div>
    </article>
  );
}
