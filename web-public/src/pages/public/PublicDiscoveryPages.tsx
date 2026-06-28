import { type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Paper } from "@mui/material";
import {
  type PublicClinicDetailResponse,
  type PublicClinicSummaryResponse,
  type PublicDoctorDetailResponse,
  type PublicDoctorSummaryResponse,
  type PublicPageResponse,
  type PublicSearchResponse,
  type PublicSpecialityDetailResponse,
  type PublicSpecialitySummaryResponse,
  fetchPublicJson,
} from "../../api/publicCatalog";
import type { PatientPortalSession } from "../../api/patientPortal";
import { branding } from "../../branding";
import {
  PUBLIC_CURRENT_LOCATION_LABEL,
  PUBLIC_DEFAULT_LOCATION,
  PUBLIC_LOCATION_OPTIONS,
  normalizePublicLocation,
  readStoredPublicLocation,
  type PublicLocationCoordinates,
  usePublicLocation,
} from "../../context/publicLocation";
import { patientPortalBookingPath, patientPortalBookingTo } from "../patient/patientPortalClinicContext";

type FetchState<T> = {
  data: T;
  loading: boolean;
  error: string | null;
};

const emptyDoctorsPage: PublicPageResponse<PublicDoctorSummaryResponse> = {
  items: [],
  page: 0,
  size: 12,
  totalItems: 0,
  totalPages: 0,
};

const emptyClinicsPage: PublicPageResponse<PublicClinicSummaryResponse> = {
  items: [],
  page: 0,
  size: 12,
  totalItems: 0,
  totalPages: 0,
};

const emptySearchResponse: PublicSearchResponse = {
  doctors: { ...emptyDoctorsPage, size: 6 },
  clinics: { ...emptyClinicsPage, size: 6 },
  specialities: [],
};

const noPublicProfilesMessage =
  "No public profiles are enabled yet. Clinic admin can enable public listing from Public Profile settings.";

const POPULAR_SEARCHES = [
  "Cardiologist",
  "Pediatrician",
  "Dentist",
  "Eye Specialist",
  "General Physician",
  "Nearby Clinics",
  "Book Appointment",
  "Pharmacy",
] as const;

const POPULAR_SEARCH_EMOJI: Record<(typeof POPULAR_SEARCHES)[number], string> = {
  Cardiologist: "❤️",
  Pediatrician: "👶",
  Dentist: "🦷",
  "Eye Specialist": "👁",
  "General Physician": "🩺",
  "Nearby Clinics": "🏥",
  "Book Appointment": "📅",
  Pharmacy: "💊",
};

const CAREAI_PROMPTS = [
  "My child has fever",
  "I have knee pain",
  "Need a female gynecologist",
  "Find a dentist near me",
  "Book appointment with Dr Neha",
] as const;

const HERO_FEATURES = [
  {
    icon: "🩺",
    title: "Find Doctors",
    text: "Verified doctors across specialities with profile, experience, and availability insights.",
    cta: "Explore Doctors",
    to: "/doctors",
  },
  {
    icon: "🏥",
    title: "Discover Clinics",
    text: "Find nearby clinics and care centres by location, speciality, and services.",
    cta: "Explore Clinics",
    to: "/clinics",
  },
  {
    icon: "🤖",
    title: "Ask AIVA",
    text: "Describe symptoms or care needs in simple language and get guided next steps.",
    cta: "Ask AIVA",
    to: "/careai",
  },
  {
    icon: "📅",
    title: "Book Appointments",
    text: "Move from discovery to booking with availability, reminders, and assisted handoff.",
    cta: "Book Now",
    to: "/patient/book-appointment",
  },
] as const;

const TRUST_SIGNALS = [
  "Growing clinic network",
  "Verified doctor discovery",
  "AI-guided care navigation",
  "Secure patient experience",
] as const;

function usePublicResource<T>(path: string, params: Record<string, string | number | undefined | null>, initialValue: T): FetchState<T> {
  const [state, setState] = useState<FetchState<T>>({
    data: initialValue,
    loading: true,
    error: null,
  });

  useEffect(() => {
    const abortController = new AbortController();
    setState((current) => ({
      data: current.data,
      loading: true,
      error: null,
    }));

    fetchPublicJson<T>(path, params, abortController.signal)
      .then((result) => {
        setState({
          data: result,
          loading: false,
          error: null,
        });
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) {
          return;
        }
        setState({
          data: initialValue,
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load public directory data.",
        });
      });

    return () => abortController.abort();
  }, [path, JSON.stringify(params)]);

  return state;
}

function formatExperience(value: number | null | undefined) {
  if (value == null) {
    return "Experience shared after clinic review";
  }
  return `${value}+ years experience`;
}

function initials(label: string) {
  return label
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

function QueryToolbar({
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
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Doctor name, speciality, clinic, or area"
        />
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

function DirectoryState({
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
    return <div className="state-card">Loading public discovery results...</div>;
  }
  if (error) {
    return <div className="state-card">Unable to load public discovery data: {error}</div>;
  }
  if (empty) {
    return <div className="state-card">{emptyMessage}</div>;
  }
  return <>{children}</>;
}

function PaginationBar({
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
      <button className="ghost-button" type="button" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
        Previous
      </button>
      <span className="pagination-label">
        Page {page + 1} of {totalPages}
      </span>
      <button className="ghost-button" type="button" onClick={() => onPageChange(page + 1)} disabled={page + 1 >= totalPages}>
        Next
      </button>
    </div>
  );
}

function DoctorCard({ doctor, session }: { doctor: PublicDoctorSummaryResponse; session: PatientPortalSession | null }) {
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
        <Link
          className="ghost-button"
          to={patientPortalBookingTo(session, {
            doctorId: doctor.publicDoctorId,
            clinicSlug: doctor.clinicSlug,
          })}
        >
          Book appointment
        </Link>
      </div>
    </article>
  );
}

function ClinicCard({ clinic, session }: { clinic: PublicClinicSummaryResponse; session: PatientPortalSession | null }) {
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
        {clinic.availableToday ? <span className="status-pill">Available today</span> : <span className="chip">Appointments via patient portal</span>}
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
        <Link className="ghost-button" to={patientPortalBookingTo(session, {
          clinicSlug: clinic.clinicSlug,
        })}>
          Book appointment
        </Link>
      </div>
    </article>
  );
}

function slugify(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

function buildDirectorySearchParams({
  query,
  city,
  area,
  page,
  size,
  extra,
}: {
  query?: string | null;
  city?: string | null;
  area?: string | null;
  page: number;
  size: number;
  extra?: Record<string, string | undefined | null>;
}) {
  const params = new URLSearchParams();
  if (query?.trim()) {
    params.set("q", query.trim());
  }
  if (city?.trim()) {
    params.set("city", city.trim());
  }
  if (area?.trim()) {
    params.set("area", area.trim());
  }
  Object.entries(extra ?? {}).forEach(([key, value]) => {
    if (value?.trim()) {
      params.set(key, value.trim());
    }
  });
  params.set("page", `${Math.max(page, 0)}`);
  params.set("size", `${size}`);
  return params;
}

function useDirectoryFilters(defaultSize = 12) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [query, setQuery] = useState(searchParams.get("q") ?? "");
  const [city, setCity] = useState(searchParams.get("city") ?? "");
  const [area, setArea] = useState(searchParams.get("area") ?? "");
  const page = Number(searchParams.get("page") ?? "0") || 0;
  const size = Number(searchParams.get("size") ?? `${defaultSize}`) || defaultSize;

  useEffect(() => {
    setQuery(searchParams.get("q") ?? "");
    setCity(searchParams.get("city") ?? "");
    setArea(searchParams.get("area") ?? "");
  }, [searchParams]);

  function submit(basePath: string, extra?: Record<string, string | undefined>) {
    const params = buildDirectorySearchParams({
      query,
      city,
      area,
      page: 0,
      size: defaultSize,
      extra,
    });
    navigate(`${basePath}?${params.toString()}`);
  }

  function changePage(basePath: string, nextPage: number, extra?: Record<string, string | undefined>) {
    const params = buildDirectorySearchParams({
      query: searchParams.get("q"),
      city: searchParams.get("city"),
      area: searchParams.get("area"),
      page: nextPage,
      size,
      extra,
    });
    navigate(`${basePath}?${params.toString()}`);
  }

  return { searchParams, query, setQuery, city, setCity, area, setArea, page, size, submit, changePage };
}

export function PublicHomePage({ session }: { session: PatientPortalSession | null }) {
  const filters = useDirectoryFilters(6);
  const navigate = useNavigate();
  const [locationPickerOpen, setLocationPickerOpen] = useState(false);
  const storedLocation = useMemo(() => readStoredPublicLocation(), []);
  const { locationState, setSelectedLocation } = usePublicLocation();
  const hasSavedPublicLocation = locationState.source !== "default";
  const queryLocation = normalizePublicLocation(filters.searchParams.get("city")?.trim() || "");
  const selectedLocation = hasSavedPublicLocation ? locationState.location : queryLocation || storedLocation.location;
  const selectedCoordinates = hasSavedPublicLocation ? locationState.coordinates : null;
  const [locationDraft, setLocationDraft] = useState(() => selectedLocation);
  const [locationMessage, setLocationMessage] = useState<string | null>(null);
  const [locationBusy, setLocationBusy] = useState(false);
  const hasHydratedLocation = useRef(false);
  const hasQuery = Boolean(
    filters.searchParams.get("q") || filters.searchParams.get("city") || filters.searchParams.get("area"),
  );
  const search = usePublicResource<PublicSearchResponse>(
    "/api/public/search",
    {
      q: filters.searchParams.get("q"),
      city: filters.searchParams.get("city"),
      area: filters.searchParams.get("area"),
      page: filters.page,
      size: filters.size,
    },
    emptySearchResponse,
  );
  const displayLocation = selectedLocation;

  useEffect(() => {
    setLocationDraft(selectedLocation);
  }, [selectedLocation]);

  useEffect(() => {
    if (hasHydratedLocation.current) {
      return;
    }
    hasHydratedLocation.current = true;
    if (!hasSavedPublicLocation && queryLocation) {
      setSelectedLocation(queryLocation);
    }
  }, [hasSavedPublicLocation, queryLocation, setSelectedLocation]);

  function commitSelectedLocation(nextLocation: string, nextCoordinates: PublicLocationCoordinates | null = null) {
    const normalizedLocation = normalizePublicLocation(nextLocation) || PUBLIC_DEFAULT_LOCATION;
    setSelectedLocation(normalizedLocation, nextCoordinates);
    setLocationDraft(normalizedLocation);
    setLocationMessage(null);
    setLocationPickerOpen(false);
  }

  function handleApplyLocationDraft() {
    commitSelectedLocation(locationDraft);
  }

  function handleCurrentLocation() {
    setLocationMessage(null);
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setLocationMessage("Location services are not available in this browser. Please select your city manually.");
      return;
    }
    setLocationBusy(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        commitSelectedLocation(PUBLIC_CURRENT_LOCATION_LABEL, {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
        setLocationBusy(false);
      },
      () => {
        setLocationBusy(false);
        setLocationMessage("Location permission was not allowed. Please select your city manually.");
      },
      {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 300000,
      },
    );
  }

  function submitHeroSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = buildDirectorySearchParams({
      query: filters.query,
      city: displayLocation,
      area: filters.area,
      page: 0,
      size: 6,
      extra: selectedCoordinates
        ? {
            lat: `${selectedCoordinates.latitude}`,
            lng: `${selectedCoordinates.longitude}`,
          }
        : undefined,
    });
    navigate(`/?${params.toString()}`);
  }

  function openCareAi(prompt?: string) {
    if (!prompt) {
      navigate("/careai");
      return;
    }
    navigate(`/careai?prompt=${encodeURIComponent(prompt)}`);
  }

  return (
    <>
      <Paper component="section" elevation={0} className="launch-banner-card" aria-label="Demo UAT environment notice">
        <div className="launch-banner-badge">DEMO / UAT ENVIRONMENT</div>
        <h2>COMING SOON</h2>
        <p>Jeevanam Healthcare Patient Portal is currently being prepared for public launch.</p>
        <div className="launch-banner-list">
          <span>✓ Doctor Discovery</span>
          <span>✓ Online Appointment Booking</span>
          <span>✓ Patient Login with OTP</span>
          <span>✓ Consultation History</span>
          <span>✓ Prescriptions &amp; Lab Reports</span>
          <span>✓ AI Health Assistant powered by AIVA</span>
        </div>
        <p className="launch-banner-footer">Currently available for invited clinics, demonstrations and UAT testing.</p>
      </Paper>
      <section className="hero hero-smart">
        <div className="hero-copy">
          <span className="eyebrow">{branding.productName}</span>
          <h1>{branding.tagline}</h1>
          <p>
            Discover doctors, clinics, services, and next-step care through an AI-first experience designed for fast, confident patient decisions.
          </p>
          <p>{branding.tagline} for Clinics and Hospitals. Powered by {branding.aiPlatformName}.</p>
          <div className="cta-row public-quick-links">
            <Link className="primary-button" to="/doctors">
              Browse doctors
            </Link>
            <Link className="secondary-button" to="/clinics">
              Browse clinics
            </Link>
            <Link className="secondary-button" to="/patient/login">
              Patient Login
            </Link>
            <Link className="ghost-button" to="/careai">
              Ask AIVA
            </Link>
          </div>
          <div className="hero-feature-grid">
            {HERO_FEATURES.map((feature) => (
              <Link key={feature.title} to={feature.to} className="hero-feature-card">
                <div className="hero-feature-icon" aria-hidden="true">{feature.icon}</div>
                <strong>{feature.title}</strong>
                <p>{feature.text}</p>
                <span>{feature.cta}</span>
              </Link>
            ))}
          </div>
          <div className="trust-strip" aria-label={`${branding.productName} trust signals`}>
            {TRUST_SIGNALS.map((item) => (
              <div key={item} className="trust-pill">
                {item}
              </div>
            ))}
          </div>
        </div>
        <div className="hero-search-card hero-smart-card">
          <div className="smart-search-shell">
            <form className="smart-search-form" onSubmit={submitHeroSearch}>
              <div className="smart-search-header">
                <div>
                  <p className="smart-search-label">SMART SEARCH</p>
                  <h2>Search doctor, clinic, symptom, speciality, or service</h2>
                </div>
                <div className="smart-location-pill" aria-label={`Selected location ${displayLocation}`}>
                  <span>📍 {displayLocation}</span>
                  <button
                    className="smart-location-button"
                    type="button"
                    onClick={() => setLocationPickerOpen((current) => !current)}
                  >
                    Change
                  </button>
                </div>
              </div>

              <label className="smart-search-input-wrap">
                <span className="sr-only">Search care discovery</span>
                <input
                  className="smart-search-input"
                  value={filters.query}
                  onChange={(event) => filters.setQuery(event.target.value)}
                  placeholder="Search doctor, clinic, symptom, speciality, or service"
                />
              </label>

              {locationPickerOpen ? (
                <div className="smart-location-selector" role="dialog" aria-label="Select location">
                  <label className="smart-location-field">
                    <span>City or locality</span>
                    <input
                      value={locationDraft}
                      onChange={(event) => setLocationDraft(normalizePublicLocation(event.target.value))}
                      placeholder="Pune"
                    />
                  </label>
                  <div className="smart-location-options" role="list" aria-label="Popular locations">
                    {PUBLIC_LOCATION_OPTIONS.map((location) => (
                      <button
                        key={location}
                        className={`smart-location-option${displayLocation === location ? " is-active" : ""}`}
                        type="button"
                        onClick={() => commitSelectedLocation(location)}
                      >
                        {location}
                      </button>
                    ))}
                  </div>
                  <div className="smart-location-actions">
                    <button
                      className="secondary-button"
                      type="button"
                      onClick={handleApplyLocationDraft}
                      disabled={!normalizePublicLocation(locationDraft)}
                    >
                      Save location
                    </button>
                    <button className="ghost-button" type="button" onClick={handleCurrentLocation} disabled={locationBusy}>
                      {locationBusy ? "Detecting..." : "Use my current location"}
                    </button>
                    <button
                      className="ghost-button"
                      type="button"
                      onClick={() => {
                        setLocationDraft(selectedLocation);
                        setLocationMessage(null);
                        setLocationPickerOpen(false);
                      }}
                    >
                      Cancel
                    </button>
                  </div>
                  {locationMessage ? <p className="patient-field-hint">{locationMessage}</p> : null}
                </div>
              ) : null}

              <div className="smart-chip-section">
                <span className="smart-search-label">Popular searches</span>
                <div className="smart-chip-grid">
                  {POPULAR_SEARCHES.map((item) => {
                    const chipLabel = `${POPULAR_SEARCH_EMOJI[item]} ${item}`;
                    const active = filters.query.trim().toLowerCase() === item.toLowerCase();
                    return (
                      <button
                        key={item}
                        className={`smart-chip${active ? " is-active" : ""}`}
                        type="button"
                        onClick={() => filters.setQuery(item)}
                      >
                        {chipLabel}
                      </button>
                    );
                  })}
                </div>
              </div>

              <button className="primary-button smart-search-submit" type="submit">
                Search
              </button>
            </form>

            <div className="smart-divider" aria-hidden="true">
              <span>OR</span>
            </div>

            <div className="careai-cta">
              <div className="careai-cta-copy">
                <p className="smart-search-label">🤖 ASK CAREAI</p>
                <h3>Need help finding the right doctor? Ask AIVA.</h3>
              </div>
              <div className="careai-prompt-grid">
                {CAREAI_PROMPTS.map((prompt) => (
                  <button
                    key={prompt}
                    className="careai-prompt-chip"
                    type="button"
                    onClick={() => openCareAi(prompt)}
                  >
                    {prompt}
                  </button>
                ))}
              </div>
              <button className="secondary-button careai-cta-button" type="button" onClick={() => openCareAi()}>
                Ask AIVA
              </button>
            </div>
          </div>
        </div>
      </section>

      <section className="content-section">
        <div className="section-heading">
          <span className="eyebrow">{hasQuery ? "Search results" : "Featured discovery"}</span>
          <h2>{hasQuery ? "Matching doctors, clinics, and specialities" : "Explore public doctor discovery"}</h2>
          <p>
            Public search only shows onboarded clinics and doctors marked safe for listing. Internal schedules, contact details, and staff-only data stay hidden.
          </p>
        </div>
        <DirectoryState
          loading={search.loading}
          error={search.error}
          empty={search.data.doctors.items.length === 0 && search.data.clinics.items.length === 0 && search.data.specialities.length === 0}
          emptyMessage={noPublicProfilesMessage}
        >
          <div className="public-preview-grid">
            <article className="patient-panel">
              <div className="patient-panel-heading">
                <h2>Doctors</h2>
                <Link to={`/doctors?${filters.searchParams.toString()}`}>View all</Link>
              </div>
              <div className="public-card-stack">
                {search.data.doctors.items.slice(0, 3).map((doctor) => (
                  <DoctorCard key={doctor.doctorSlug} doctor={doctor} session={session} />
                ))}
              </div>
            </article>

            <article className="patient-panel">
              <div className="patient-panel-heading">
                <h2>Clinics</h2>
                <Link to={`/clinics?${filters.searchParams.toString()}`}>View all</Link>
              </div>
              <div className="public-card-stack">
                {search.data.clinics.items.slice(0, 3).map((clinic) => (
                  <ClinicCard key={clinic.clinicSlug} clinic={clinic} session={session} />
                ))}
              </div>
            </article>
          </div>

          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Specialities</h2>
              <Link to="/specialities">Browse all</Link>
            </div>
            <div className="chip-row">
              {search.data.specialities.slice(0, 10).map((speciality) => (
                <Link key={speciality.specialitySlug} className="chip" to={`/specialities/${speciality.specialitySlug}`}>
                  {speciality.speciality}
                </Link>
              ))}
            </div>
          </article>
        </DirectoryState>
      </section>
    </>
  );
}

export function PublicDoctorsPage({ session }: { session: PatientPortalSession | null }) {
  const filters = useDirectoryFilters();
  const speciality = filters.searchParams.get("speciality");
  const clinic = filters.searchParams.get("clinic");
  const doctors = usePublicResource<PublicPageResponse<PublicDoctorSummaryResponse>>(
    "/api/public/doctors",
    {
      q: filters.searchParams.get("q"),
      city: filters.searchParams.get("city"),
      area: filters.searchParams.get("area"),
      speciality,
      clinic,
      page: filters.page,
      size: filters.size,
    },
    emptyDoctorsPage,
  );

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Doctor directory</span>
        <h1>Browse public doctor profiles</h1>
        <p>Doctor cards stay public-safe: speciality, experience, clinic context, and a next-slot summary when available.</p>
      </div>
      <QueryToolbar
        actionLabel="Search doctors"
        query={filters.query}
        setQuery={filters.setQuery}
        city={filters.city}
        setCity={filters.setCity}
        area={filters.area}
        setArea={filters.setArea}
        onSubmit={(event) => {
          event.preventDefault();
          filters.submit("/doctors", {
            speciality: speciality ?? undefined,
            clinic: clinic ?? undefined,
          });
        }}
      />
      <DirectoryState
        loading={doctors.loading}
        error={doctors.error}
        empty={doctors.data.items.length === 0}
        emptyMessage={noPublicProfilesMessage}
      >
        <div className="public-directory-grid">
          {doctors.data.items.map((doctor) => (
            <DoctorCard key={doctor.doctorSlug} doctor={doctor} session={session} />
          ))}
        </div>
        <PaginationBar
          page={doctors.data.page}
          totalPages={doctors.data.totalPages}
          onPageChange={(nextPage) =>
            filters.changePage("/doctors", nextPage, {
              speciality: speciality ?? undefined,
              clinic: clinic ?? undefined,
            })
          }
        />
      </DirectoryState>
    </section>
  );
}

export function PublicDoctorDetailPage({ session }: { session: PatientPortalSession | null }) {
  const { doctorSlug = "" } = useParams();
  const detail = usePublicResource<PublicDoctorDetailResponse | null>(`/api/public/doctors/${doctorSlug}`, {}, null);

  return (
    <section className="page-section">
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data}
        emptyMessage="This doctor profile is not available for public discovery."
      >
        {detail.data ? (
          <div className="public-detail-shell">
            <article className="patient-panel public-detail-hero">
              <div className="directory-card-top">
                <div className="directory-avatar directory-avatar-large" aria-hidden="true">
                  {detail.data.photoUrl ? <img src={detail.data.photoUrl} alt="" /> : <span>{initials(detail.data.doctorDisplayName)}</span>}
                </div>
                <div className="directory-card-heading">
                  <strong>{detail.data.doctorDisplayName}</strong>
                  <span>{detail.data.specialities.join(", ") || "General consultation"}</span>
                  <p>{detail.data.qualification ?? "Qualification shared by clinic onboarding"} · {formatExperience(detail.data.yearsOfExperience)}</p>
                </div>
              </div>
              <div className="directory-badge-row">
                {detail.data.availableToday ? <span className="status-pill">Available today</span> : null}
                {detail.data.languages.length ? <span className="chip">Languages: {detail.data.languages.join(", ")}</span> : null}
              </div>
              <div className="directory-action-row">
                <Link
                  className="primary-button"
                  to={patientPortalBookingTo(session, {
                    doctorId: detail.data.publicDoctorId,
                    ...(detail.data.clinics.length === 1
                      ? { clinicSlug: detail.data.clinics[0].clinicSlug }
                      : {}),
                  })}
                >
                  Book appointment
                </Link>
                <Link className="ghost-button" to="/careai">
                  Ask AIVA
                </Link>
              </div>
            </article>

            <div className="public-detail-grid">
              <article className="patient-panel">
                <div className="patient-panel-heading">
                  <h2>Clinic</h2>
                </div>
                <div className="patient-subcard-list">
                  {detail.data.clinics.map((clinic) => (
                    <Link key={clinic.clinicSlug} className="patient-subcard" to={`/clinics/${clinic.clinicSlug}`}>
                      <strong>{clinic.clinicDisplayName}</strong>
                      <span>
                        {clinic.area ?? clinic.city ?? "Clinic profile"}
                        {clinic.area && clinic.city ? ` · ${clinic.city}` : ""}
                      </span>
                    </Link>
                  ))}
                </div>
              </article>

              <article className="patient-panel">
                <div className="patient-panel-heading">
                  <h2>Availability</h2>
                </div>
                <div className="patient-detail-list">
                  <div>
                    <strong>Available days</strong>
                    <span>{detail.data.availableDays.join(", ") || "Clinic shares availability after review"}</span>
                  </div>
                </div>
                <div className="patient-subcard-list">
                  {detail.data.nextAvailableSlots.length ? (
                    detail.data.nextAvailableSlots.map((slot) => (
                      <div key={slot} className="patient-subcard">
                        <strong>Next slot</strong>
                        <span>{slot}</span>
                      </div>
                    ))
                  ) : (
                    <div className="patient-inline-empty">Next available slots will appear when the clinic publishes patient-safe availability.</div>
                  )}
                </div>
              </article>
            </div>
          </div>
        ) : null}
      </DirectoryState>
    </section>
  );
}

export function PublicClinicsPage({ session }: { session: PatientPortalSession | null }) {
  const filters = useDirectoryFilters();
  const speciality = filters.searchParams.get("speciality");
  const clinics = usePublicResource<PublicPageResponse<PublicClinicSummaryResponse>>(
    "/api/public/clinics",
    {
      q: filters.searchParams.get("q"),
      city: filters.searchParams.get("city"),
      area: filters.searchParams.get("area"),
      speciality,
      page: filters.page,
      size: filters.size,
    },
    emptyClinicsPage,
  );

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Clinic directory</span>
        <h1>Browse public clinic profiles</h1>
        <p>Public clinic cards show location, doctor count, visible specialities, and whether any listed doctor has availability today.</p>
      </div>
      <QueryToolbar
        actionLabel="Search clinics"
        query={filters.query}
        setQuery={filters.setQuery}
        city={filters.city}
        setCity={filters.setCity}
        area={filters.area}
        setArea={filters.setArea}
        onSubmit={(event) => {
          event.preventDefault();
          filters.submit("/clinics", { speciality: speciality ?? undefined });
        }}
      />
      <DirectoryState
        loading={clinics.loading}
        error={clinics.error}
        empty={clinics.data.items.length === 0}
        emptyMessage={noPublicProfilesMessage}
      >
        <div className="public-directory-grid">
          {clinics.data.items.map((clinic) => (
            <ClinicCard key={clinic.clinicSlug} clinic={clinic} session={session} />
          ))}
        </div>
        <PaginationBar
          page={clinics.data.page}
          totalPages={clinics.data.totalPages}
          onPageChange={(nextPage) => filters.changePage("/clinics", nextPage, { speciality: speciality ?? undefined })}
        />
      </DirectoryState>
    </section>
  );
}

export function PublicClinicDetailPage({ session }: { session: PatientPortalSession | null }) {
  const { clinicSlug = "" } = useParams();
  const detail = usePublicResource<PublicClinicDetailResponse | null>(`/api/public/clinics/${clinicSlug}`, {}, null);
  const bookingPath = detail.data
    ? patientPortalBookingTo(session, {
        clinicSlug: detail.data.clinicSlug,
      })
    : patientPortalBookingPath(session);

  return (
    <section className="page-section">
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data}
        emptyMessage="This clinic profile is not available for public discovery."
      >
        {detail.data ? (
          <div className="public-detail-shell">
            <article className="patient-panel public-detail-hero">
              <div className="directory-card-top">
                <div className="directory-avatar directory-avatar-large" aria-hidden="true">
                  {detail.data.logoUrl ? <img src={detail.data.logoUrl} alt="" /> : <span>{initials(detail.data.clinicDisplayName)}</span>}
                </div>
                <div className="directory-card-heading">
                  <strong>{detail.data.clinicDisplayName}</strong>
                  <span>{detail.data.area ?? detail.data.city ?? "Clinic profile"}</span>
                  <p>{detail.data.address ?? "Clinic address shared after onboarding"}</p>
                </div>
              </div>
              <div className="directory-badge-row">
                {detail.data.availableToday ? <span className="status-pill">Available today</span> : null}
                {detail.data.specialities.slice(0, 4).map((speciality) => (
                  <Link key={speciality} className="chip" to={`/specialities/${slugify(speciality)}`}>
                    {speciality}
                  </Link>
                ))}
              </div>
              <div className="directory-action-row">
                <Link className="primary-button" to={bookingPath}>
                  Book appointment
                </Link>
                <Link className="ghost-button" to="/careai">
                  Ask AIVA
                </Link>
              </div>
            </article>

            <div className="public-detail-grid">
              <article className="patient-panel">
                <div className="patient-panel-heading">
                  <h2>Timings</h2>
                </div>
                <div className="patient-subcard-list">
                  {detail.data.timings.length ? (
                    detail.data.timings.map((timing) => (
                      <div key={timing} className="patient-subcard">
                        <strong>{timing}</strong>
                        <span>Published from visible doctor schedules only.</span>
                      </div>
                    ))
                  ) : (
                    <div className="patient-inline-empty">Clinic timings will appear when visible doctor schedules are published.</div>
                  )}
                </div>
              </article>

              <article className="patient-panel">
                <div className="patient-panel-heading">
                  <h2>Doctors</h2>
                </div>
                <div className="public-card-stack">
                  {detail.data.doctors.map((doctor) => (
                    <DoctorCard key={doctor.doctorSlug} doctor={doctor} session={session} />
                  ))}
                </div>
              </article>
            </div>
          </div>
        ) : null}
      </DirectoryState>
    </section>
  );
}

export function PublicSpecialitiesPage() {
  const filters = useDirectoryFilters(24);
  const specialities = usePublicResource<PublicSpecialitySummaryResponse[]>(
    "/api/public/specialities",
    {
      q: filters.searchParams.get("q"),
      city: filters.searchParams.get("city"),
    },
    [],
  );

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Specialities</span>
        <h1>Explore specialities across public clinics</h1>
        <p>Use speciality pages to narrow down which visible doctors and clinics match the care you need.</p>
      </div>
      <form
        className="toolbar-card public-toolbar-card"
        onSubmit={(event) => {
          event.preventDefault();
          filters.submit("/specialities");
        }}
      >
        <label className="toolbar-field">
          <span>Speciality</span>
          <input value={filters.query} onChange={(event) => filters.setQuery(event.target.value)} placeholder="Dermatology, pediatrics, cardiology" />
        </label>
        <label className="toolbar-field">
          <span>City</span>
          <input value={filters.city} onChange={(event) => filters.setCity(event.target.value)} placeholder="Filter by city" />
        </label>
        <button className="primary-button" type="submit">
          Search specialities
        </button>
      </form>
      <DirectoryState
        loading={specialities.loading}
        error={specialities.error}
        empty={specialities.data.length === 0}
        emptyMessage={noPublicProfilesMessage}
      >
        <div className="public-directory-grid speciality-directory-grid">
          {specialities.data.map((speciality) => (
            <Link key={speciality.specialitySlug} className="public-directory-card feature-card" to={`/specialities/${speciality.specialitySlug}`}>
              <strong>{speciality.speciality}</strong>
              <p>
                {speciality.doctorsCount} doctor{speciality.doctorsCount === 1 ? "" : "s"} across {speciality.clinicsCount} clinic{speciality.clinicsCount === 1 ? "" : "s"}.
              </p>
            </Link>
          ))}
        </div>
      </DirectoryState>
    </section>
  );
}

export function PublicSpecialityDetailPage({ session }: { session: PatientPortalSession | null }) {
  const { specialitySlug = "" } = useParams();
  const filters = useDirectoryFilters();
  const detail = usePublicResource<PublicSpecialityDetailResponse | null>(
    `/api/public/specialities/${specialitySlug}`,
    {
      q: filters.searchParams.get("q"),
      city: filters.searchParams.get("city"),
      area: filters.searchParams.get("area"),
      clinic: filters.searchParams.get("clinic"),
      page: filters.page,
      size: filters.size,
    },
    null,
  );

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Speciality detail</span>
        <h1>{detail.data?.speciality ?? "Speciality"}</h1>
        <p>Browse public doctor profiles for this speciality, then continue to patient OTP when you are ready to book.</p>
      </div>
      <QueryToolbar
        actionLabel="Filter doctors"
        query={filters.query}
        setQuery={filters.setQuery}
        city={filters.city}
        setCity={filters.setCity}
        area={filters.area}
        setArea={filters.setArea}
        onSubmit={(event) => {
          event.preventDefault();
          filters.submit(`/specialities/${specialitySlug}`);
        }}
      />
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data || detail.data.doctors.items.length === 0}
        emptyMessage="No public doctors matched this speciality filter."
      >
        {detail.data ? (
          <>
            <div className="public-directory-grid">
              {detail.data.doctors.items.map((doctor) => (
                <DoctorCard key={doctor.doctorSlug} doctor={doctor} session={session} />
              ))}
            </div>
            <PaginationBar
              page={detail.data.doctors.page}
              totalPages={detail.data.doctors.totalPages}
              onPageChange={(nextPage) => filters.changePage(`/specialities/${specialitySlug}`, nextPage)}
            />
          </>
        ) : null}
      </DirectoryState>
    </section>
  );
}

export function PublicCareAiPage({ session }: { session: PatientPortalSession | null }) {
  const [searchParams] = useSearchParams();
  const suggestedPrompt = searchParams.get("prompt")?.trim() ?? "";

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Public AIVA</span>
        <h1>Ask AIVA to narrow down the right in-clinic path.</h1>
        <p>AIVA can guide you toward a speciality, doctor, clinic, or next availability. It does not diagnose, prescribe, or expose patient/private data.</p>
      </div>
      <div className="careai-panel public-careai-panel">
        <article className="info-card accent-card">
          <strong>Try asking</strong>
          <ul className="plain-list">
            <li>I need a skin doctor in Pune.</li>
            <li>Show me cardiologists near Baner.</li>
            <li>Which clinic has a pediatrician available today?</li>
          </ul>
          {suggestedPrompt ? (
            <div className="patient-inline-empty">
              <strong>Suggested prompt</strong>
              <span>{suggestedPrompt}</span>
            </div>
          ) : null}
        </article>
        <article className="info-card">
          <strong>Next step</strong>
          <p>Use the public directories to compare options, then continue into the patient portal when you are ready to book.</p>
          <div className="directory-action-row">
            <Link className="primary-button" to="/doctors">
              Browse doctors
            </Link>
            <Link className="ghost-button" to={patientPortalBookingPath(session)}>
              Book through patient portal
            </Link>
          </div>
        </article>
      </div>
    </section>
  );
}
