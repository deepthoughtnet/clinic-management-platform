import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type {
  PublicClinicDetailResponse,
  PublicClinicSummaryResponse,
  PublicDoctorDetailResponse,
  PublicDoctorSummaryResponse,
  PublicPageResponse,
  PublicSearchResponse,
  PublicSpecialityDetailResponse,
  PublicSpecialitySummaryResponse,
} from "../../api/publicCatalog";
import { fetchPublicJson } from "../../api/publicCatalog";
import {
  ClinicCard,
  DirectoryState,
  DoctorCard,
  PaginationBar,
  QueryToolbar,
  careBookingUrl,
  emptyClinicsPage,
  emptyDoctorsPage,
  formatExperience,
  initials,
  noPublicProfilesMessage,
} from "../../components/DiscoveryComponents";
import {
  PUBLIC_CURRENT_LOCATION_LABEL,
  PUBLIC_DEFAULT_LOCATION,
  PUBLIC_LOCATION_OPTIONS,
  normalizePublicLocation,
  readStoredPublicLocation,
  type PublicLocationCoordinates,
  usePublicLocation,
} from "../../context/PublicLocationContext";
import { DISCOVER_ROUTES } from "../../routes";
import {
  discoveryEmptyMessage,
  matchesDiscoveryQuery,
  normalizeDiscoveryText,
  scoreDiscoveryLocation,
  slugify,
} from "../../utils/publicDiscovery";

type FetchState<T> = {
  data: T;
  loading: boolean;
  error: string | null;
};

const emptySearchResponse: PublicSearchResponse = {
  doctors: { ...emptyDoctorsPage, size: 6 },
  clinics: { ...emptyClinicsPage, size: 6 },
  specialities: [],
};

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

const CATEGORY_CARDS = [
  {
    title: "Doctors",
    body: "Browse individual medical professionals by speciality, clinic, language, and public availability.",
    to: DISCOVER_ROUTES.doctors.path,
  },
  {
    title: "Clinics",
    body: "Explore local care centres, doctor teams, specialities, and public timings.",
    to: DISCOVER_ROUTES.clinics.path,
  },
  {
    title: "Hospitals",
    body: "Hospital discovery foundations are ready for departments, facilities, and public pages.",
    to: DISCOVER_ROUTES.hospitals.path,
  },
  {
    title: "Specialities",
    body: "Start with the type of care needed, then compare public doctors and clinics.",
    to: DISCOVER_ROUTES.specialities.path,
  },
  {
    title: "Services",
    body: "Service discovery will cover consultations, diagnostics, vaccination, and approved provider offerings.",
    to: DISCOVER_ROUTES.services.path,
  },
] as const;

const TRUST_SIGNALS = [
  "Public-safe provider profiles",
  "Verified publication workflow",
  "No private health data required to search",
  "Booking starts through Jeevanam Care",
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

function filterAndSortDoctorResults(doctors: PublicDoctorSummaryResponse[], query: string, selectedLocation: string) {
  return [...doctors]
    .filter((doctor) =>
      matchesDiscoveryQuery(
        [
          doctor.doctorDisplayName,
          doctor.speciality,
          doctor.clinicDisplayName,
          doctor.area,
          doctor.city,
          doctor.nextAvailableSlotSummary,
          doctor.languages.join(" "),
        ],
        query,
      ),
    )
    .sort((left, right) => {
      const locationScore =
        scoreDiscoveryLocation(right.city, right.area, selectedLocation) -
        scoreDiscoveryLocation(left.city, left.area, selectedLocation);
      if (locationScore !== 0) {
        return locationScore;
      }
      return normalizeDiscoveryText(left.doctorDisplayName).localeCompare(normalizeDiscoveryText(right.doctorDisplayName));
    });
}

function filterAndSortClinicResults(clinics: PublicClinicSummaryResponse[], query: string, selectedLocation: string) {
  return [...clinics]
    .filter((clinic) =>
      matchesDiscoveryQuery(
        [
          clinic.clinicDisplayName,
          clinic.address,
          clinic.area,
          clinic.city,
          clinic.specialities.join(" "),
          clinic.doctorsCount,
        ],
        query,
      ),
    )
    .sort((left, right) => {
      const locationScore =
        scoreDiscoveryLocation(right.city, right.area, selectedLocation) -
        scoreDiscoveryLocation(left.city, left.area, selectedLocation);
      if (locationScore !== 0) {
        return locationScore;
      }
      return normalizeDiscoveryText(left.clinicDisplayName).localeCompare(normalizeDiscoveryText(right.clinicDisplayName));
    });
}

function filterAndSortSpecialities(specialities: PublicSpecialitySummaryResponse[], query: string) {
  return [...specialities]
    .filter((speciality) =>
      matchesDiscoveryQuery([speciality.speciality, speciality.doctorsCount, speciality.clinicsCount], query),
    )
    .sort((left, right) => normalizeDiscoveryText(left.speciality).localeCompare(normalizeDiscoveryText(right.speciality)));
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
  if (query?.trim()) params.set("q", query.trim());
  if (city?.trim()) params.set("city", city.trim());
  if (area?.trim()) params.set("area", area.trim());
  Object.entries(extra ?? {}).forEach(([key, value]) => {
    if (value?.trim()) params.set(key, value.trim());
  });
  params.set("page", `${Math.max(page, 0)}`);
  params.set("size", `${size}`);
  return params;
}

function useDirectoryFilters(defaultSize = 12) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { locationState } = usePublicLocation();
  const selectedLocation =
    locationState.location === PUBLIC_CURRENT_LOCATION_LABEL
      ? PUBLIC_DEFAULT_LOCATION
      : locationState.location || PUBLIC_DEFAULT_LOCATION;
  const queryParam = searchParams.get("q") ?? "";
  const cityParam = searchParams.get("city") ?? "";
  const [query, setQuery] = useState(queryParam);
  const [city, setCity] = useState(cityParam || selectedLocation);
  const [area, setArea] = useState(searchParams.get("area") ?? "");
  const page = Number(searchParams.get("page") ?? "0") || 0;
  const size = Number(searchParams.get("size") ?? `${defaultSize}`) || defaultSize;

  useEffect(() => {
    setQuery(searchParams.get("q") ?? "");
    setCity(searchParams.get("city") ?? selectedLocation);
    setArea(searchParams.get("area") ?? "");
  }, [searchParams, selectedLocation]);

  function submit(basePath: string, extra?: Record<string, string | undefined | null>) {
    const params = buildDirectorySearchParams({
      query,
      city: city || selectedLocation,
      area,
      page: 0,
      size: defaultSize,
      extra,
    });
    navigate(`${basePath}?${params.toString()}`);
  }

  function changePage(basePath: string, nextPage: number, extra?: Record<string, string | undefined | null>) {
    const params = buildDirectorySearchParams({
      query: searchParams.get("q"),
      city: searchParams.get("city") || selectedLocation,
      area: searchParams.get("area"),
      page: nextPage,
      size,
      extra,
    });
    navigate(`${basePath}?${params.toString()}`);
  }

  return { searchParams, query, setQuery, city, setCity, area, setArea, page, size, submit, changePage };
}

export function PublicHomePage() {
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
  const hasQuery = Boolean(filters.searchParams.get("q") || filters.searchParams.get("city") || filters.searchParams.get("area"));
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
  const searchableLocation = displayLocation === PUBLIC_CURRENT_LOCATION_LABEL ? PUBLIC_DEFAULT_LOCATION : displayLocation;
  const submittedQuery = search.data ? filters.searchParams.get("q") ?? "" : "";
  const homeDoctors = useMemo(
    () => filterAndSortDoctorResults(search.data.doctors.items, submittedQuery, searchableLocation),
    [search.data.doctors.items, searchableLocation, submittedQuery],
  );
  const homeClinics = useMemo(
    () => filterAndSortClinicResults(search.data.clinics.items, submittedQuery, searchableLocation),
    [search.data.clinics.items, searchableLocation, submittedQuery],
  );
  const homeSpecialities = useMemo(
    () => filterAndSortSpecialities(search.data.specialities, submittedQuery),
    [search.data.specialities, submittedQuery],
  );

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
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }

  function submitHeroSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = buildDirectorySearchParams({
      query: filters.query,
      city: searchableLocation,
      area: filters.area,
      page: 0,
      size: 6,
      extra: selectedCoordinates
        ? { lat: `${selectedCoordinates.latitude}`, lng: `${selectedCoordinates.longitude}` }
        : undefined,
    });
    navigate(`/?${params.toString()}`);
  }

  function applyPopularSearch(searchTerm: string) {
    const params = buildDirectorySearchParams({
      query: searchTerm,
      city: searchableLocation,
      area: filters.area,
      page: 0,
      size: 6,
    });
    navigate(`/?${params.toString()}`);
  }

  return (
    <>
      <section className="hero-section hero-discovery">
        <div className="hero-grid">
          <div className="hero-copy">
            <span className="eyebrow">Jeevanam public discovery</span>
            <h1>Find the right doctor, clinic, or hospital.</h1>
            <p>Search trusted healthcare providers, explore services, and start your care journey with Jeevanam.</p>
            <div className="cta-row">
              <Link className="primary-button" to={DISCOVER_ROUTES.doctors.path}>Find a Doctor</Link>
              <Link className="secondary-button" to={DISCOVER_ROUTES.clinics.path}>Find a Clinic</Link>
              <Link className="text-button" to={DISCOVER_ROUTES.listPractice.path}>List Your Practice</Link>
            </div>
            <div className="trust-strip" aria-label="Jeevanam Discover trust signals">
              {TRUST_SIGNALS.map((item) => (
                <span key={item}>{item}</span>
              ))}
            </div>
          </div>

          <form id="find-care" className="search-panel smart-search-form" aria-label="Discover care search" onSubmit={submitHeroSearch}>
            <div className="smart-search-header">
              <div>
                <span className="eyebrow">Public search</span>
                <h2>Search doctor, clinic, symptom, speciality, or service</h2>
              </div>
              <button className="location-pill" type="button" onClick={() => setLocationPickerOpen((current) => !current)}>
                {displayLocation}
              </button>
            </div>
            <label>
              <span>What are you looking for?</span>
              <input
                value={filters.query}
                onChange={(event) => filters.setQuery(event.target.value)}
                placeholder="Doctor, speciality, service, clinic, or hospital"
                autoComplete="off"
              />
            </label>
            <label>
              <span>Area</span>
              <input value={filters.area} onChange={(event) => filters.setArea(event.target.value)} placeholder="Baner, Kothrud, Andheri" autoComplete="off" />
            </label>
            {locationPickerOpen ? (
              <div className="location-selector" role="dialog" aria-label="Select location">
                <label>
                  <span>City or locality</span>
                  <input value={locationDraft} onChange={(event) => setLocationDraft(normalizePublicLocation(event.target.value))} placeholder="Pune" />
                </label>
                <div className="chip-row" role="list" aria-label="Popular locations">
                  {PUBLIC_LOCATION_OPTIONS.map((location) => (
                    <button key={location} className="chip-button" type="button" onClick={() => commitSelectedLocation(location)}>
                      {location}
                    </button>
                  ))}
                </div>
                <div className="cta-row">
                  <button className="secondary-button" type="button" onClick={() => commitSelectedLocation(locationDraft)} disabled={!normalizePublicLocation(locationDraft)}>
                    Save location
                  </button>
                  <button className="text-button" type="button" onClick={handleCurrentLocation} disabled={locationBusy}>
                    {locationBusy ? "Detecting..." : "Use my current location"}
                  </button>
                </div>
                {locationMessage ? <p className="form-note" role="status">{locationMessage}</p> : null}
              </div>
            ) : null}
            <div className="chip-row" aria-label="Popular searches">
              {POPULAR_SEARCHES.map((item) => (
                <button key={item} className="chip-button" type="button" onClick={() => applyPopularSearch(item)}>
                  {item}
                </button>
              ))}
            </div>
            <button className="primary-button" type="submit">Search</button>
          </form>
        </div>
      </section>

      <section className="page-section">
        <div className="section-heading">
          <span className="eyebrow">{hasQuery ? "Search results" : "Featured providers"}</span>
          <h2>{hasQuery ? "Matching doctors, clinics, and specialities" : "Explore public healthcare discovery"}</h2>
          <p>Public search uses provider information approved for public discovery. Private health records and operational data are not part of Discover.</p>
        </div>
        <DirectoryState
          loading={search.loading}
          error={search.error}
          empty={homeDoctors.length === 0 && homeClinics.length === 0 && homeSpecialities.length === 0}
          emptyMessage={discoveryEmptyMessage({
            query: submittedQuery || filters.searchParams.get("city") || filters.searchParams.get("area"),
            selectedLocation: displayLocation,
            defaultMessage: noPublicProfilesMessage,
          })}
        >
          <div className="public-preview-grid">
            <article className="result-panel">
              <div className="panel-heading">
                <h2>Doctors</h2>
                <Link to={`/doctors?${filters.searchParams.toString()}`}>View all</Link>
              </div>
              <div className="public-card-stack">
                {homeDoctors.slice(0, 3).map((doctor) => <DoctorCard key={doctor.doctorSlug} doctor={doctor} />)}
              </div>
            </article>
            <article className="result-panel">
              <div className="panel-heading">
                <h2>Clinics</h2>
                <Link to={`/clinics?${filters.searchParams.toString()}`}>View all</Link>
              </div>
              <div className="public-card-stack">
                {homeClinics.slice(0, 3).map((clinic) => <ClinicCard key={clinic.clinicSlug} clinic={clinic} />)}
              </div>
            </article>
          </div>
          <article className="result-panel">
            <div className="panel-heading">
              <h2>Specialities</h2>
              <Link to={DISCOVER_ROUTES.specialities.path}>Browse all</Link>
            </div>
            <div className="chip-row">
              {homeSpecialities.slice(0, 10).map((speciality) => (
                <Link key={speciality.specialitySlug} className="chip" to={`/specialities/${speciality.specialitySlug}`}>
                  {speciality.speciality}
                </Link>
              ))}
            </div>
          </article>
        </DirectoryState>
      </section>

      <section className="page-section">
        <div className="section-heading">
          <span className="eyebrow">Discovery categories</span>
          <h2>Start from the route that matches your care need.</h2>
        </div>
        <div className="category-grid">
          {CATEGORY_CARDS.map((category) => (
            <Link className="category-card" to={category.to} key={category.title}>
              <strong>{category.title}</strong>
              <span>{category.body}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="provider-band">
        <div>
          <span className="eyebrow">For providers</span>
          <h2>Grow your healthcare presence with Jeevanam.</h2>
          <p>Create a public profile, prepare a provider page, and move toward verified publication.</p>
        </div>
        <div className="cta-row">
          <Link className="primary-button" to={DISCOVER_ROUTES.registerDoctor.path}>Register as Doctor</Link>
          <Link className="secondary-button" to={DISCOVER_ROUTES.registerClinic.path}>Register a Clinic</Link>
          <Link className="secondary-button" to={DISCOVER_ROUTES.registerHospital.path}>Register a Hospital</Link>
        </div>
      </section>

      <section className="page-section">
        <div className="section-heading">
          <span className="eyebrow">Product family</span>
          <h2>One Jeevanam ecosystem, three focused applications.</h2>
        </div>
        <div className="product-grid">
          <article>
            <strong>Jeevanam Discover</strong>
            <p>Public search, provider pages, registration entry, product information, and booking initiation.</p>
          </article>
          <article>
            <strong>Jeevanam Care</strong>
            <p>Private personal care access after authentication in the dedicated Care application.</p>
          </article>
          <article>
            <strong>Jeevanam Healthcare</strong>
            <p>Clinic and hospital operations, administration, platform mode, and commercial management.</p>
          </article>
        </div>
      </section>

      <section className="final-cta">
        <h2>Start with the right next step.</h2>
        <div className="cta-row">
          <Link className="primary-button" to={`${DISCOVER_ROUTES.home.path}#find-care`}>Find Care</Link>
          <Link className="secondary-button" to={DISCOVER_ROUTES.listPractice.path}>List Your Practice</Link>
          <Link className="text-button" to={DISCOVER_ROUTES.contact.path}>Book Demo</Link>
        </div>
      </section>
    </>
  );
}

export function PublicDoctorsPage() {
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
  const visibleDoctors = useMemo(
    () => filterAndSortDoctorResults(doctors.data.items, filters.query, filters.city || PUBLIC_DEFAULT_LOCATION),
    [doctors.data.items, filters.city, filters.query],
  );

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Doctor directory</span>
        <h1>Browse public doctor profiles.</h1>
        <p>Doctor cards show public-safe details: speciality, experience, clinic context, fee when published, and next availability summary.</p>
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
          filters.submit(DISCOVER_ROUTES.doctors.path, { speciality, clinic });
        }}
      />
      <DirectoryState
        loading={doctors.loading}
        error={doctors.error}
        empty={visibleDoctors.length === 0}
        emptyMessage={discoveryEmptyMessage({
          query: filters.query || filters.city || filters.area,
          selectedLocation: filters.city,
          defaultMessage: noPublicProfilesMessage,
        })}
      >
        <div className="public-directory-grid">
          {visibleDoctors.map((doctor) => <DoctorCard key={doctor.doctorSlug} doctor={doctor} />)}
        </div>
        <PaginationBar
          page={doctors.data.page}
          totalPages={doctors.data.totalPages}
          onPageChange={(nextPage) => filters.changePage(DISCOVER_ROUTES.doctors.path, nextPage, { speciality, clinic })}
        />
      </DirectoryState>
    </section>
  );
}

export function PublicDoctorDetailPage() {
  const { doctorSlug = "" } = useParams();
  const detail = usePublicResource<PublicDoctorDetailResponse | null>(`/api/public/doctors/${doctorSlug}`, {}, null);

  return (
    <section className="page-section">
      <DirectoryState loading={detail.loading} error={detail.error} empty={!detail.data} emptyMessage="This doctor profile is not available for public discovery.">
        {detail.data ? (
          <div className="public-detail-shell">
            <article className="result-panel public-detail-hero">
              <div className="directory-card-top">
                <div className="directory-avatar directory-avatar-large" aria-hidden="true">
                  {detail.data.photoUrl ? <img src={detail.data.photoUrl} alt="" /> : <span>{initials(detail.data.doctorDisplayName)}</span>}
                </div>
                <div className="directory-card-heading">
                  <strong>{detail.data.doctorDisplayName}</strong>
                  <span>{detail.data.specialities.join(", ") || "General consultation"}</span>
                  <p>{detail.data.qualification ?? "Qualification shared by provider onboarding"} · {formatExperience(detail.data.yearsOfExperience)}</p>
                </div>
              </div>
              <div className="directory-badge-row">
                {detail.data.availableToday ? <span className="status-pill">Available today</span> : null}
                {detail.data.languages.length ? <span className="chip">Languages: {detail.data.languages.join(", ")}</span> : null}
              </div>
              <div className="directory-action-row">
                <a
                  className="primary-button"
                  href={careBookingUrl({
                    doctorId: detail.data.publicDoctorId,
                    ...(detail.data.clinics.length === 1 ? { clinicSlug: detail.data.clinics[0].clinicSlug } : {}),
                  })}
                >
                  Start booking
                </a>
                <Link className="secondary-button" to={DISCOVER_ROUTES.doctors.path}>
                  Back to doctors
                </Link>
              </div>
            </article>
            <div className="public-detail-grid">
              <article className="result-panel">
                <div className="panel-heading"><h2>Clinic</h2></div>
                <div className="subcard-list">
                  {detail.data.clinics.map((clinic) => (
                    <Link key={clinic.clinicSlug} className="subcard" to={`/clinics/${clinic.clinicSlug}`}>
                      <strong>{clinic.clinicDisplayName}</strong>
                      <span>{clinic.area ?? clinic.city ?? "Clinic profile"}{clinic.area && clinic.city ? ` · ${clinic.city}` : ""}</span>
                    </Link>
                  ))}
                </div>
              </article>
              <article className="result-panel">
                <div className="panel-heading"><h2>Availability</h2></div>
                <div className="detail-list">
                  <div>
                    <strong>Available days</strong>
                    <span>{detail.data.availableDays.join(", ") || "Provider shares availability after review"}</span>
                  </div>
                </div>
                <div className="subcard-list">
                  {detail.data.nextAvailableSlots.length ? (
                    detail.data.nextAvailableSlots.map((slot) => (
                      <div key={slot} className="subcard">
                        <strong>Next slot</strong>
                        <span>{slot}</span>
                      </div>
                    ))
                  ) : (
                    <div className="state-card compact">Next available slots will appear when the clinic publishes public-safe availability.</div>
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

export function PublicClinicsPage() {
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
  const visibleClinics = useMemo(
    () => filterAndSortClinicResults(clinics.data.items, filters.query, filters.city || PUBLIC_DEFAULT_LOCATION),
    [clinics.data.items, filters.city, filters.query],
  );

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Clinic directory</span>
        <h1>Browse public clinic profiles.</h1>
        <p>Public clinic cards show location, doctor count, visible specialities, and public appointment entry points.</p>
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
          filters.submit(DISCOVER_ROUTES.clinics.path, { speciality });
        }}
      />
      <DirectoryState
        loading={clinics.loading}
        error={clinics.error}
        empty={visibleClinics.length === 0}
        emptyMessage={discoveryEmptyMessage({
          query: filters.query || filters.city || filters.area,
          selectedLocation: filters.city,
          defaultMessage: noPublicProfilesMessage,
        })}
      >
        <div className="public-directory-grid">
          {visibleClinics.map((clinic) => <ClinicCard key={clinic.clinicSlug} clinic={clinic} />)}
        </div>
        <PaginationBar
          page={clinics.data.page}
          totalPages={clinics.data.totalPages}
          onPageChange={(nextPage) => filters.changePage(DISCOVER_ROUTES.clinics.path, nextPage, { speciality })}
        />
      </DirectoryState>
    </section>
  );
}

export function PublicClinicDetailPage() {
  const { clinicSlug = "" } = useParams();
  const detail = usePublicResource<PublicClinicDetailResponse | null>(`/api/public/clinics/${clinicSlug}`, {}, null);

  return (
    <section className="page-section">
      <DirectoryState loading={detail.loading} error={detail.error} empty={!detail.data} emptyMessage="This clinic profile is not available for public discovery.">
        {detail.data ? (
          <div className="public-detail-shell">
            <article className="result-panel public-detail-hero">
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
                  <Link key={speciality} className="chip" to={`/specialities/${slugify(speciality)}`}>{speciality}</Link>
                ))}
              </div>
              <div className="directory-action-row">
                <a className="primary-button" href={careBookingUrl({ clinicSlug: detail.data.clinicSlug })}>Start booking</a>
                <Link className="secondary-button" to={DISCOVER_ROUTES.clinics.path}>Back to clinics</Link>
              </div>
            </article>
            <div className="public-detail-grid">
              <article className="result-panel">
                <div className="panel-heading"><h2>Timings</h2></div>
                <div className="subcard-list">
                  {detail.data.timings.length ? (
                    detail.data.timings.map((timing) => (
                      <div key={timing} className="subcard">
                        <strong>{timing}</strong>
                        <span>Published from visible doctor schedules only.</span>
                      </div>
                    ))
                  ) : (
                    <div className="state-card compact">Clinic timings will appear when visible doctor schedules are published.</div>
                  )}
                </div>
              </article>
              <article className="result-panel">
                <div className="panel-heading"><h2>Doctors</h2></div>
                <div className="public-card-stack">
                  {detail.data.doctors.map((doctor) => <DoctorCard key={doctor.doctorSlug} doctor={doctor} />)}
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
    { q: filters.searchParams.get("q"), city: filters.searchParams.get("city") },
    [],
  );
  const visibleSpecialities = useMemo(() => filterAndSortSpecialities(specialities.data, filters.query), [filters.query, specialities.data]);

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Specialities</span>
        <h1>Explore specialities across public providers.</h1>
        <p>Use speciality pages to narrow down visible doctors and clinics that match the care you need.</p>
      </div>
      <form className="toolbar-card public-toolbar-card" onSubmit={(event) => { event.preventDefault(); filters.submit(DISCOVER_ROUTES.specialities.path); }}>
        <label className="toolbar-field">
          <span>Speciality</span>
          <input value={filters.query} onChange={(event) => filters.setQuery(event.target.value)} placeholder="Dermatology, pediatrics, cardiology" />
        </label>
        <label className="toolbar-field">
          <span>City</span>
          <input value={filters.city} onChange={(event) => filters.setCity(event.target.value)} placeholder="Filter by city" />
        </label>
        <button className="primary-button" type="submit">Search specialities</button>
      </form>
      <DirectoryState
        loading={specialities.loading}
        error={specialities.error}
        empty={visibleSpecialities.length === 0}
        emptyMessage={discoveryEmptyMessage({
          query: filters.query || filters.city,
          selectedLocation: filters.city,
          defaultMessage: noPublicProfilesMessage,
        })}
      >
        <div className="public-directory-grid speciality-directory-grid">
          {visibleSpecialities.map((speciality) => (
            <article key={speciality.specialitySlug} className="public-directory-card feature-card speciality-card">
              <strong>{speciality.speciality}</strong>
              <p>{speciality.doctorsCount} doctor{speciality.doctorsCount === 1 ? "" : "s"} across {speciality.clinicsCount} clinic{speciality.clinicsCount === 1 ? "" : "s"}.</p>
              <span>{speciality.doctorsCount > 0 ? "Search and book from this speciality." : "Public profile details will appear once available."}</span>
              <div className="directory-action-row">
                <Link className="secondary-button" to={`/specialities/${speciality.specialitySlug}`}>Search doctors</Link>
                <a className="text-button" href={careBookingUrl({ speciality: speciality.specialitySlug })}>Start booking</a>
              </div>
            </article>
          ))}
        </div>
      </DirectoryState>
    </section>
  );
}

export function PublicSpecialityDetailPage() {
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
  const visibleDoctors = useMemo(
    () => filterAndSortDoctorResults(detail.data?.doctors.items ?? [], filters.query, filters.city || PUBLIC_DEFAULT_LOCATION),
    [detail.data?.doctors.items, filters.city, filters.query],
  );

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Speciality detail</span>
        <h1>{detail.data?.speciality ?? "Speciality"}</h1>
        <p>Browse public doctor profiles for this speciality, then continue to Jeevanam Care when you are ready to book.</p>
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
        empty={!detail.data || visibleDoctors.length === 0}
        emptyMessage={discoveryEmptyMessage({
          query: filters.query || filters.city || filters.area,
          selectedLocation: filters.city,
          defaultMessage: "No public doctors matched this speciality filter.",
        })}
      >
        {detail.data ? (
          <>
            <div className="speciality-summary-card">
              <strong>{detail.data.speciality}</strong>
              <p>{visibleDoctors.length} doctor{visibleDoctors.length === 1 ? "" : "s"} across visible clinics.</p>
              <span>Search by doctor, clinic, city, or symptom to narrow the list.</span>
            </div>
            <div className="public-directory-grid">
              {visibleDoctors.map((doctor) => <DoctorCard key={doctor.doctorSlug} doctor={doctor} />)}
            </div>
            <PaginationBar page={detail.data.doctors.page} totalPages={detail.data.doctors.totalPages} onPageChange={(nextPage) => filters.changePage(`/specialities/${specialitySlug}`, nextPage)} />
          </>
        ) : null}
      </DirectoryState>
    </section>
  );
}
