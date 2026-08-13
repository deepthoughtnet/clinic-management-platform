import { type FormEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  AutoAwesomeOutlined,
  CurrencyRupeeOutlined,
  LanguageOutlined,
  LocationOnOutlined,
  ScheduleOutlined,
  StarRounded,
} from "@mui/icons-material";
import type {
  PublicClinicSummaryResponse,
  PublicDoctorSummaryResponse,
  PublicHospitalSummaryResponse,
  PublicPageResponse,
} from "../api/publicCatalog";
import { discoverConfig } from "../config";
import { DISCOVER_DETAIL_PATHS } from "../routes";
import { ProviderCardMedia } from "./discovery/ProviderCardMedia";
import {
  BookingCapabilityBadge,
  bookingCapabilityLabel,
  providerBookingPrimaryLabel,
  providerBookingSecondaryLabel,
  normalizeBookingMode,
} from "./discovery/BookingCapability";

export type FetchState<T> = {
  data: T;
  loading: boolean;
  error: string | null;
};

export const emptyDoctorsPage: PublicPageResponse<PublicDoctorSummaryResponse> = {
  items: [],
  page: 0,
  size: 12,
  totalItems: 0,
  totalPages: 0,
};

export const emptyClinicsPage: PublicPageResponse<PublicClinicSummaryResponse> = {
  items: [],
  page: 0,
  size: 12,
  totalItems: 0,
  totalPages: 0,
};

export const emptyHospitalsPage: PublicPageResponse<PublicHospitalSummaryResponse> = {
  items: [],
  page: 0,
  size: 12,
  totalItems: 0,
  totalPages: 0,
};

export const noPublicProfilesMessage =
  "Published provider profiles will appear here as the directory grows.";

type DirectoryCardDemoProps = {
  demo?: boolean;
  demoLabel?: string;
};

type HomeProviderCardChip = {
  key: string;
  label: string;
  variant?: "default" | "muted" | "success" | "info";
};

type HomeProviderCardProps = {
  providerType: "clinic" | "hospital";
  displayName: string;
  locationLabel: string;
  summary: string;
  logoUrl: string | null;
  coverUrl: string | null;
  demo: boolean;
  demoLabel: string;
  metaChips: HomeProviderCardChip[];
  statusChips: HomeProviderCardChip[];
  primaryAction: ReactNode;
  secondaryAction: ReactNode;
};

export function formatExperience(value: number | null | undefined) {
  if (value == null) {
    return "Experience shared after profile review";
  }
  return `${value}+ years experience`;
}

export function initials(label: string) {
  return label
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

function HealthcareAvatarFallback() {
  return (
    <svg className="directory-avatar-illustration" viewBox="0 0 64 64" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="avatarFallbackBase" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#E7F7F6" />
          <stop offset="100%" stopColor="#CFE8E8" />
        </linearGradient>
        <linearGradient id="avatarFallbackAccent" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#0F8B8D" />
          <stop offset="100%" stopColor="#0C7778" />
        </linearGradient>
      </defs>
      <circle cx="32" cy="32" r="30" fill="url(#avatarFallbackBase)" />
      <circle cx="32" cy="25" r="9" fill="#FFFFFF" fillOpacity="0.95" />
      <path
        d="M20 49c1.9-7.2 7.6-12 12-12s10.1 4.8 12 12"
        fill="none"
        stroke="url(#avatarFallbackAccent)"
        strokeWidth="6"
        strokeLinecap="round"
      />
      <path
        d="M31 19h2v5h5v2h-5v5h-2v-5h-5v-2h5z"
        fill="url(#avatarFallbackAccent)"
      />
      <circle cx="48" cy="18" r="4" fill="#FFFFFF" fillOpacity="0.82" />
      <path
        d="M46 18h4M48 16v4"
        stroke="#0F8B8D"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

function patientFacingLocationParts(...values: Array<string | null | undefined>) {
  return values
    .map((value) => value?.trim())
    .filter((value): value is string => typeof value === "string" && value.length > 0 && value.toLowerCase() !== "primary");
}

function HomeProviderCard({
  providerType,
  displayName,
  locationLabel,
  summary,
  logoUrl,
  coverUrl,
  demo,
  demoLabel,
  metaChips,
  statusChips,
  primaryAction,
  secondaryAction,
}: HomeProviderCardProps) {
  return (
    <article className={`public-directory-card home-provider-card home-provider-card--${providerType} ${demo ? "is-demo" : ""}`}>
      <ProviderCardMedia
        providerType={providerType}
        displayName={displayName}
        logoUrl={logoUrl}
        coverUrl={coverUrl}
        context="HOME_BANNER"
        className="home-provider-card__media"
        loading="eager"
      />
      <div className="home-provider-card__body">
        <div className="home-provider-card__heading">
          <div>
            <strong>{displayName}</strong>
            <span>{locationLabel}</span>
          </div>
        </div>
        <p className="home-provider-card__summary line-clamp-3">{summary}</p>
        {metaChips.length ? (
          <div className="home-provider-card__chip-row" aria-label={`${displayName} details`}>
            {metaChips.map((chip) => (
              <span key={chip.key} className={`chip chip--${chip.variant ?? "muted"}`}>
                {chip.label}
              </span>
            ))}
          </div>
        ) : null}
        {statusChips.length ? (
          <div className="home-provider-card__chip-row home-provider-card__chip-row--status" aria-label={`${displayName} status`}>
            {statusChips.map((chip) => (
              <span key={chip.key} className={`chip chip--${chip.variant ?? "muted"}`}>
                {chip.label}
              </span>
            ))}
            {demo ? <span className="chip chip--demo">{demoLabel}</span> : null}
          </div>
        ) : demo ? (
          <div className="home-provider-card__chip-row home-provider-card__chip-row--status">
            <span className="chip chip--demo">{demoLabel}</span>
          </div>
        ) : null}
        <div className="directory-action-row">
          {secondaryAction}
          {primaryAction}
        </div>
      </div>
    </article>
  );
}

export function formatConsultationFee(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const numericValue = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return null;
  }
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(numericValue);
}

export function formatDistanceKm(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const numericValue = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numericValue) || numericValue < 0) {
    return null;
  }
  const rounded = numericValue < 10 ? numericValue.toFixed(1) : numericValue.toFixed(0);
  return `${rounded} km away`;
}

function slugify(value: string) {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export function resolveSpecialityPath(item: string) {
  return DISCOVER_DETAIL_PATHS.speciality(slugify(item));
}

function appendBookingParams(baseUrl: string, context?: Record<string, string | undefined | null>) {
  const url = new URL(baseUrl, window.location.origin);
  url.pathname = "/patient/book-appointment";
  Object.entries(context ?? {}).forEach(([key, value]) => {
    if (value?.trim()) {
      url.searchParams.set(key, value.trim());
    }
  });
  return url.toString();
}

function dedupeDisplayParts(values: Array<string | null | undefined>) {
  const seen = new Set<string>();
  const parts: string[] = [];
  values.forEach((value) => {
    const normalized = value?.trim();
    if (!normalized) {
      return;
    }
    const key = normalized.toLowerCase();
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    parts.push(normalized);
  });
  return parts;
}

export function careBookingUrl(context?: Record<string, string | undefined | null>) {
  return appendBookingParams(discoverConfig.careAppUrl, context);
}

export function QueryToolbar({
  actionLabel,
  query,
  setQuery,
  city,
  setCity,
  area,
  setArea,
  onSubmit,
}: {
  actionLabel: string;
  query: string;
  setQuery: (value: string) => void;
  city: string;
  setCity: (value: string) => void;
  area: string;
  setArea: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="toolbar-card public-toolbar-card" onSubmit={onSubmit}>
      <label className="toolbar-field">
        <span>Keyword</span>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Doctor, speciality, clinic or area" />
      </label>
      <label className="toolbar-field">
        <span>City</span>
        <input value={city} onChange={(event) => setCity(event.target.value)} placeholder="Search by city" />
      </label>
      <label className="toolbar-field">
        <span>Area</span>
        <input value={area} onChange={(event) => setArea(event.target.value)} placeholder="Search by area" />
      </label>
      <button className="primary-button" type="submit">
        {actionLabel}
      </button>
    </form>
  );
}

export function DiscoverEmptyState({
  icon = "＋",
  title,
  description,
  primaryAction,
  primaryTo,
  primaryHref,
  secondaryAction,
  secondaryTo,
  secondaryHref,
  variant = "full",
}: {
  icon?: string;
  title: string;
  description: string;
  primaryAction?: string;
  primaryTo?: string;
  primaryHref?: string;
  secondaryAction?: string;
  secondaryTo?: string;
  secondaryHref?: string;
  variant?: "full" | "compact";
}) {
  const primary = primaryAction
    ? primaryTo
      ? <Link className="primary-button" to={primaryTo}>{primaryAction}</Link>
      : primaryHref
        ? <a className="primary-button" href={primaryHref}>{primaryAction}</a>
        : null
    : null;
  const secondary = secondaryAction
    ? secondaryTo
      ? <Link className="secondary-button" to={secondaryTo}>{secondaryAction}</Link>
      : secondaryHref
        ? <a className="secondary-button" href={secondaryHref}>{secondaryAction}</a>
        : null
    : null;

  return (
    <div className={`discover-empty-state ${variant === "compact" ? "is-compact" : ""}`}>
      <span className="empty-state-icon" aria-hidden="true">{icon}</span>
      <strong>{title}</strong>
      <p>{description}</p>
      {primary || secondary ? (
        <div className="cta-row">
          {primary}
          {secondary}
        </div>
      ) : null}
    </div>
  );
}

export function DirectoryState({
  loading,
  error,
  empty,
  emptyMessage,
  emptyTitle,
  emptyIcon,
  primaryAction,
  primaryTo,
  primaryHref,
  secondaryAction,
  secondaryTo,
  secondaryHref,
  errorTitle,
  children,
}: {
  loading: boolean;
  error: string | null;
  empty: boolean;
  emptyMessage: string;
  emptyTitle?: string;
  emptyIcon?: string;
  primaryAction?: string;
  primaryTo?: string;
  primaryHref?: string;
  secondaryAction?: string;
  secondaryTo?: string;
  secondaryHref?: string;
  errorTitle?: string;
  children: ReactNode;
}) {
  if (loading) {
    return (
      <div className="directory-result-area" role="status" aria-label="Loading discovery results">
        <div className="skeleton-grid">
          <span className="skeleton-card" />
          <span className="skeleton-card" />
          <span className="skeleton-card" />
        </div>
      </div>
    );
  }
  if (error) {
    return (
      <div className="directory-result-area">
        <DiscoverEmptyState
          icon="!"
          title={errorTitle ?? "We could not load this directory right now."}
          description="Please try again or continue browsing another section of Jeevanam Discover."
          primaryAction="Try again"
          primaryHref={typeof window !== "undefined" ? window.location.href : undefined}
          secondaryAction="Browse clinics"
          secondaryTo="/clinics"
        />
      </div>
    );
  }
  if (empty) {
    return (
      <div className="directory-result-area">
        <DiscoverEmptyState
          icon={emptyIcon}
          title={emptyTitle ?? "No matching results yet"}
          description={emptyMessage}
          primaryAction={primaryAction}
          primaryTo={primaryTo}
          primaryHref={primaryHref}
          secondaryAction={secondaryAction}
          secondaryTo={secondaryTo}
          secondaryHref={secondaryHref}
        />
      </div>
    );
  }
  return <>{children}</>;
}

export function InlineDirectoryState({
  loading,
  error,
  empty,
  emptyMessage,
  emptyTitle,
  emptyIcon,
  primaryAction,
  primaryTo,
  secondaryAction,
  secondaryTo,
}: {
  loading: boolean;
  error: string | null;
  empty: boolean;
  emptyMessage: string;
  emptyTitle?: string;
  emptyIcon?: string;
  primaryAction?: string;
  primaryTo?: string;
  secondaryAction?: string;
  secondaryTo?: string;
}) {
  if (loading) {
    return (
      <div className="skeleton-grid" role="status" aria-label="Loading discovery data">
        <span className="skeleton-card" />
        <span className="skeleton-card" />
        <span className="skeleton-card" />
      </div>
    );
  }
  if (error) {
    return (
      <DiscoverEmptyState
        icon="!"
        title="This section could not be loaded"
        description="Other discovery options remain available while you continue browsing."
        primaryAction="Browse clinics"
        primaryTo="/clinics"
        variant="compact"
      />
    );
  }
  if (empty) {
    return (
      <DiscoverEmptyState
        icon={emptyIcon}
        title={emptyTitle ?? "Nothing to show yet"}
        description={emptyMessage}
        primaryAction={primaryAction}
        primaryTo={primaryTo}
        secondaryAction={secondaryAction}
        secondaryTo={secondaryTo}
        variant="compact"
      />
    );
  }
  return null;
}

export function PaginationBar({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <div className="pagination-row" aria-label="Pagination">
      <button className="secondary-button" type="button" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
        Previous
      </button>
      <span className="pagination-label">
        Page {page + 1} of {totalPages}
      </span>
      <button className="secondary-button" type="button" onClick={() => onPageChange(page + 1)} disabled={page + 1 >= totalPages}>
        Next
      </button>
    </div>
  );
}

export function DoctorCard({
  doctor,
  demo = false,
  demoLabel = "Demo preview",
  context = "directory",
  hostProviderName = null,
}: {
  doctor: PublicDoctorSummaryResponse;
  context?: "directory" | "hospital";
  hostProviderName?: string | null;
} & DirectoryCardDemoProps) {
  const consultationFee = formatConsultationFee(doctor.consultationFee ?? null);
  const distance = formatDistanceKm(doctor.distanceKm ?? null);
  const subtitle = doctor.subtitle?.trim() || doctor.speciality || null;
  const locationSummary = context === "hospital"
    ? (hostProviderName?.trim() ? `Associated with ${hostProviderName.trim()}` : null)
    : dedupeDisplayParts(patientFacingLocationParts(doctor.clinicDisplayName, doctor.area, doctor.city)).join(" · ");
  const availabilityText = doctor.availableToday ? "Available today" : doctor.nextAvailableSlotSummary || "Check next slot";
  const availabilityClass = doctor.availableToday ? "chip chip--success" : "chip chip--info";
  const bookingMode = normalizeBookingMode(doctor.bookingMode) ?? "ONLINE_BOOKING";
  const bookingHref = doctor.contactPhone?.trim() ? `tel:${doctor.contactPhone.trim()}` : null;
  const primaryActionLabel = providerBookingPrimaryLabel(bookingMode);
  const secondaryActionLabel = providerBookingSecondaryLabel(bookingMode) ?? "View profile";

  return (
    <article className={`public-directory-card doctor-directory-card ${demo ? "is-demo" : ""}`}>
      <div className="directory-card-top">
        <div className="directory-avatar" aria-hidden="true">
          {doctor.photoUrl ? <img src={doctor.photoUrl} alt="" loading="lazy" /> : <HealthcareAvatarFallback />}
        </div>
        <div className="directory-card-heading">
          <strong>{doctor.doctorDisplayName}</strong>
          {subtitle ? <span>{subtitle}</span> : null}
          {doctor.speciality ? <p>{doctor.speciality}</p> : null}
          {locationSummary ? <span>{locationSummary}</span> : null}
        </div>
      </div>
      <div className="directory-meta-list">
        {doctor.yearsOfExperience != null ? (
          <span>
            <ScheduleOutlined fontSize="small" aria-hidden="true" />
            {doctor.yearsOfExperience}+ years experience
          </span>
        ) : null}
        {consultationFee ? (
          <span>
            <CurrencyRupeeOutlined fontSize="small" aria-hidden="true" />
            Consultation fee: {consultationFee}
          </span>
        ) : null}
        {doctor.languages.length ? (
          <span>
            <LanguageOutlined fontSize="small" aria-hidden="true" />
            {doctor.languages.join(", ")}
          </span>
        ) : null}
        {context === "directory" && locationSummary ? (
          <span>
            <LocationOnOutlined fontSize="small" aria-hidden="true" />
            {locationSummary}
          </span>
        ) : null}
      </div>
      <div className="doctor-directory-rating-row" aria-label="Doctor rating">
        <span className="doctor-directory-rating-row__score">
          <StarRounded fontSize="small" aria-hidden="true" />
          <strong>4.8</strong>
        </span>
        <span>(245 Reviews)</span>
      </div>
      <div className="directory-badge-row">
        <BookingCapabilityBadge mode={bookingMode} compact />
        {demo ? <span className="chip chip--demo">{demoLabel}</span> : null}
        <span className={availabilityClass}>{availabilityText}</span>
        {distance ? <span className="chip chip--muted">{distance}</span> : null}
      </div>
      <div className="directory-action-row">
        {demo ? (
          <button className="secondary-button" type="button" disabled aria-disabled="true">
            Demo profile
          </button>
        ) : (
          <Link className="secondary-button" to={doctor.publicPath ?? DISCOVER_DETAIL_PATHS.doctor(doctor.doctorSlug)}>
            {secondaryActionLabel}
          </Link>
        )}
        {demo ? (
          <button className="primary-button" type="button" disabled aria-disabled="true">
            Demo booking
          </button>
        ) : bookingMode === "CALL_TO_BOOK" && bookingHref ? (
          <a className="primary-button" href={bookingHref}>
            {primaryActionLabel}
          </a>
        ) : bookingMode === "NOT_AVAILABLE" ? (
          <Link className="primary-button" to={doctor.publicPath ?? DISCOVER_DETAIL_PATHS.doctor(doctor.doctorSlug)}>
            {primaryActionLabel}
          </Link>
        ) : (
          <a
            className="primary-button"
            href={careBookingUrl({
              doctorId: doctor.publicDoctorId,
              clinicSlug: doctor.clinicSlug,
            })}
          >
            {primaryActionLabel}
          </a>
        )}
      </div>
    </article>
  );
}

export function ClinicCard({ clinic, demo = false, demoLabel = "Demo preview" }: { clinic: PublicClinicSummaryResponse } & DirectoryCardDemoProps) {
  const locationLabel = clinic.area ?? clinic.city ?? "Clinic";
  const summary = clinic.summary?.trim() || clinic.subtitle?.trim() || "Clinic profile published for Discover";
  const primarySpeciality = clinic.specialities[0] ?? null;
  const bookingMode = normalizeBookingMode(clinic.bookingMode) ?? "ONLINE_BOOKING";
  const callHref = clinic.contactPhone?.trim() ? `tel:${clinic.contactPhone.trim()}` : null;
  const metaChips = [
    primarySpeciality ? { key: "speciality", label: primarySpeciality, variant: "muted" as const } : null,
    clinic.serviceCount ? { key: "services", label: `${clinic.serviceCount} services`, variant: "info" as const } : null,
  ].filter(Boolean) as HomeProviderCardChip[];
  const statusChips = [
    { key: "booking", label: bookingCapabilityLabel(bookingMode), variant: bookingMode === "ONLINE_BOOKING" ? "success" as const : bookingMode === "CALL_TO_BOOK" ? "info" as const : "muted" as const },
    { key: "availability", label: clinic.availableToday ? "Available today" : "Appointment entry available", variant: clinic.availableToday ? "success" as const : "muted" as const },
  ].filter(Boolean) as HomeProviderCardChip[];

  return (
    <HomeProviderCard
      providerType="clinic"
      displayName={clinic.clinicDisplayName}
      locationLabel={locationLabel}
      summary={summary}
      logoUrl={clinic.logoUrl ?? null}
      coverUrl={clinic.coverUrl ?? null}
      demo={demo}
      demoLabel={demoLabel}
      metaChips={metaChips}
      statusChips={statusChips}
      secondaryAction={
        demo ? <button className="secondary-button" type="button" disabled aria-disabled="true">Demo clinic</button> : <Link className="secondary-button" to={clinic.publicPath ?? DISCOVER_DETAIL_PATHS.clinic(clinic.clinicSlug)}>View clinic</Link>
      }
      primaryAction={
        demo ? <button className="primary-button" type="button" disabled aria-disabled="true">Demo booking</button> : bookingMode === "CALL_TO_BOOK" && callHref ? <a className="primary-button" href={callHref}>Call clinic</a> : bookingMode === "NOT_AVAILABLE" ? <Link className="primary-button" to={clinic.publicPath ?? DISCOVER_DETAIL_PATHS.clinic(clinic.clinicSlug)}>View profile</Link> : <a className="primary-button" href={careBookingUrl({ clinicSlug: clinic.clinicSlug })}>Book appointment</a>
      }
    />
  );
}

export function HospitalCard({ hospital, demo = false, demoLabel = "Demo preview" }: { hospital: PublicHospitalSummaryResponse } & DirectoryCardDemoProps) {
  const summary = hospital.summary?.trim() || "Hospital profile published for Discover";
  const callHref = hospital.contactPhone?.trim() ? `tel:${hospital.contactPhone.trim()}` : null;
  const contactLabel = callHref ? "Call Hospital" : "View hospital";
  const metaChips = hospital.departments.slice(0, 3).map((item, index) => ({
    key: `department-${index}`,
    label: item,
    variant: "muted" as const,
  }));
  const statusChips = [
    { key: "booking", label: contactLabel, variant: callHref ? "info" as const : "muted" as const },
    { key: "availability", label: hospital.emergencyAvailable ? "Emergency available" : "Review services", variant: hospital.emergencyAvailable ? "success" as const : "muted" as const },
    hospital.coverUrl ? { key: "cover", label: "Cover image", variant: "info" as const } : null,
  ].filter(Boolean) as HomeProviderCardChip[];

  return (
    <HomeProviderCard
      providerType="hospital"
      displayName={hospital.hospitalDisplayName}
      locationLabel={hospital.area ?? hospital.city ?? "Hospital"}
      summary={summary}
      logoUrl={hospital.logoUrl ?? null}
      coverUrl={hospital.coverUrl ?? null}
      demo={demo}
      demoLabel={demoLabel}
      metaChips={metaChips}
      statusChips={statusChips}
      secondaryAction={
        demo ? <button className="secondary-button" type="button" disabled aria-disabled="true">Demo hospital</button> : <Link className="secondary-button" to={hospital.publicPath ?? DISCOVER_DETAIL_PATHS.hospital(hospital.hospitalSlug)}>View hospital</Link>
      }
      primaryAction={
        demo ? <button className="primary-button" type="button" disabled aria-disabled="true">Demo booking</button> : callHref ? <a className="primary-button" href={callHref}>Call Hospital</a> : <Link className="primary-button" to={hospital.publicPath ?? DISCOVER_DETAIL_PATHS.hospital(hospital.hospitalSlug)}>View hospital</Link>
      }
    />
  );
}

export function AivaDiscoveryAssistantCard() {
  return (
    <aside className="discover-value-panel home-aiva-panel" aria-label="AIVA Discovery Assistant">
      <div className="home-aiva-card">
        <div className="home-aiva-header">
          <span className="home-aiva-icon" aria-hidden="true">
            <AutoAwesomeOutlined fontSize="small" />
          </span>
          <div className="discover-value-copy">
            <span className="eyebrow">AIVA</span>
            <h2>Your AI care guide</h2>
            <p>Not sure which doctor to visit?</p>
            <p>Describe your symptoms in natural language.</p>
          </div>
        </div>

        <div className="home-aiva-summary">
          <strong>AIVA will soon help you:</strong>
          <ul className="home-aiva-benefits" aria-label="What AIVA will help with">
            <li>
              <span aria-hidden="true">✓</span>
              <span>Suggest the right speciality</span>
            </li>
            <li>
              <span aria-hidden="true">✓</span>
              <span>Compare nearby doctors</span>
            </li>
            <li>
              <span aria-hidden="true">✓</span>
              <span>Recommend clinics</span>
            </li>
            <li>
              <span aria-hidden="true">✓</span>
              <span>Explain medical terms</span>
            </li>
            <li>
              <span aria-hidden="true">✓</span>
              <span>Help prepare for appointments</span>
            </li>
          </ul>
        </div>

        <span className="chip chip--demo home-aiva-coming-soon">Coming Soon</span>
        <p className="home-aiva-note">AIVA assists healthcare discovery. It does not replace medical advice.</p>
      </div>
    </aside>
  );
}
