import { type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import {
  CheckCircleOutlined,
  CloseRounded,
  CurrencyRupeeOutlined,
  AutoAwesomeOutlined,
  DirectionsRunOutlined,
  FilterAltOutlined,
  LocalHospitalOutlined,
  LocationOnOutlined,
  MediationOutlined,
  MenuBookOutlined,
  KeyboardArrowDownRounded,
  ScheduleOutlined,
  SearchOutlined,
} from "@mui/icons-material";
import { Link } from "react-router-dom";
import type {
  PublicClinicSummaryResponse,
  PublicDoctorSummaryResponse,
  PublicHospitalSummaryResponse,
  PublicSpecialitySummaryResponse,
} from "../../api/publicCatalog";
import { PublicMediaImage } from "../landing/PublicMediaImage";
import { DISCOVER_DETAIL_PATHS } from "../../routes";
import { careBookingUrl, formatConsultationFee, formatDistanceKm, initials } from "../DiscoveryComponents";
import { PUBLIC_DEFAULT_LOCATION, PUBLIC_LOCATION_OPTIONS, normalizePublicLocation, type PublicLocationCoordinates, usePublicLocation } from "../../context/PublicLocationContext";
import { DISCOVER_DIRECTORY_TOKENS } from "../../theme/discoverTheme";
import type { FormEvent, KeyboardEvent } from "react";

type DirectoryCardCommonProps = {
  demo?: boolean;
  demoLabel?: string;
};

type DirectoryFiltersDrawerProps = {
  open: boolean;
  title: string;
  selectedCount: number;
  onClose: () => void;
  onApply: () => void;
  onClear: () => void;
  children: ReactNode;
};

export function safePublicImageSrc(src: string | null | undefined) {
  const trimmed = src?.trim();
  if (!trimmed) {
    return null;
  }
  try {
    const resolved = typeof window === "undefined" ? new URL(trimmed, "https://example.invalid") : new URL(trimmed, window.location.origin);
    return resolved.pathname.startsWith("/api/public/") ? resolved.toString() : null;
  } catch {
    return null;
  }
}

function DirectoryFallbackAvatar({ label, tone }: { label: string; tone: "doctor" | "clinic" | "hospital" | "speciality" }) {
  return (
    <div className={`directory-fallback-avatar directory-fallback-avatar--${tone}`} aria-hidden="true">
      <span>{label}</span>
    </div>
  );
}

export function DirectoryPageShell({ className, children }: { className?: string; children: ReactNode }) {
  return <section className={`page-section directory-page-shell ${className ?? ""}`.trim()}>{children}</section>;
}

export function DirectoryHero({
  eyebrow,
  title,
  body,
  accent,
  accessory,
}: {
  eyebrow: string;
  title: string;
  body: string;
  accent?: "teal" | "blue-teal" | "blue" | "violet";
  accessory?: ReactNode;
}) {
  return (
    <div className={`directory-hero directory-hero--${accent ?? "teal"}`}>
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{body}</p>
      </div>
      {accessory ? <div className="directory-hero-accessory">{accessory}</div> : null}
    </div>
  );
}

export function DirectorySortMenu({
  value,
  options,
  onChange,
}: {
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const selectedOption = options.find((option) => option.value === value) ?? options[0];

  useEffect(() => {
    function handleDocumentPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    function handleDocumentKeyDown(event: Event) {
      if ((event as globalThis.KeyboardEvent).key === "Escape") {
        setOpen(false);
      }
    }

    document.addEventListener("pointerdown", handleDocumentPointerDown);
    document.addEventListener("keydown", handleDocumentKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handleDocumentPointerDown);
      document.removeEventListener("keydown", handleDocumentKeyDown);
    };
  }, []);

  return (
    <div className="directory-sort-menu" ref={containerRef}>
      <button
        className="secondary-button directory-toolbar-control directory-sort-trigger"
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="directory-sort-trigger__label">Sort</span>
        <span className="directory-sort-trigger__value">{selectedOption?.label ?? "Relevance"}</span>
        <KeyboardArrowDownRounded fontSize="small" aria-hidden="true" />
      </button>
      {open ? (
        <div className="directory-sort-menu__panel" role="menu" aria-label="Sort results">
          {options.map((option) => {
            const isActive = option.value === value;
            return (
              <button
                key={option.value}
                className={`directory-sort-menu__item${isActive ? " is-active" : ""}`}
                type="button"
                role="menuitemradio"
                aria-checked={isActive}
                onClick={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
              >
                <span>{option.label}</span>
                {isActive ? <CheckCircleOutlined fontSize="small" aria-hidden="true" /> : null}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

export function DirectorySearchPanel({
  query,
  onQueryChange,
  placeholder,
  searchButtonLabel,
  onSubmit,
  locationLabel,
  onLocationCommit,
  onUseCurrentLocation,
  selectedCoordinates,
  radiusKm,
  onRadiusChange,
  note,
}: {
  query: string;
  onQueryChange: (value: string) => void;
  placeholder: string;
  searchButtonLabel: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  locationLabel: string;
  onLocationCommit: (nextLocation: string, nextCoordinates?: PublicLocationCoordinates | null) => void;
  onUseCurrentLocation: () => void;
  selectedCoordinates: PublicLocationCoordinates | null;
  radiusKm: string;
  onRadiusChange: (value: string) => void;
  note?: string;
}) {
  const { locationState } = usePublicLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [draftLocation, setDraftLocation] = useState(locationLabel);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (menuOpen) {
      setDraftLocation(locationLabel);
      setMessage(null);
    }
  }, [locationLabel, menuOpen]);

  function commit(nextLocation: string, nextCoordinates: PublicLocationCoordinates | null = null) {
    const normalized = normalizePublicLocation(nextLocation) || PUBLIC_DEFAULT_LOCATION;
    onLocationCommit(normalized, nextCoordinates);
    setMenuOpen(false);
    setMessage(null);
    setDraftLocation(normalized);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      setMenuOpen(false);
    }
  }

  return (
    <form className="directory-search-panel" aria-label="Smart search" onSubmit={onSubmit}>
      <label className="directory-search-field directory-search-field--query">
        <span className="visually-hidden">What are you looking for?</span>
        <SearchOutlined fontSize="small" aria-hidden="true" />
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder={placeholder}
          aria-label="What are you looking for?"
          autoComplete="off"
        />
      </label>
      <div className="directory-search-location-shell" onKeyDown={handleKeyDown}>
        <button
          className="location-select-button directory-search-location-button"
          type="button"
          aria-haspopup="dialog"
          aria-expanded={menuOpen}
          aria-label={`Active location, currently ${locationLabel}`}
          onClick={() => setMenuOpen((current) => !current)}
        >
          <LocationOnOutlined fontSize="small" aria-hidden="true" />
          <span>{locationLabel}</span>
        </button>
        {menuOpen ? (
          <div className="directory-location-popover" role="dialog" aria-label="Select location">
            <label className="directory-search-field">
              <span className="directory-field-label">Active location</span>
              <input value={draftLocation} onChange={(event) => setDraftLocation(normalizePublicLocation(event.target.value))} placeholder="Pune" />
            </label>
            <div className="chip-row" role="list" aria-label="Popular locations">
              {PUBLIC_LOCATION_OPTIONS.map((location) => (
                <button key={location} className="chip-button" type="button" onClick={() => commit(location)}>
                  {location}
                </button>
              ))}
            </div>
            <label className="directory-search-field">
              <span className="directory-field-label">Nearby radius</span>
              <select value={radiusKm} onChange={(event) => onRadiusChange(event.target.value)} disabled={!selectedCoordinates}>
                <option value="2">2 km</option>
                <option value="5">5 km</option>
                <option value="10">10 km</option>
                <option value="25">25 km</option>
                <option value="50">50 km</option>
              </select>
            </label>
            <div className="cta-row">
              <button className="secondary-button" type="button" onClick={() => commit(draftLocation)} disabled={!normalizePublicLocation(draftLocation)}>
                Save location
              </button>
              <button className="text-button" type="button" onClick={onUseCurrentLocation}>
                Use my location
              </button>
            </div>
            <p className="form-note">
              {selectedCoordinates ? `Radius applies within ${radiusKm} km of your selected location.` : "Radius becomes active after using your current location."}
            </p>
            {message ? <p className="form-note" role="status">{message}</p> : null}
          </div>
        ) : null}
      </div>
      <button className="primary-button directory-search-button" type="submit">
        {searchButtonLabel}
      </button>
      {note ? <p className="directory-search-note">{note}</p> : null}
      {locationState.source === "browser" && selectedCoordinates ? (
        <p className="directory-search-note">Location permission remains optional. Your current location is applied only after you choose it.</p>
      ) : null}
    </form>
  );
}

export function DirectoryResultsToolbar({
  resultLabel,
  locationLabel,
  filterSummary,
  selectedFilterCount,
  sortValue,
  sortOptions,
  onSortChange,
  onOpenFilters,
  onClear,
}: {
  resultLabel: string;
  locationLabel: string;
  filterSummary: string | null;
  selectedFilterCount: number;
  sortValue: string;
  sortOptions: Array<{ value: string; label: string }>;
  onSortChange: (value: string) => void;
  onOpenFilters: () => void;
  onClear: () => void;
}) {
  return (
    <div className="directory-results-toolbar">
      <div className="directory-results-toolbar__summary">
        <strong>{resultLabel}</strong>
        <p>
          {locationLabel}
          {filterSummary ? ` · ${filterSummary}` : ""}
        </p>
      </div>
      <div className="directory-results-toolbar__actions">
        <button className="secondary-button directory-filters-button" type="button" onClick={onOpenFilters}>
          <FilterAltOutlined fontSize="small" aria-hidden="true" />
          Filters
          {selectedFilterCount > 0 ? <span className="directory-filter-count">{selectedFilterCount}</span> : null}
        </button>
        <DirectorySortMenu value={sortValue} options={sortOptions} onChange={onSortChange} />
        <button className="text-button" type="button" onClick={onClear} disabled={selectedFilterCount === 0 && sortValue === "relevance"}>
          Clear filters
        </button>
      </div>
    </div>
  );
}

export function DirectoryFiltersDrawer({ open, title, selectedCount, onClose, onApply, onClear, children }: DirectoryFiltersDrawerProps) {
  if (!open) {
    return null;
  }
  return (
    <div className="directory-drawer-backdrop" role="presentation" onClick={onClose}>
      <aside className="directory-drawer" role="dialog" aria-modal="true" aria-label={title} onClick={(event) => event.stopPropagation()}>
        <div className="directory-drawer__header">
          <div>
            <span className="eyebrow">{title}</span>
            <strong>{selectedCount > 0 ? `${selectedCount} selected` : "No active filters"}</strong>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Close filters">
            <CloseRounded fontSize="small" aria-hidden="true" />
          </button>
        </div>
        <div className="directory-drawer__content">{children}</div>
        <div className="directory-drawer__footer">
          <button className="secondary-button" type="button" onClick={onClear}>
            Clear
          </button>
          <button className="primary-button" type="button" onClick={onApply}>
            Apply filters
          </button>
        </div>
      </aside>
    </div>
  );
}

export function DirectoryFilterSection({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="directory-filter-section">
      <strong>{title}</strong>
      {children}
    </section>
  );
}

export function DirectoryFilterChips({
  label,
  values,
  active,
  onToggle,
  onClear,
}: {
  label: string;
  values: string[];
  active: Set<string>;
  onToggle: (value: string) => void;
  onClear?: () => void;
}) {
  return (
    <DirectoryFilterSection title={label}>
      <div className="chip-row" role="list" aria-label={label}>
        {values.map((value) => {
          const isActive = active.has(value);
          return (
            <button
              key={value}
              className={`chip-button${isActive ? " is-active" : ""}`}
              type="button"
              aria-pressed={isActive}
              onClick={() => onToggle(value)}
            >
              {value}
            </button>
          );
        })}
      </div>
      {onClear && active.size ? (
        <button className="text-button directory-filter-clear" type="button" onClick={onClear}>
          Clear {label.toLowerCase()}
        </button>
      ) : null}
    </DirectoryFilterSection>
  );
}

export function DirectoryToggleFilters({
  label,
  items,
  active,
  onToggle,
}: {
  label: string;
  items: Array<{ value: string; label: string; disabled?: boolean }>;
  active: Set<string>;
  onToggle: (value: string) => void;
}) {
  return (
    <DirectoryFilterSection title={label}>
      <div className="directory-toggle-list">
        {items.map((item) => {
          const isActive = active.has(item.value);
          return (
            <button
              key={item.value}
              type="button"
              className={`directory-toggle-item${isActive ? " is-active" : ""}`}
              aria-pressed={isActive}
              disabled={item.disabled}
              onClick={() => onToggle(item.value)}
            >
              <span>{item.label}</span>
              {isActive ? <CheckCircleOutlined fontSize="small" aria-hidden="true" /> : null}
            </button>
          );
        })}
      </div>
    </DirectoryFilterSection>
  );
}

export function DirectoryPageStickyPanel({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <aside className="directory-sticky-panel" aria-label={title}>
      {children}
    </aside>
  );
}

function safeDoctorImage(doctor: PublicDoctorSummaryResponse) {
  return safePublicImageSrc(doctor.photoUrl);
}

function safeClinicImage(clinic: PublicClinicSummaryResponse) {
  return safePublicImageSrc(clinic.coverUrl ?? clinic.logoUrl);
}

function safeHospitalImage(hospital: PublicHospitalSummaryResponse) {
  return safePublicImageSrc(hospital.coverUrl ?? hospital.logoUrl);
}

function providerBadgeText(provider: string) {
  return initials(provider) || provider.slice(0, 2).toUpperCase();
}

export function DoctorDirectoryCard({
  doctor,
  demo = false,
  demoLabel = "Demo preview",
}: {
  doctor: PublicDoctorSummaryResponse;
} & DirectoryCardCommonProps) {
  const consultationFee = formatConsultationFee(doctor.consultationFee ?? null);
  const distance = formatDistanceKm(doctor.distanceKm ?? null);
  const location = [doctor.area, doctor.city].filter(Boolean).join(" · ") || doctor.clinicDisplayName;
  const photoUrl = safeDoctorImage(doctor);
  const consultationModeText = doctor.nextAvailableSlotSummary || (doctor.availableToday ? "Available today" : "Next slot on request");

  return (
    <article className={`directory-card directory-card--doctor ${demo ? "is-demo" : ""}`}>
      <div className="directory-card__media">
        <div className="directory-card__avatar">
          {photoUrl ? (
            <PublicMediaImage src={photoUrl} alt={`${doctor.doctorDisplayName} photo`} className="directory-card__image" objectFit="cover" fallback={<DirectoryFallbackAvatar label={providerBadgeText(doctor.doctorDisplayName)} tone="doctor" />} />
          ) : (
            <DirectoryFallbackAvatar label={providerBadgeText(doctor.doctorDisplayName)} tone="doctor" />
          )}
        </div>
      </div>
      <div className="directory-card__body">
        <div className="directory-card__heading">
          <div>
            <strong>{doctor.doctorDisplayName}</strong>
            <span>{doctor.subtitle?.trim() || doctor.speciality || "Doctor profile"}</span>
          </div>
        </div>
        <div className="directory-card__meta">
          {doctor.speciality ? (
            <span>
              <MediationOutlined fontSize="small" aria-hidden="true" />
              {doctor.speciality}
            </span>
          ) : null}
          {doctor.yearsOfExperience != null ? (
            <span>
              <ScheduleOutlined fontSize="small" aria-hidden="true" />
              {doctor.yearsOfExperience}+ years experience
            </span>
          ) : null}
          {doctor.languages.length ? (
            <span>
              <MediationOutlined fontSize="small" aria-hidden="true" />
              {doctor.languages.join(" · ")}
            </span>
          ) : null}
          {consultationFee ? (
            <span>
              <CurrencyRupeeOutlined fontSize="small" aria-hidden="true" />
              {consultationFee}
            </span>
          ) : null}
          <span>
            <LocationOnOutlined fontSize="small" aria-hidden="true" />
            {location}
          </span>
          {distance ? (
            <span>
              <DirectionsRunOutlined fontSize="small" aria-hidden="true" />
              {distance}
            </span>
          ) : null}
          <span>
            <CheckCircleOutlined fontSize="small" aria-hidden="true" />
            {consultationModeText}
          </span>
        </div>
        {demo ? <span className="chip chip--demo">{demoLabel}</span> : null}
        <div className="directory-card__actions">
          <Link className="secondary-button" to={doctor.publicPath ?? DISCOVER_DETAIL_PATHS.doctor(doctor.doctorSlug)}>
            View profile
          </Link>
          <a className="primary-button" href={careBookingUrl({ doctorId: doctor.publicDoctorId, clinicSlug: doctor.clinicSlug })}>
            Book appointment
          </a>
        </div>
      </div>
    </article>
  );
}

export function ClinicDirectoryCard({
  clinic,
  demo = false,
  demoLabel = "Demo preview",
}: {
  clinic: PublicClinicSummaryResponse;
} & DirectoryCardCommonProps) {
  const distance = formatDistanceKm(clinic.distanceKm ?? null);
  const mediaUrl = safeClinicImage(clinic);
  const tagLine = clinic.subtitle?.trim() || clinic.summary?.trim() || null;
  const departments = clinic.serviceCount || clinic.departmentCount ? `${clinic.serviceCount ?? 0} services · ${clinic.departmentCount ?? 0} departments` : null;
  const specialities = clinic.specialities.slice(0, 3);

  return (
    <article className={`directory-card directory-card--clinic ${demo ? "is-demo" : ""}`}>
      <div className="directory-card__media directory-card__media--wide">
        {mediaUrl ? (
          <PublicMediaImage src={mediaUrl} alt={`${clinic.clinicDisplayName} clinic image`} className="directory-card__hero-image" objectFit="cover" fallback={<DirectoryFallbackAvatar label={providerBadgeText(clinic.clinicDisplayName)} tone="clinic" />} />
        ) : (
          <DirectoryFallbackAvatar label={providerBadgeText(clinic.clinicDisplayName)} tone="clinic" />
        )}
      </div>
      <div className="directory-card__body">
        <div className="directory-card__heading">
          <div>
            <strong>{clinic.clinicDisplayName}</strong>
            <span>{[clinic.area, clinic.city].filter(Boolean).join(" · ") || "Clinic"}</span>
          </div>
          {clinic.availableToday ? <span className="chip chip--success">Available today</span> : null}
        </div>
        {tagLine ? <p className="directory-card__summary">{tagLine}</p> : null}
        <div className="directory-card__meta">
          {clinic.address ? (
            <span>
              <LocationOnOutlined fontSize="small" aria-hidden="true" />
              {clinic.address}
            </span>
          ) : null}
          {clinic.doctorsCount > 0 ? (
            <span>
              <CheckCircleOutlined fontSize="small" aria-hidden="true" />
              {clinic.doctorsCount} doctor{clinic.doctorsCount === 1 ? "" : "s"}
            </span>
          ) : null}
          {departments ? (
            <span>
              <LocalHospitalOutlined fontSize="small" aria-hidden="true" />
              {departments}
            </span>
          ) : null}
          {distance ? (
            <span>
              <DirectionsRunOutlined fontSize="small" aria-hidden="true" />
              {distance}
            </span>
          ) : null}
        </div>
        <div className="directory-card__chips">
          {specialities.length ? specialities.map((item) => <span key={item} className="chip chip--muted">{item}</span>) : <span className="chip chip--muted">Services published after onboarding</span>}
          {demo ? <span className="chip chip--demo">{demoLabel}</span> : null}
        </div>
        <div className="directory-card__actions">
          <Link className="secondary-button" to={clinic.publicPath ?? DISCOVER_DETAIL_PATHS.clinic(clinic.clinicSlug)}>
            View clinic
          </Link>
          <Link className="secondary-button" to={clinic.publicPath ?? DISCOVER_DETAIL_PATHS.clinic(clinic.clinicSlug)}>
            View doctors
          </Link>
          <a className="primary-button" href={careBookingUrl({ clinicSlug: clinic.clinicSlug })}>
            Book appointment
          </a>
        </div>
      </div>
    </article>
  );
}

export function HospitalDirectoryCard({
  hospital,
  demo = false,
  demoLabel = "Demo preview",
}: {
  hospital: PublicHospitalSummaryResponse;
} & DirectoryCardCommonProps) {
  const distance = formatDistanceKm(hospital.distanceKm ?? null);
  const mediaUrl = safeHospitalImage(hospital);

  return (
    <article className={`directory-card directory-card--hospital ${demo ? "is-demo" : ""}`}>
      <div className="directory-card__media directory-card__media--wide">
        {mediaUrl ? (
          <PublicMediaImage src={mediaUrl} alt={`${hospital.hospitalDisplayName} hospital image`} className="directory-card__hero-image" objectFit="cover" fallback={<DirectoryFallbackAvatar label={providerBadgeText(hospital.hospitalDisplayName)} tone="hospital" />} />
        ) : (
          <DirectoryFallbackAvatar label={providerBadgeText(hospital.hospitalDisplayName)} tone="hospital" />
        )}
      </div>
      <div className="directory-card__body">
        <div className="directory-card__heading">
          <div>
            <strong>{hospital.hospitalDisplayName}</strong>
            <span>{[hospital.area, hospital.city].filter(Boolean).join(" · ") || "Hospital"}</span>
          </div>
          {hospital.departments.length ? <span className="chip chip--info">{hospital.departments.length} departments</span> : null}
        </div>
        {hospital.summary?.trim() ? <p className="directory-card__summary">{hospital.summary.trim()}</p> : null}
        <div className="directory-card__meta">
          {hospital.doctorsCount > 0 ? (
            <span>
              <CheckCircleOutlined fontSize="small" aria-hidden="true" />
              {hospital.doctorsCount} doctor{hospital.doctorsCount === 1 ? "" : "s"}
            </span>
          ) : null}
          {distance ? (
            <span>
              <DirectionsRunOutlined fontSize="small" aria-hidden="true" />
              {distance}
            </span>
          ) : null}
        </div>
        <div className="directory-card__chips">
          {hospital.departments.slice(0, 4).map((item) => (
            <span key={item} className="chip chip--muted">
              {item}
            </span>
          ))}
          {demo ? <span className="chip chip--demo">{demoLabel}</span> : null}
        </div>
        <div className="directory-card__actions">
          <Link className="secondary-button" to={hospital.publicPath ?? DISCOVER_DETAIL_PATHS.hospital(hospital.hospitalSlug)}>
            View hospital
          </Link>
          <Link className="secondary-button" to={hospital.publicPath ?? DISCOVER_DETAIL_PATHS.hospital(hospital.hospitalSlug)}>
            View doctors
          </Link>
          <a className="primary-button" href={careBookingUrl({ hospitalSlug: hospital.hospitalSlug })}>
            Explore departments
          </a>
        </div>
      </div>
    </article>
  );
}

export function SpecialityCard({
  speciality,
  icon,
  onSearchDoctors,
  onSearchClinics,
  showExploreAction = true,
}: {
  speciality: PublicSpecialitySummaryResponse;
  icon?: ReactNode;
  onSearchDoctors?: string;
  onSearchClinics?: string;
  showExploreAction?: boolean;
}) {
  return (
    <article className="directory-speciality-card">
      <div className="directory-speciality-card__icon" aria-hidden="true">
        {icon ?? <MenuBookOutlined fontSize="large" />}
      </div>
      <div className="directory-speciality-card__copy">
        <strong>{speciality.speciality}</strong>
        <p>
          {speciality.doctorsCount} doctor{speciality.doctorsCount === 1 ? "" : "s"} · {speciality.clinicsCount} clinic{speciality.clinicsCount === 1 ? "" : "s"}
        </p>
        {speciality.hospitalsCount != null ? <span>{speciality.hospitalsCount} hospital{speciality.hospitalsCount === 1 ? "" : "s"}</span> : null}
      </div>
      <div className="directory-speciality-card__actions">
        {showExploreAction ? (
          <Link className="secondary-button" to={DISCOVER_DETAIL_PATHS.speciality(speciality.specialitySlug)}>
            Explore
          </Link>
        ) : null}
        {onSearchDoctors ? (
          <Link className="text-button" to={onSearchDoctors}>
            Search doctors
          </Link>
        ) : null}
        {onSearchClinics ? (
          <Link className="text-button" to={onSearchClinics}>
            Search clinics
          </Link>
        ) : null}
      </div>
    </article>
  );
}

export function PopularSpecialityGrid({
  items,
  onExplore,
}: {
  items: PublicSpecialitySummaryResponse[];
  onExplore?: (speciality: PublicSpecialitySummaryResponse) => string;
}) {
  return (
    <div className="directory-popular-grid directory-popular-grid--specialities">
      {items.map((item) => (
        <SpecialityCard
          key={item.specialitySlug}
          speciality={item}
          icon={<MediationOutlined fontSize="large" aria-hidden="true" />}
          showExploreAction
        />
      ))}
    </div>
  );
}

export function PopularLinkChipRow({
  title,
  items,
  onSelect,
  secondaryAction,
}: {
  title: string;
  items: string[];
  onSelect: (value: string) => void;
  secondaryAction?: { label: string; to: string };
}) {
  return (
    <section className="directory-popular-row">
      <div className="directory-popular-row__heading">
        <strong>{title}</strong>
        {secondaryAction ? (
          <Link className="text-button" to={secondaryAction.to}>
            {secondaryAction.label}
          </Link>
        ) : null}
      </div>
      <div className="chip-row">
        {items.map((item) => (
          <button key={item} className="chip-button chip-button--search" type="button" onClick={() => onSelect(item)}>
            {item}
          </button>
        ))}
      </div>
    </section>
  );
}

export function AivaComingSoonPanel() {
  return (
    <aside className="directory-aiva-panel" aria-label="AIVA coming soon">
      <div className="directory-aiva-panel__header">
        <span className="directory-aiva-panel__icon" aria-hidden="true">
          <AutoAwesomeOutlined fontSize="small" />
        </span>
        <div>
          <span className="eyebrow">Meet AIVA</span>
          <h2>Your AI care guide</h2>
        </div>
      </div>
      <p>Not sure which speciality fits your concern?</p>
      <p>AIVA will soon help you explore relevant specialities and nearby care options.</p>
      <span className="chip chip--coming-soon">Coming soon</span>
      <p className="directory-aiva-panel__note">AIVA supports healthcare discovery and does not replace professional medical advice.</p>
    </aside>
  );
}

export function AivaComingSoonPanelCompact() {
  return <AivaComingSoonPanel />;
}

export function AlphabetNavigation({
  letters,
  activeLetter,
  disabledLetters,
  onSelect,
}: {
  letters: string[];
  activeLetter: string | null;
  disabledLetters: Set<string>;
  onSelect: (letter: string | null) => void;
}) {
  const firstEnabled = useMemo(() => letters.find((letter) => !disabledLetters.has(letter)) ?? null, [disabledLetters, letters]);

  return (
    <nav className="directory-alphabet-nav" aria-label="Browse A to Z">
      <button
        className={`directory-alphabet-nav__letter${activeLetter === null ? " is-active" : ""}`}
        type="button"
        onClick={() => onSelect(null)}
        aria-pressed={activeLetter === null}
      >
        All
      </button>
      {letters.map((letter) => {
        const disabled = disabledLetters.has(letter);
        return (
          <button
            key={letter}
            className={`directory-alphabet-nav__letter${activeLetter === letter ? " is-active" : ""}${disabled ? " is-disabled" : ""}`}
            type="button"
            onClick={() => onSelect(letter)}
            aria-pressed={activeLetter === letter}
            disabled={disabled}
          >
            {letter}
          </button>
        );
      })}
      {firstEnabled ? (
        <span className="visually-hidden" aria-live="polite">
          First enabled letter {firstEnabled}
        </span>
      ) : null}
    </nav>
  );
}

export function ResultCount({ value, label }: { value: number; label: string }) {
  return (
    <div className="directory-result-count">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

export function buildDirectoryResultLabel(count: number, label: string, locationLabel?: string | null) {
  const suffix = locationLabel?.trim() ? ` near ${locationLabel.trim()}` : "";
  return `${count} ${label}${count === 1 ? "" : "s"}${suffix}`;
}

export function countActiveDirectoryFilters(values: Array<string | number | boolean | null | undefined>) {
  return values.reduce<number>((count, value) => {
    if (value === null || value === undefined || value === false) {
      return count;
    }
    if (typeof value === "string") {
      return count + (value.trim() ? 1 : 0);
    }
    return count + 1;
  }, 0);
}

export function splitFilterValues(value: string | null | undefined) {
  if (!value?.trim()) {
    return [];
  }
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export function joinFilterValues(values: string[]) {
  return values.filter(Boolean).join(",");
}

export function toggleFilterValue(values: string[], value: string) {
  const normalized = value.trim();
  if (!normalized) {
    return values;
  }
  const current = new Set(values.map((item) => item.trim()).filter(Boolean));
  if (current.has(normalized)) {
    current.delete(normalized);
  } else {
    current.add(normalized);
  }
  return Array.from(current);
}

export function buildResultSummary({
  count,
  label,
  location,
  filters,
}: {
  count: number;
  label: string;
  location?: string | null;
  filters?: string[] | null;
}) {
  const activeFilters = filters?.filter(Boolean) ?? [];
  const filterText = activeFilters.length ? ` · ${activeFilters.join(" · ")}` : "";
  const locationText = location?.trim() ? ` near ${location.trim()}` : "";
  return `${count} ${label}${count === 1 ? "" : "s"}${locationText}${filterText}`;
}

export function pageAccentClass(page: "doctors" | "clinics" | "hospitals" | "specialities") {
  return `directory-page--${page}`;
}

export function pageAccentTone(page: "doctors" | "clinics" | "hospitals" | "specialities") {
  return DISCOVER_DIRECTORY_TOKENS[page].accentTone;
}

export function pageSearchButtonLabel(page: "doctors" | "clinics" | "hospitals" | "specialities") {
  switch (page) {
    case "doctors":
      return "Search doctors";
    case "clinics":
      return "Search clinics";
    case "hospitals":
      return "Search hospitals";
    case "specialities":
      return "Search specialities";
    default:
      return "Search";
  }
}

export function pageSearchPlaceholder(page: "doctors" | "clinics" | "hospitals" | "specialities") {
  switch (page) {
    case "doctors":
      return "Search doctor, speciality, clinic, or health concern";
    case "clinics":
      return "Search clinic, service, speciality, or area";
    case "hospitals":
      return "Search hospital, department, speciality, or area";
    case "specialities":
      return "Search speciality, condition, or care category";
    default:
      return "Search";
  }
}
