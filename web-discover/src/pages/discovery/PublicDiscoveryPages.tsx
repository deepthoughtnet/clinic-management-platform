import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type {
  PublicClinicDetailResponse,
  PublicClinicSummaryResponse,
  PublicDoctorDetailResponse,
  PublicDoctorSummaryResponse,
  PublicHospitalDetailResponse,
  PublicHospitalSummaryResponse,
  PublicPageResponse,
  PublicSpecialityDetailResponse,
  PublicSpecialitySummaryResponse,
} from "../../api/publicCatalog";
import { fetchPublicJson } from "../../api/publicCatalog";
import { discoverConfig } from "../../config";
import {
  ClinicCard,
  DirectoryState,
  DoctorCard,
  InlineDirectoryState,
  HospitalCard,
  PaginationBar,
  QueryToolbar,
  careBookingUrl,
  emptyClinicsPage,
  emptyDoctorsPage,
  formatExperience,
  initials,
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
import { DISCOVER_DETAIL_PATHS, DISCOVER_ROUTES } from "../../routes";
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

const TRUST_SIGNALS = [
  {
    title: "Provider Information",
    body: "Specialities, experience and locations.",
    icon: "◎",
  },
  {
    title: "Easy Booking",
    body: "Search and book in fewer steps.",
    icon: "↗",
  },
  {
    title: "Your Care Workspace",
    body: "Appointments, reports and bills.",
    icon: "＋",
  },
  {
    title: "Clinics & Hospitals",
    body: "Browse trusted healthcare providers.",
    icon: "⌂",
  },
] as const;

const HEALTHCARE_SERVICES = [
  { title: "Doctor consultations", body: "Find doctors by speciality, clinic and location.", to: DISCOVER_ROUTES.doctors.path, state: "Explore →" },
  { title: "Clinic appointments", body: "Browse clinics and start appointment booking.", to: DISCOVER_ROUTES.clinics.path, state: "Explore →" },
  { title: "Hospital discovery", body: "Explore hospital profiles, departments and facilities.", to: DISCOVER_ROUTES.hospitals.path, state: "Explore →" },
  { title: "Speciality search", body: "Start with the medical speciality you need.", to: DISCOVER_ROUTES.specialities.path, state: "Explore →" },
] as const;

const WHY_JEEVANAM = [
  {
    icon: "◎",
    title: "Find care confidently",
    body: "Verified public healthcare information.",
  },
  {
    icon: "↗",
    title: "Choose with confidence",
    body: "Compare providers and services.",
  },
  {
    icon: "＋",
    title: "Continue your journey",
    body: "Manage appointments inside Care.",
  },
  {
    icon: "⌂",
    title: "Connected healthcare",
    body: "Participating clinics stay connected.",
  },
] as const;

function homepageParams(selectedLocation: string, size: number) {
  return {
    city: selectedLocation === PUBLIC_CURRENT_LOCATION_LABEL ? PUBLIC_DEFAULT_LOCATION : selectedLocation,
    page: 0,
    size,
  };
}

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
  const displayLocation = selectedLocation;
  const searchableLocation = displayLocation === PUBLIC_CURRENT_LOCATION_LABEL ? PUBLIC_DEFAULT_LOCATION : displayLocation;
  const doctors = usePublicResource<PublicPageResponse<PublicDoctorSummaryResponse>>(
    "/api/public/doctors",
    homepageParams(searchableLocation, 4),
    { ...emptyDoctorsPage, size: 4 },
  );
  const clinics = usePublicResource<PublicPageResponse<PublicClinicSummaryResponse>>(
    "/api/public/clinics",
    homepageParams(searchableLocation, 3),
    { ...emptyClinicsPage, size: 3 },
  );
  const specialities = usePublicResource<PublicSpecialitySummaryResponse[]>(
    "/api/public/specialities",
    { city: searchableLocation },
    [],
  );
  const homeDoctors = useMemo(
    () => filterAndSortDoctorResults(doctors.data.items, "", searchableLocation),
    [doctors.data.items, searchableLocation],
  );
  const homeClinics = useMemo(
    () => filterAndSortClinicResults(clinics.data.items, "", searchableLocation),
    [clinics.data.items, searchableLocation],
  );
  const homeSpecialities = useMemo(
    () => filterAndSortSpecialities(specialities.data, ""),
    [specialities.data],
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
            <span className="eyebrow">Jeevanam Discover</span>
            <h1>Find trusted doctors, clinics and hospitals near you.</h1>
            <p>Search healthcare providers, compare services and book appointments with confidence.</p>
            <form id="find-care" className="hero-search-panel" aria-label="Discover care search" onSubmit={submitHeroSearch}>
              <label className="hero-search-field hero-search-query">
                <span className="visually-hidden">Search</span>
                <input
                  value={filters.query}
                  onChange={(event) => filters.setQuery(event.target.value)}
                  placeholder="Search doctors, specialities, clinics or treatments"
                  autoComplete="off"
                  aria-label="Search doctors, specialities, clinics or treatments"
                />
              </label>
              <label className="hero-search-field">
                <span className="visually-hidden">Location</span>
                <button className="location-select-button" type="button" onClick={() => setLocationPickerOpen((current) => !current)} aria-label={`Change location, currently ${displayLocation}`}>
                  {displayLocation}
                </button>
              </label>
              <button className="primary-button hero-search-button" type="submit" aria-label="Search healthcare providers">
                <span aria-hidden="true">⌕</span>
                Search
              </button>
              {locationPickerOpen ? (
                <div className="location-selector hero-location-selector" role="dialog" aria-label="Select location">
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
            </form>
            <div className="popular-searches" aria-label="Popular searches">
              <span>Popular searches</span>
              <div className="chip-row">
                {POPULAR_SEARCHES.slice(0, 5).map((item) => (
                  <button key={item} className="chip-button" type="button" onClick={() => applyPopularSearch(item)}>
                    {item}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="hero-visual" aria-label="Jeevanam Discover provider preview">
            <div className="hero-profile-card">
              <div className="hero-profile-header">
                <div className="hero-visual-avatar" aria-hidden="true">AS</div>
                <div>
                  <strong>Dr. Anjali Sharma</strong>
                  <span>General Physician</span>
                </div>
              </div>
              <div className="hero-profile-meta">
                <span>15 Years Experience</span>
                <span aria-label="Five star rating">★★★★★</span>
              </div>
              <div className="hero-profile-footer">
                <span className="hero-availability-pill">Available Today</span>
                <span className="primary-button hero-consult-button">Book Consultation</span>
              </div>
            </div>
            <div className="hero-clinic-card">
              <span className="mini-avatar clinic" aria-hidden="true">CL</span>
              <div>
                <strong>Sunrise Family Clinic</strong>
                <small>Open Today · Appointments Available</small>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="page-section">
        <div className="trust-grid" aria-label="Jeevanam Discover trust indicators">
          {TRUST_SIGNALS.map((item) => (
            <article className="trust-card" key={item.title}>
              <span aria-hidden="true">{item.icon}</span>
              <strong>{item.title}</strong>
              <p>{item.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="page-section">
        <div className="section-heading section-heading-row">
          <div>
            <span className="eyebrow">Specialities</span>
            <h2>Browse by speciality</h2>
            <p>Find care based on the medical speciality you need.</p>
          </div>
          <Link className="text-button" to={DISCOVER_ROUTES.specialities.path}>View all specialities</Link>
        </div>
        <InlineDirectoryState
          loading={specialities.loading}
          error={specialities.error}
          empty={homeSpecialities.length === 0}
          emptyIcon="＋"
          emptyTitle="Specialities are being prepared"
          emptyMessage="Published provider specialities will appear here as the directory grows."
          primaryAction="Browse clinics"
          primaryTo={DISCOVER_ROUTES.clinics.path}
          secondaryAction="List your practice"
          secondaryTo={DISCOVER_ROUTES.listPractice.path}
        />
        {homeSpecialities.length ? (
          <div className="speciality-card-grid">
            {homeSpecialities.slice(0, 8).map((speciality) => (
              <Link className="speciality-card" key={speciality.specialitySlug} to={DISCOVER_DETAIL_PATHS.speciality(speciality.specialitySlug)}>
                <span className="speciality-icon" aria-hidden="true">＋</span>
                <strong>{speciality.speciality}</strong>
                <span>
                  {speciality.doctorsCount || speciality.clinicsCount
                    ? `${speciality.doctorsCount} doctors · ${speciality.clinicsCount} clinics`
                    : "Explore providers"}
                </span>
              </Link>
            ))}
          </div>
        ) : null}
      </section>

      <section className="page-section">
        <div className="section-heading section-heading-row">
          <div>
            <span className="eyebrow">Doctors</span>
            <h2>Doctors you can explore</h2>
            <p>Review experience, specialities and available booking options.</p>
          </div>
          <Link className="text-button" to={DISCOVER_ROUTES.doctors.path}>View all doctors</Link>
        </div>
        <InlineDirectoryState
          loading={doctors.loading}
          error={doctors.error}
          empty={homeDoctors.length === 0}
          emptyIcon="DR"
          emptyTitle={`No doctors found for ${searchableLocation}`}
          emptyMessage="Try another location or broaden your search."
          primaryAction="Change location"
          primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
          secondaryAction="View clinics"
          secondaryTo={DISCOVER_ROUTES.clinics.path}
        />
        {homeDoctors.length ? (
          <div className="homepage-doctor-grid">
            {homeDoctors.slice(0, 4).map((doctor) => <DoctorCard key={doctor.doctorSlug} doctor={doctor} />)}
          </div>
        ) : null}
      </section>

      <section className="page-section surface-band">
        <div className="section-heading section-heading-row">
          <div>
            <span className="eyebrow">Clinics</span>
            <h2>Clinics near you</h2>
            <p>{displayLocation ? "Explore clinics, services and doctors available in your selected location." : "Explore clinics and the services they offer."}</p>
          </div>
          <Link className="text-button" to={DISCOVER_ROUTES.clinics.path}>View all clinics</Link>
        </div>
        <InlineDirectoryState
          loading={clinics.loading}
          error={clinics.error}
          empty={homeClinics.length === 0}
          emptyIcon="CL"
          emptyTitle={`No clinics found for ${searchableLocation}`}
          emptyMessage="Try another location or browse doctors by speciality."
          primaryAction="Change location"
          primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
          secondaryAction="Browse specialities"
          secondaryTo={DISCOVER_ROUTES.specialities.path}
        />
        {homeClinics.length ? (
          <div className="homepage-clinic-grid">
            {homeClinics.slice(0, 3).map((clinic) => <ClinicCard key={clinic.clinicSlug} clinic={clinic} />)}
          </div>
        ) : null}
      </section>

      <section className="page-section">
        <div className="section-heading">
          <span className="eyebrow">Services</span>
          <h2>Explore healthcare services</h2>
          <p>Start with the care paths currently available in Jeevanam Discover.</p>
        </div>
        <div className="service-grid">
          {HEALTHCARE_SERVICES.map((service) => (
            service.state === "Explore →" ? (
              <Link className="service-card" to={service.to} key={service.title}>
                <strong>{service.title}</strong>
                <span>{service.body}</span>
                <small>{service.state}</small>
              </Link>
            ) : (
              <article className="service-card service-card-disabled" key={service.title} aria-label={`${service.title}: ${service.state}`}>
                <strong>{service.title}</strong>
                <span>{service.body}</span>
                <small>{service.state}</small>
              </article>
            )
          ))}
        </div>
      </section>

      <section className="page-section">
        <div className="section-heading">
          <span className="eyebrow">Why Jeevanam</span>
          <h2>A simpler way to find and manage care</h2>
        </div>
        <div className="feature-grid">
          {WHY_JEEVANAM.map((feature) => (
            <article className="feature-card" key={feature.title}>
              <span aria-hidden="true">{feature.icon}</span>
              <strong>{feature.title}</strong>
              <p>{feature.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="provider-band">
        <div>
          <span className="eyebrow">For providers</span>
          <h2>Grow your practice with Jeevanam</h2>
          <p>Create a public profile, present your services and connect appointment discovery with your clinic operations.</p>
          <ul className="provider-benefits">
            <li>Publish your practice profile</li>
            <li>Present doctors and services</li>
            <li>Receive appointment enquiries</li>
          </ul>
        </div>
        <div className="cta-row">
          <Link className="primary-button light-button" to={DISCOVER_ROUTES.listPractice.path}>List your practice</Link>
          <Link className="secondary-button light-outline-button" to={DISCOVER_ROUTES.healthcare.path}>Explore Jeevanam Healthcare</Link>
        </div>
      </section>

      <section className="page-section">
        <div className="section-heading">
          <span className="eyebrow">Ecosystem</span>
          <h2>One connected healthcare experience</h2>
        </div>
        <div className="ecosystem-grid">
          <article className="ecosystem-card ecosystem-discover">
            <span className="ecosystem-icon" aria-hidden="true">◎</span>
            <strong>Jeevanam Discover</strong>
            <p>Find doctors, clinics, hospitals and appointment options.</p>
            <Link className="text-button" to={`${DISCOVER_ROUTES.home.path}#find-care`}>Explore care →</Link>
          </article>
          <article className="ecosystem-card ecosystem-care">
            <span className="ecosystem-icon" aria-hidden="true">＋</span>
            <strong>Jeevanam Care</strong>
            <p>Manage appointments, prescriptions, reports, bills and your care journey.</p>
            <a className="text-button" href={discoverConfig.careAppUrl}>Open Care →</a>
          </article>
          <article className="ecosystem-card ecosystem-healthcare">
            <span className="ecosystem-icon" aria-hidden="true">⌂</span>
            <strong>Jeevanam Healthcare</strong>
            <p>Run connected clinical and administrative workflows for clinics and hospitals.</p>
            <a className="text-button" href={discoverConfig.healthcareAppUrl}>Clinic / Hospital Login →</a>
          </article>
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
      <div className="section-heading compact-page-hero">
        <span className="eyebrow">Doctor directory</span>
        <h1>Find doctors by speciality, clinic and location</h1>
        <p>Compare doctor profiles and continue to booking when you find the right care option.</p>
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
        emptyIcon="DR"
        emptyTitle={`No doctors found for ${filters.city || PUBLIC_DEFAULT_LOCATION}`}
        emptyMessage="Try another location or broaden your search."
        primaryAction="Change search"
        primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
        secondaryAction="View clinics"
        secondaryTo={DISCOVER_ROUTES.clinics.path}
        errorTitle="We could not load doctors right now."
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
  const location = useLocation();
  const detail = usePublicResource<PublicDoctorDetailResponse | null>(`/api/public/doctors/${doctorSlug}`, {}, null);

  if (detail.data?.publicPath && detail.data.publicPath !== location.pathname) {
    return <Navigate replace to={`${detail.data.publicPath}${location.search}`} />;
  }

  return (
    <section className="page-section">
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data}
        emptyIcon="DR"
        emptyTitle="Doctor profile unavailable"
        emptyMessage="This doctor profile is not available in Discover right now."
        primaryAction="Browse doctors"
        primaryTo={DISCOVER_ROUTES.doctors.path}
        secondaryAction="View clinics"
        secondaryTo={DISCOVER_ROUTES.clinics.path}
      >
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
                    <Link key={clinic.clinicSlug} className="subcard" to={DISCOVER_DETAIL_PATHS.clinic(clinic.clinicSlug)}>
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
                    <div className="state-card compact">Availability will appear when this provider shares appointment options.</div>
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
      <div className="section-heading compact-page-hero">
        <span className="eyebrow">Clinic directory</span>
        <h1>Find clinics and appointment options near you</h1>
        <p>Explore clinic locations, specialities and doctor teams before you book.</p>
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
        emptyIcon="CL"
        emptyTitle={`No clinics found for ${filters.city || PUBLIC_DEFAULT_LOCATION}`}
        emptyMessage="Try another location or browse doctors by speciality."
        primaryAction="Change search"
        primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
        secondaryAction="Browse specialities"
        secondaryTo={DISCOVER_ROUTES.specialities.path}
        errorTitle="We could not load clinics right now."
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
  const location = useLocation();
  const detail = usePublicResource<PublicClinicDetailResponse | null>(`/api/public/clinics/${clinicSlug}`, {}, null);

  if (detail.data?.publicPath && detail.data.publicPath !== location.pathname) {
    return <Navigate replace to={`${detail.data.publicPath}${location.search}`} />;
  }

  return (
    <section className="page-section">
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data}
        emptyIcon="CL"
        emptyTitle="Clinic profile unavailable"
        emptyMessage="This clinic profile is not available in Discover right now."
        primaryAction="Browse clinics"
        primaryTo={DISCOVER_ROUTES.clinics.path}
        secondaryAction="Find doctors"
        secondaryTo={DISCOVER_ROUTES.doctors.path}
      >
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
                  <Link key={speciality} className="chip" to={DISCOVER_DETAIL_PATHS.speciality(slugify(speciality))}>{speciality}</Link>
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
                        <span>Shared by the clinic for patient planning.</span>
                      </div>
                    ))
                  ) : (
                    <div className="state-card compact">Clinic timings will appear when this clinic shares appointment hours.</div>
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

export function PublicHospitalsPage() {
  const filters = useDirectoryFilters();
  const speciality = filters.searchParams.get("speciality");
  const hospitals = usePublicResource<PublicPageResponse<PublicHospitalSummaryResponse>>(
    "/api/public/hospitals",
    {
      q: filters.searchParams.get("q"),
      city: filters.searchParams.get("city"),
      area: filters.searchParams.get("area"),
      speciality,
      page: filters.page,
      size: filters.size,
    },
    { items: [], page: 0, size: filters.size, totalItems: 0, totalPages: 0 },
  );
  const visibleHospitals = useMemo(
    () => [...hospitals.data.items].sort((left, right) => normalizeDiscoveryText(left.hospitalDisplayName).localeCompare(normalizeDiscoveryText(right.hospitalDisplayName))),
    [hospitals.data.items],
  );

  return (
    <section className="page-section">
      <div className="section-heading compact-page-hero">
        <span className="eyebrow">Hospital directory</span>
        <h1>Find hospitals and specialty care</h1>
        <p>Explore hospital profiles, departments and facilities before you continue to booking.</p>
      </div>
      <QueryToolbar
        actionLabel="Search hospitals"
        query={filters.query}
        setQuery={filters.setQuery}
        city={filters.city}
        setCity={filters.setCity}
        area={filters.area}
        setArea={filters.setArea}
        onSubmit={(event) => {
          event.preventDefault();
          filters.submit(DISCOVER_ROUTES.hospitals.path, { speciality });
        }}
      />
      <DirectoryState
        loading={hospitals.loading}
        error={hospitals.error}
        empty={visibleHospitals.length === 0}
        emptyIcon="H"
        emptyTitle={`No hospitals found for ${filters.city || PUBLIC_DEFAULT_LOCATION}`}
        emptyMessage="Try another location or search by department or speciality."
        primaryAction="Browse clinics"
        primaryTo={DISCOVER_ROUTES.clinics.path}
        secondaryAction="Browse specialities"
        secondaryTo={DISCOVER_ROUTES.specialities.path}
        errorTitle="We could not load hospitals right now."
      >
        <div className="public-directory-grid">
          {visibleHospitals.map((hospital) => <HospitalCard key={hospital.hospitalSlug} hospital={hospital} />)}
        </div>
        <PaginationBar
          page={hospitals.data.page}
          totalPages={hospitals.data.totalPages}
          onPageChange={(nextPage) => filters.changePage(DISCOVER_ROUTES.hospitals.path, nextPage, { speciality })}
        />
      </DirectoryState>
    </section>
  );
}

export function PublicHospitalDetailPage() {
  const { hospitalSlug = "" } = useParams();
  const location = useLocation();
  const detail = usePublicResource<PublicHospitalDetailResponse | null>(`/api/public/hospitals/${hospitalSlug}`, {}, null);

  if (detail.data?.publicPath && detail.data.publicPath !== location.pathname) {
    return <Navigate replace to={`${detail.data.publicPath}${location.search}`} />;
  }

  return (
    <section className="page-section">
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data}
        emptyIcon="H"
        emptyTitle="Hospital profile unavailable"
        emptyMessage="This hospital profile is not available in Discover right now."
        primaryAction="Browse hospitals"
        primaryTo={DISCOVER_ROUTES.hospitals.path}
        secondaryAction="Find clinics"
        secondaryTo={DISCOVER_ROUTES.clinics.path}
      >
        {detail.data ? (
          <div className="public-detail-shell">
            <article className="result-panel public-detail-hero">
              <div className="directory-card-top">
                <div className="directory-avatar directory-avatar-large" aria-hidden="true">
                  {detail.data.logoUrl ? <img src={detail.data.logoUrl} alt="" /> : <span>{initials(detail.data.hospitalDisplayName)}</span>}
                </div>
                <div className="directory-card-heading">
                  <strong>{detail.data.hospitalDisplayName}</strong>
                  <span>{detail.data.area ?? detail.data.city ?? "Hospital profile"}</span>
                  <p>{detail.data.address ?? "Hospital address shared after onboarding"}</p>
                </div>
              </div>
              <div className="directory-badge-row">
                {detail.data.emergencyAvailable ? <span className="status-pill">Emergency available</span> : null}
                {detail.data.departments.slice(0, 4).map((department) => (
                  <span key={department} className="chip">{department}</span>
                ))}
              </div>
              <div className="directory-action-row">
                <a className="primary-button" href={careBookingUrl({ hospitalSlug: detail.data.hospitalSlug })}>Start booking</a>
                <Link className="secondary-button" to={DISCOVER_ROUTES.hospitals.path}>Back to hospitals</Link>
              </div>
            </article>
            <div className="public-detail-grid">
              <article className="result-panel">
                <div className="panel-heading"><h2>Facilities</h2></div>
                <div className="subcard-list">
                  {detail.data.facilities.length ? detail.data.facilities.map((facility) => (
                    <div key={facility} className="subcard">
                      <strong>{facility}</strong>
                      <span>Shared by the hospital for public discovery.</span>
                    </div>
                  )) : <div className="state-card compact">Hospital facilities will appear when the profile is published.</div>}
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
      <div className="section-heading compact-page-hero">
        <span className="eyebrow">Specialities</span>
        <h1>Explore specialities</h1>
        <p>Browse healthcare specialities and find relevant doctors and clinics.</p>
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
        emptyIcon="＋"
        emptyTitle="Specialities are being prepared"
        emptyMessage="Published provider specialities will appear here as the directory grows."
        primaryAction="Browse clinics"
        primaryTo={DISCOVER_ROUTES.clinics.path}
        secondaryAction="List your practice"
        secondaryTo={DISCOVER_ROUTES.listPractice.path}
        errorTitle="We could not load specialities right now."
      >
        <div className="public-directory-grid speciality-directory-grid">
          {visibleSpecialities.map((speciality) => (
            <article key={speciality.specialitySlug} className="public-directory-card feature-card speciality-card">
              <strong>{speciality.speciality}</strong>
              <p>{speciality.doctorsCount} doctor{speciality.doctorsCount === 1 ? "" : "s"} across {speciality.clinicsCount} clinic{speciality.clinicsCount === 1 ? "" : "s"}.</p>
              <span>{speciality.doctorsCount > 0 ? "Search and book from this speciality." : "Provider details will appear once available."}</span>
              <div className="directory-action-row">
                <Link className="secondary-button" to={DISCOVER_DETAIL_PATHS.speciality(speciality.specialitySlug)}>Search doctors</Link>
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
        <p>Browse doctor profiles for this speciality, then continue to Jeevanam Care when you are ready to book.</p>
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
          filters.submit(DISCOVER_DETAIL_PATHS.speciality(specialitySlug));
        }}
      />
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data || visibleDoctors.length === 0}
        emptyMessage={discoveryEmptyMessage({
          query: filters.query || filters.city || filters.area,
          selectedLocation: filters.city,
          defaultMessage: "No doctors matched this speciality filter.",
        })}
        emptyIcon="DR"
        emptyTitle="No doctors found for this speciality"
        primaryAction="Browse all specialities"
        primaryTo={DISCOVER_ROUTES.specialities.path}
        secondaryAction="View clinics"
        secondaryTo={DISCOVER_ROUTES.clinics.path}
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
            <PaginationBar page={detail.data.doctors.page} totalPages={detail.data.doctors.totalPages} onPageChange={(nextPage) => filters.changePage(DISCOVER_DETAIL_PATHS.speciality(specialitySlug), nextPage)} />
          </>
        ) : null}
      </DirectoryState>
    </section>
  );
}
