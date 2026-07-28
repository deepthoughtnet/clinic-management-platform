import type { FormEvent, ReactNode } from "react";
import { Link } from "react-router-dom";
import type {
  PublicClinicSummaryResponse,
  PublicDoctorSummaryResponse,
  PublicPageResponse,
} from "../api/publicCatalog";
import { discoverConfig } from "../config";
import { slugify } from "../utils/publicDiscovery";

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

export const noPublicProfilesMessage =
  "Published provider profiles will appear here as the directory grows.";

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

export function DoctorCard({ doctor }: { doctor: PublicDoctorSummaryResponse }) {
  const consultationFee = formatConsultationFee(doctor.consultationFee ?? null);
  return (
    <article className="public-directory-card doctor-directory-card">
      <div className="directory-card-top">
        <div className="directory-avatar" aria-hidden="true">
          {doctor.photoUrl ? <img src={doctor.photoUrl} alt="" loading="lazy" /> : <span>{initials(doctor.doctorDisplayName)}</span>}
        </div>
        <div className="directory-card-heading">
          <strong>{doctor.doctorDisplayName}</strong>
          <span>{doctor.speciality ?? "General consultation"}</span>
        </div>
      </div>
      <div className="directory-meta-list">
        <span>{formatExperience(doctor.yearsOfExperience)}</span>
        <span>
          {doctor.clinicDisplayName}
          {doctor.area ? ` · ${doctor.area}` : ""}
          {doctor.city ? ` · ${doctor.city}` : ""}
        </span>
        {consultationFee ? <span>Consultation fee: {consultationFee}</span> : <span>Fee available on profile</span>}
        {doctor.languages.length ? <span>Languages: {doctor.languages.join(", ")}</span> : null}
      </div>
      <div className="directory-badge-row">
        {doctor.availableToday ? <span className="status-pill">Available today</span> : <span className="chip">Check next slot</span>}
        {doctor.nextAvailableSlotSummary ? <span className="chip">{doctor.nextAvailableSlotSummary}</span> : null}
      </div>
      <div className="directory-action-row">
        <Link className="secondary-button" to={`/doctors/${doctor.doctorSlug}`}>
          View profile
        </Link>
        <a
          className="primary-button"
          href={careBookingUrl({
            doctorId: doctor.publicDoctorId,
            clinicSlug: doctor.clinicSlug,
          })}
        >
          Book appointment
        </a>
      </div>
    </article>
  );
}

export function ClinicCard({ clinic }: { clinic: PublicClinicSummaryResponse }) {
  return (
    <article className="public-directory-card clinic-directory-card">
      <div className="clinic-card-media">
        <div className="directory-avatar clinic-avatar" aria-hidden="true">
          {clinic.logoUrl ? <img src={clinic.logoUrl} alt="" loading="lazy" /> : <span>{initials(clinic.clinicDisplayName)}</span>}
        </div>
      </div>
      <div className="directory-card-top">
        <div className="directory-card-heading">
          <strong>{clinic.clinicDisplayName}</strong>
          <span>{clinic.area ?? clinic.city ?? "Clinic"}</span>
        </div>
      </div>
      <div className="directory-meta-list">
        <span>{clinic.address ?? "Address shared after clinic onboarding"}</span>
        {clinic.doctorsCount > 0 ? <span>{clinic.doctorsCount} doctor{clinic.doctorsCount === 1 ? "" : "s"}</span> : null}
      </div>
      <div className="directory-badge-row">
        {clinic.availableToday ? <span className="status-pill">Available today</span> : <span className="chip">Appointment entry available</span>}
        {clinic.specialities.slice(0, 2).map((item) => (
          <Link key={item} className="chip" to={`/specialities/${slugify(item)}`}>
            {item}
          </Link>
        ))}
      </div>
      <div className="directory-action-row">
        <Link className="secondary-button" to={`/clinics/${clinic.clinicSlug}`}>
          View clinic
        </Link>
        <a className="primary-button" href={careBookingUrl({ clinicSlug: clinic.clinicSlug })}>
          Book appointment
        </a>
      </div>
    </article>
  );
}
