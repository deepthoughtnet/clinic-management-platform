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
  "No public profiles are enabled yet. Healthcare teams can publish public-safe profiles after verification.";

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
        <span>Search</span>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Doctor name, speciality, clinic, or area" />
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

export function DirectoryState({
  loading,
  error,
  empty,
  emptyMessage,
  children,
}: {
  loading: boolean;
  error: string | null;
  empty: boolean;
  emptyMessage: string;
  children: ReactNode;
}) {
  if (loading) {
    return <div className="state-card" role="status">Loading public discovery results...</div>;
  }
  if (error) {
    return <div className="state-card">Unable to load public discovery data: {error}</div>;
  }
  if (empty) {
    return <div className="state-card">{emptyMessage}</div>;
  }
  return <>{children}</>;
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
          {doctor.photoUrl ? <img src={doctor.photoUrl} alt="" /> : <span>{initials(doctor.doctorDisplayName)}</span>}
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
        {consultationFee ? <span>Fee: {consultationFee}</span> : null}
        {doctor.languages.length ? <span>Languages: {doctor.languages.join(", ")}</span> : null}
      </div>
      <div className="directory-badge-row">
        {doctor.availableToday ? <span className="status-pill">Available today</span> : <span className="chip">Check next slot</span>}
        <span className="chip">{doctor.nextAvailableSlotSummary ?? "Clinic shares next slot after review"}</span>
      </div>
      <div className="directory-action-row">
        <Link className="secondary-button" to={`/doctors/${doctor.doctorSlug}`}>
          View profile
        </Link>
        <a
          className="text-button"
          href={careBookingUrl({
            doctorId: doctor.publicDoctorId,
            clinicSlug: doctor.clinicSlug,
          })}
        >
          Start booking
        </a>
      </div>
    </article>
  );
}

export function ClinicCard({ clinic }: { clinic: PublicClinicSummaryResponse }) {
  return (
    <article className="public-directory-card clinic-directory-card">
      <div className="directory-card-top">
        <div className="directory-avatar" aria-hidden="true">
          {clinic.logoUrl ? <img src={clinic.logoUrl} alt="" /> : <span>{initials(clinic.clinicDisplayName)}</span>}
        </div>
        <div className="directory-card-heading">
          <strong>{clinic.clinicDisplayName}</strong>
          <span>{clinic.area ?? clinic.city ?? "Clinic profile"}</span>
        </div>
      </div>
      <div className="directory-meta-list">
        <span>{clinic.address ?? "Address shared after clinic onboarding"}</span>
        <span>{clinic.doctorsCount} doctor{clinic.doctorsCount === 1 ? "" : "s"}</span>
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
        <a className="text-button" href={careBookingUrl({ clinicSlug: clinic.clinicSlug })}>
          Start booking
        </a>
      </div>
    </article>
  );
}
