import { type CSSProperties, type ReactNode } from "react";
import type { LandingPageRenderable, LandingProfile, LandingSection, LandingSnapshot, LandingTheme } from "../../api/providerLandingPage";
import { initials } from "../DiscoveryComponents";
import { LocationDisplayMap } from "../location";
import { PublicMediaImage } from "./PublicMediaImage";

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

function locationText(profile: LandingProfile) {
  const first = profile.locations[0];
  if (!first) {
    return [profile.area, profile.city].filter(Boolean).join(", ") || "Location details will be published soon.";
  }
  return [first.label, first.address, first.city, first.state, first.country, first.pinCode].filter(Boolean).join(" • ");
}

function summaryChips(profile: LandingProfile) {
  const chips = [
    profile.primarySpeciality,
    profile.yearsOfExperience != null ? `${profile.yearsOfExperience}+ years experience` : null,
    profile.onlineConsultation ? "Online consultations" : null,
    profile.emergencyAvailable ? "Emergency care" : null,
  ];
  return chips.filter((chip): chip is string => Boolean(chip));
}

function sectionCard(title: string, description?: string | null, children?: ReactNode) {
  return (
    <section className="landing-section-card">
      <div className="landing-section-heading">
        <h2>{title}</h2>
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

export function LandingHero({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const avatarUrl = profile.providerType === "INDIVIDUAL_DOCTOR" ? profile.imageUrl || profile.logoUrl : profile.logoUrl || profile.imageUrl;
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
          <p>{profile.summary || profile.biography || "A professional landing page built from structured provider information."}</p>
          <div className="landing-chip-row">
            {summaryChips(profile).map((chip) => (
              <span className="landing-chip" key={chip}>{chip}</span>
            ))}
          </div>
          <div className="cta-row">
            <a className="primary-button" href={profile.publicPath}>
              View public profile
            </a>
            {profile.contactPhone ? (
              <a className="secondary-button" href={`tel:${profile.contactPhone}`}>
                Call practice
              </a>
            ) : null}
          </div>
        </div>
        <div className="landing-hero-panel">
          <strong>{locationText(profile)}</strong>
          <ul>
            <li>{profile.specialities.slice(0, 4).join(" • ") || "Speciality details available after publication"}</li>
            <li>{profile.services.slice(0, 4).join(" • ") || "Services will appear here"}</li>
            <li>{profile.contactEmail || "Contact details will be shared here"}</li>
          </ul>
        </div>
      </div>
    </div>
  ));
}

export function LandingAbout({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-prose-grid">
      <p>{profile.biography || profile.summary || "About this provider will appear here once the profile is published."}</p>
      <ul className="landing-info-list">
        <li>
          <strong>Qualification</strong>
          <span>{profile.qualification || "Shared after review"}</span>
        </li>
        <li>
          <strong>Medical council</strong>
          <span>{profile.medicalCouncil || "Shared after review"}</span>
        </li>
        <li>
          <strong>Experience</strong>
          <span>{profile.yearsOfExperience != null ? `${profile.yearsOfExperience}+ years` : "Shared after review"}</span>
        </li>
      </ul>
    </div>
  ));
}

export function LandingServices({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-chip-grid">
      {(profile.services.length ? profile.services : ["Consultations", "Preventive care", "Referral support"]).map((service) => (
        <span key={service} className="landing-pill">{service}</span>
      ))}
    </div>
  ));
}

export function LandingDoctors({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const teams = profile.providerType === "INDIVIDUAL_DOCTOR"
    ? [
        profile.displayName,
        profile.qualification,
        profile.primarySpeciality,
      ]
    : profile.specialities.slice(0, 6);
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-doctor-grid">
      {teams.filter(Boolean).map((entry) => (
        <article className="landing-mini-card" key={entry as string}>
          <strong>{entry as string}</strong>
          <span>{profile.providerType === "INDIVIDUAL_DOCTOR" ? "Lead practitioner" : "Associated care team"}</span>
        </article>
      ))}
    </div>
  ));
}

export function LandingDepartments({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-chip-grid">
      {(profile.departments.length ? profile.departments : ["Primary care", "Diagnostics", "Speciality support"]).map((item) => (
        <span key={item} className="landing-pill">{item}</span>
      ))}
    </div>
  ));
}

export function LandingFacilities({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-chip-grid">
      {(profile.facilities.length ? profile.facilities : ["Waiting area", "Accessible entry", "Front desk support"]).map((item) => (
        <span key={item} className="landing-pill">{item}</span>
      ))}
    </div>
  ));
}

export function LandingHours({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const hours = profile.locations
    .map((location) => ({ label: location.label || location.city || "Location", workingHours: location.workingHours }))
    .filter((item) => Boolean(item.workingHours));
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-hours-list">
      {hours.length ? hours.map((item) => (
        <article key={item.label} className="landing-mini-card">
          <strong>{item.label}</strong>
          <span>{item.workingHours}</span>
        </article>
      )) : <p>Working hours will appear after the provider publishes location details.</p>}
    </div>
  ));
}

export function LandingGallery({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const galleryImages = profile.galleryImageUrls.length
    ? profile.galleryImageUrls.map((url, index) => ({
        url,
        caption: profile.gallery[index]?.caption || `Gallery image ${index + 1}`,
      }))
    : [];
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-gallery-grid">
      {galleryImages.length ? galleryImages.map((item, index) => (
        <article className="landing-gallery-card" key={`${item.url}-${index}`}>
          <div className="landing-gallery-media">
            <PublicMediaImage
              src={item.url}
              alt={landingImageAlt(profile, "gallery", index + 1)}
              className="landing-gallery-image"
              objectFit="cover"
              fallback={<div className="landing-gallery-fallback" aria-hidden="true" />}
            />
          </div>
          <strong>{item.caption}</strong>
        </article>
      )) : <p className="landing-gallery-empty">Gallery images will appear here once the provider uploads them.</p>}
    </div>
  ));
}

export function LandingInsurance({ profile: _profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-prose-grid">
      <p>Insurance and payment options will be listed here when enabled for this provider.</p>
      <div className="landing-chip-grid">
        <span className="landing-pill">Cash</span>
        <span className="landing-pill">Card</span>
        <span className="landing-pill">Insurance-ready</span>
      </div>
    </div>
  ));
}

export function LandingAwards({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-chip-grid">
      {(profile.emergencyAvailable ? ["Emergency care", "Safety protocols"] : ["Accreditation-ready", "Safety-first"]).map((item) => (
        <span key={item} className="landing-pill">{item}</span>
      ))}
    </div>
  ));
}

export function LandingFAQ({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const items = [
    ["How do I book an appointment?", "Use the appointment call to action or contact the practice directly."],
    ["What services are available?", profile.services.length ? profile.services.slice(0, 4).join(", ") : "Services will appear when published."],
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
  ));
}

export function LandingContact({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  const firstLocation = profile.locations[0];
  return sectionCard(sectionLabel(section), section.description, (
    <>
      <div className="landing-contact-grid">
        <div className="landing-mini-card">
          <strong>Contact</strong>
          <span>{profile.contactPhone || "Phone will be shared after publication"}</span>
          <span>{profile.contactEmail || "Email will be shared after publication"}</span>
        </div>
        <div className="landing-mini-card">
          <strong>Address</strong>
          <span>{firstLocation?.address || locationText(profile)}</span>
          <span>{[firstLocation?.city, firstLocation?.state, firstLocation?.country].filter(Boolean).join(", ")}</span>
        </div>
        <div className="landing-mini-card">
          <strong>Public route</strong>
          <span>{profile.publicPath}</span>
          <span>{profile.website || "Website link will appear here"}</span>
        </div>
      </div>
      <LocationDisplayMap
        providerName={profile.displayName}
        locations={profile.locations}
        compact
        title="Find us on the map"
        directionsLabel="Get directions"
      />
    </>
  ));
}

export function LandingCTA({ profile, section }: { profile: LandingProfile; section: LandingSection }) {
  return sectionCard(sectionLabel(section), section.description, (
    <div className="landing-cta-band">
      <div>
        <strong>Ready to book?</strong>
        <p>Use the public profile or call the practice to continue.</p>
      </div>
      <div className="cta-row">
        <a className="primary-button" href={profile.publicPath}>
          Open profile
        </a>
        {profile.contactPhone ? <a className="secondary-button" href={`tel:${profile.contactPhone}`}>Call now</a> : null}
      </div>
    </div>
  ));
}

function renderSection(profile: LandingProfile, section: LandingSection) {
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
      return <LandingContact profile={profile} section={section} />;
    case "CTA":
      return <LandingCTA profile={profile} section={section} />;
    default:
      return sectionCard(sectionLabel(section), section.description, <p>{section.title || "Configured section"}</p>);
  }
}

export function LandingPageRenderer({ page, snapshot }: { page: LandingPageRenderable; snapshot?: LandingSnapshot | null }) {
  const activeSnapshot = snapshot ?? page.publishedSnapshot ?? page.draft;
  if (!activeSnapshot) {
    return (
      <div className="landing-page">
        <div className="landing-page-shell">
          <header className="landing-page-header">
            <div>
              <span className="landing-eyebrow">Structured landing page</span>
              <h1>{page.displayName}</h1>
              <p>A published landing page will appear here after configuration is published.</p>
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
            <p>{page.profile.summary || page.profile.biography || "A structured landing page built from the published profile and template settings."}</p>
          </div>
          <div className="landing-page-meta">
            <span>{page.profile.providerType.replaceAll("_", " ").toLowerCase()}</span>
            <span>{page.canonicalSlug}</span>
            <span>{page.publicPath}</span>
          </div>
        </header>
        <div className="landing-page-sections">
          {sections.map((section) => renderSection(page.profile, section))}
        </div>
      </div>
    </div>
  );
}
