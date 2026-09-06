import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type {
  PublicClinicDetailResponse,
  PublicClinicSummaryResponse,
  PublicDoctorDetailResponse,
  PublicDoctorSummaryResponse,
  PublicHospitalDetailResponse,
  PublicHospitalSummaryResponse,
  PublicPageResponse,
  PublicSearchResponse,
  PublicSpecialitySummaryResponse,
} from "../../api/publicCatalog";
import {
  fetchPublicJson,
  isPublicCatalogNotFoundError,
  patientBookingPath,
} from "../../api/publicCatalog";
import {
  PUBLIC_PROFILE_STATUS,
  createPublicProfileLifecycle,
  type PublicProfileLifecycle,
  type PublicProfileState,
} from "./publicProfileLifecycle.js";
import {
  buildCareProfileBackState,
  hasCareProfileBackState,
  resolveCareProfileBackTarget,
  resolveCareProfileFallbackPath,
} from "./careProfileNavigation.js";
import type {
  PatientPortalAuthorizedClinicResponse,
  PatientPortalClinicResponse,
  PatientPortalDoctorResponse,
  PatientPortalPatientSession,
  PatientPortalSession,
} from "../../api/patientPortal";
import {
  fetchPatientPortalJson,
  postPatientPortalClinicSwitch,
  type PatientPortalClinicSwitchResponse,
} from "../../api/patientPortal";
import { careConfig } from "../../config";
import { usePublicLocation } from "../../context/publicLocation";
import { PatientPortalShell } from "./PatientPortalPages";
import {
  dedupeByExplicitKey,
  normalizeDiscoveryText,
  normalizePublicClinicDisplayName,
  resolveTelHref,
} from "./patientDiscoveryModel.js";

type FetchState<T> = {
  data: T;
  loading: boolean;
  error: string | null;
};

type DiscoveryTab = "all" | "doctors" | "clinics" | "hospitals" | "specialities" | "services";

type CareDoctorCard = {
  source: "care";
  explicitKey: string;
  publicDoctorId: string;
  doctorName: string;
  specialization: string | null;
  qualification: string | null;
  consultationRoom: string | null;
  yearsOfExperience: number | null;
  clinicName: string;
  clinicSlug: string | null;
  contactPhone: string | null;
};

type PublicDoctorCard = {
  source: "public";
  explicitKey: string;
  publicDoctorId: string;
  doctorSlug: string;
  doctorName: string;
  specialization: string | null;
  qualification: string | null;
  yearsOfExperience: number | null;
  clinicName: string;
  clinicSlug: string;
  area: string | null;
  city: string | null;
  contactPhone: string | null;
  bookingMode: string | null;
  bookingReference: string | null;
  availableToday: boolean;
  nextAvailableSlotSummary: string | null;
};

type CareClinicCard = {
  source: "care";
  explicitKey: string;
  tenantId: string;
  clinicName: string;
  displayName: string;
  address: string | null;
  city: string | null;
  phone: string | null;
  slug: string | null;
  selected: boolean;
  authorizationSource: string;
  active: boolean;
};

type PublicClinicCard = {
  source: "public";
  explicitKey: string;
  clinicSlug: string;
  clinicName: string;
  address: string | null;
  area: string | null;
  city: string | null;
  phone: string | null;
  bookingMode: string | null;
  specialities: string[];
  doctorsCount: number;
  availableToday: boolean;
};

type PublicHospitalCard = {
  source: "public";
  explicitKey: string;
  hospitalSlug: string;
  hospitalName: string;
  address: string | null;
  area: string | null;
  city: string | null;
  phone: string | null;
  bookingMode: string | null;
  specialities: string[];
  doctorsCount: number;
  availableToday: boolean;
};

type SpecialityCard = PublicSpecialitySummaryResponse & {
  explicitKey: string;
};

type PublicProfileFetchState<T> = PublicProfileState<T>;

function usePatientPortalResource<T>(
  session: PatientPortalPatientSession | null,
  path: string,
  initialValue: T,
  refreshKey = 0,
): FetchState<T> {
  const initialValueRef = useRef(initialValue);
  const [state, setState] = useState<FetchState<T>>({
    data: initialValue,
    loading: false,
    error: null,
  });

  useEffect(() => {
    initialValueRef.current = initialValue;
  }, [initialValue]);

  useEffect(() => {
    const abortController = new AbortController();
    if (!session) {
      setState({
        data: initialValueRef.current,
        loading: false,
        error: "Sign in with your patient session to view discovery data.",
      });
      return () => abortController.abort();
    }
    setState({
      data: initialValueRef.current,
      loading: true,
      error: null,
    });
    fetchPatientPortalJson<T>(path, session, abortController.signal)
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
          data: initialValueRef.current,
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load patient discovery data.",
        });
      });
    return () => abortController.abort();
  }, [path, refreshKey, session]);

  return state;
}

function usePublicSearch(query: string, city: string, area: string, refreshKey = 0) {
  const [state, setState] = useState<FetchState<PublicSearchResponse>>({
    data: {
      doctors: { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 },
      clinics: { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 },
      hospitals: { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 },
      specialities: [],
    },
    loading: false,
    error: null,
  });

  useEffect(() => {
    const abortController = new AbortController();
    setState((current) => ({
      data: current.data,
      loading: true,
      error: null,
    }));
    fetchPublicJson<PublicSearchResponse>(
      "/api/public/search",
      {
        q: query.trim() || null,
        city: city.trim() || null,
        area: area.trim() || null,
        page: 0,
        size: 12,
      },
      abortController.signal,
    )
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
          data: {
            doctors: { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 },
            clinics: { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 },
            hospitals: { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 },
            specialities: [],
          },
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load public discovery data.",
        });
      });
    return () => abortController.abort();
  }, [area, city, query, refreshKey]);

  return state;
}

function formatText(value: string | null | undefined) {
  return value?.trim() || "";
}

function buildDiscoveryLocationLabel(area: string | null | undefined, city: string | null | undefined) {
  const parts = [formatText(area), formatText(city)].filter(Boolean);
  return parts.length ? parts.join(" · ") : "Location available on profile";
}

function normalizeSearchQuery(value: string) {
  return normalizeDiscoveryText(value);
}

function matchesSearch(values: Array<string | number | null | undefined>, query: string) {
  const normalizedQuery = normalizeSearchQuery(query);
  if (!normalizedQuery) {
    return true;
  }
  return values.some((value) => normalizeDiscoveryText(value).includes(normalizedQuery));
}

function getDiscoveryTab(value: string | null | undefined, fallback: DiscoveryTab) {
  const normalized = value?.trim().toLowerCase();
  if (
    normalized === "all" ||
    normalized === "doctors" ||
    normalized === "clinics" ||
    normalized === "hospitals" ||
    normalized === "specialities" ||
    normalized === "services"
  ) {
    return normalized;
  }
  return fallback;
}

function discoveryTabLabel(tab: DiscoveryTab) {
  switch (tab) {
    case "doctors":
      return "Doctors";
    case "clinics":
      return "Clinics";
    case "hospitals":
      return "Hospitals";
    case "specialities":
      return "Specialities";
    case "services":
      return "Services";
    default:
      return "All";
  }
}

function normalizeBookingMode(value: string | null | undefined) {
  const normalized = value?.trim().toUpperCase() ?? "";
  if (normalized === "ONLINE_BOOKING" || normalized === "CALL_TO_BOOK" || normalized === "REQUEST_APPOINTMENT" || normalized === "NOT_AVAILABLE") {
    return normalized;
  }
  return null;
}

function bookingModeLabel(value: string | null | undefined) {
  switch (normalizeBookingMode(value)) {
    case "ONLINE_BOOKING":
      return "Book online";
    case "CALL_TO_BOOK":
      return "Call clinic";
    case "REQUEST_APPOINTMENT":
      return "Request appointment";
    case "NOT_AVAILABLE":
      return "Not available";
    default:
      return "Profile only";
  }
}

function bookingModeTone(value: string | null | undefined) {
  switch (normalizeBookingMode(value)) {
    case "ONLINE_BOOKING":
      return "is-online";
    case "CALL_TO_BOOK":
      return "is-call";
    case "REQUEST_APPOINTMENT":
      return "is-request";
    default:
      return "is-muted";
  }
}

function formatYears(years: number | null | undefined) {
  return typeof years === "number" && Number.isFinite(years) ? `${years} yrs exp` : null;
}

function careDoctorCard(doctor: PatientPortalDoctorResponse, clinic: PatientPortalClinicResponse | null): CareDoctorCard {
  return {
    source: "care",
    explicitKey: doctor.publicDoctorId,
    publicDoctorId: doctor.publicDoctorId,
    doctorName: doctor.doctorName,
    specialization: doctor.specialization,
    qualification: doctor.qualification,
    consultationRoom: doctor.consultationRoom,
    yearsOfExperience: doctor.yearsOfExperience,
    clinicName: clinic?.displayName ?? clinic?.clinicName ?? "My clinic",
    clinicSlug: clinic?.slug ?? null,
    contactPhone: clinic?.phone ?? null,
  };
}

function publicDoctorCard(doctor: PublicDoctorSummaryResponse): PublicDoctorCard {
  const clinicName =
    normalizePublicClinicDisplayName(doctor.clinicDisplayName)
    ?? "Published clinic profile";
  return {
    source: "public",
    explicitKey: doctor.publicDoctorId,
    publicDoctorId: doctor.publicDoctorId,
    doctorSlug: doctor.doctorSlug,
    doctorName: doctor.doctorDisplayName,
    specialization: doctor.speciality,
    qualification: null,
    yearsOfExperience: doctor.yearsOfExperience,
    clinicName,
    clinicSlug: doctor.clinicSlug,
    area: doctor.area,
    city: doctor.city,
    contactPhone: doctor.contactPhone ?? null,
    bookingMode: doctor.bookingMode ?? null,
    bookingReference: doctor.bookingReference ?? null,
    availableToday: doctor.availableToday,
    nextAvailableSlotSummary: doctor.nextAvailableSlotSummary,
  };
}

function careClinicCard(clinic: PatientPortalAuthorizedClinicResponse, selectedClinic: PatientPortalClinicResponse | null): CareClinicCard {
  return {
    source: "care",
    explicitKey: clinic.tenantCode,
    tenantId: clinic.tenantId,
    clinicName: clinic.clinicName,
    displayName: selectedClinic?.displayName ?? selectedClinic?.clinicName ?? clinic.clinicName,
    address: selectedClinic?.addressLine1 ?? selectedClinic?.addressLine2 ?? null,
    city: selectedClinic?.city ?? null,
    phone: selectedClinic?.phone ?? null,
    slug: selectedClinic?.slug ?? null,
    selected: clinic.selected,
    authorizationSource: clinic.authorizationSource,
    active: clinic.active,
  };
}

function publicClinicCard(clinic: PublicClinicSummaryResponse): PublicClinicCard {
  return {
    source: "public",
    explicitKey: clinic.clinicSlug,
    clinicSlug: clinic.clinicSlug,
    clinicName: clinic.clinicDisplayName,
    address: clinic.address,
    area: clinic.area,
    city: clinic.city,
    phone: clinic.contactPhone ?? null,
    bookingMode: clinic.bookingMode ?? null,
    specialities: clinic.specialities ?? [],
    doctorsCount: clinic.doctorsCount,
    availableToday: clinic.availableToday,
  };
}

function publicHospitalCard(hospital: PublicHospitalSummaryResponse): PublicHospitalCard {
  return {
    source: "public",
    explicitKey: hospital.hospitalSlug,
    hospitalSlug: hospital.hospitalSlug,
    hospitalName: hospital.hospitalDisplayName,
    address: hospital.address,
    area: hospital.area,
    city: hospital.city,
    phone: hospital.contactPhone ?? null,
    bookingMode: hospital.bookingMode ?? null,
    specialities: hospital.specialities ?? [],
    doctorsCount: hospital.doctorsCount,
    availableToday: hospital.availableToday,
  };
}

function specialtyCard(speciality: PublicSpecialitySummaryResponse): SpecialityCard {
  return {
    ...speciality,
    explicitKey: speciality.specialitySlug,
  };
}

function DiscoveryActionRow({
  primary,
  secondary,
  tertiary,
}: {
  primary?: ReactNode;
  secondary?: ReactNode;
  tertiary?: ReactNode;
}) {
  return (
    <div className="patient-action-row patient-discovery-action-row">
      {primary}
      {secondary}
      {tertiary}
    </div>
  );
}

function ResultHeader({
  title,
  caption,
}: {
  title: string;
  caption: string;
}) {
  return (
    <div className="patient-panel-heading">
      <h2>{title}</h2>
      <span className="panel-caption">{caption}</span>
    </div>
  );
}

function CareDoctorResultCard({
  doctor,
  clinic,
  session,
  currentPath,
}: {
  doctor: CareDoctorCard;
  clinic: PatientPortalClinicResponse | null;
  session: PatientPortalPatientSession;
  currentPath: string;
}) {
  const bookingPath = patientBookingPath(session, `/patient/book-appointment?doctorId=${encodeURIComponent(doctor.publicDoctorId)}${doctor.clinicSlug ? `&clinicSlug=${encodeURIComponent(doctor.clinicSlug)}` : ""}`);
  return (
    <article className="patient-record-card patient-discovery-card">
      <div className="record-card-top">
        <div>
          <strong>{doctor.doctorName}</strong>
          <span>{doctor.specialization ?? "General consultation"}</span>
        </div>
        <span className="status-pill status-pill--compact">Care network</span>
      </div>
      <div className="record-card-meta">
        <span>{doctor.qualification ?? clinic?.displayName ?? "My clinic"}</span>
        <span>{[doctor.consultationRoom, formatYears(doctor.yearsOfExperience)].filter(Boolean).join(" · ") || "Authorized clinic doctor"}</span>
      </div>
      <div className="record-card-meta">
        <span>{doctor.clinicName}</span>
        <span>{clinic?.city ?? "Authorized clinic"}</span>
      </div>
      <DiscoveryActionRow
        primary={(
          <Link className="primary-button" to={bookingPath}>
            Book online through Care
          </Link>
        )}
        secondary={clinic?.slug ? (
          <Link
            className="secondary-button"
            to={`/patient/clinics/${encodeURIComponent(clinic.slug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View clinic
          </Link>
        ) : null}
      />
    </article>
  );
}

function PublicDoctorResultCard({
  doctor,
  session,
  currentPath,
}: {
  doctor: PublicDoctorCard;
  session: PatientPortalSession | null;
  currentPath: string;
}) {
  const bookingMode = normalizeBookingMode(doctor.bookingMode);
  const bookingPath = patientBookingPath(session, `/patient/book-appointment?doctorSlug=${encodeURIComponent(doctor.doctorSlug)}${doctor.clinicSlug ? `&clinicSlug=${encodeURIComponent(doctor.clinicSlug)}` : ""}`);
  const callHref = resolveTelHref(doctor.contactPhone);
  return (
    <article className="patient-record-card patient-discovery-card">
      <div className="record-card-top">
        <div>
          <strong>{doctor.doctorName}</strong>
          <span>{doctor.specialization ?? "General consultation"}</span>
        </div>
        <span className={`status-pill status-pill--compact ${bookingMode ? `booking-mode-chip--${bookingModeTone(doctor.bookingMode)}` : ""}`}>
          {bookingModeLabel(doctor.bookingMode)}
        </span>
      </div>
      <div className="record-card-meta">
        <span>{doctor.clinicName}</span>
        <span>{buildDiscoveryLocationLabel(doctor.area, doctor.city)}</span>
      </div>
      <div className="record-card-meta">
        <span>{doctor.qualification ?? "Published doctor profile"}</span>
        <span>{[formatYears(doctor.yearsOfExperience), doctor.nextAvailableSlotSummary].filter(Boolean).join(" · ") || "Published profile"}</span>
      </div>
      <DiscoveryActionRow
        primary={bookingMode === "ONLINE_BOOKING" ? (
          <Link className="primary-button" to={bookingPath}>
            Book online
          </Link>
        ) : (
          <Link
            className="primary-button"
            to={`/patient/doctors/${encodeURIComponent(doctor.doctorSlug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View profile
          </Link>
        )}
        secondary={(
          <Link
            className="secondary-button"
            to={`/patient/doctors/${encodeURIComponent(doctor.doctorSlug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View profile
          </Link>
        )}
        tertiary={callHref ? (
          <a className="ghost-button" href={callHref}>
            Call clinic
          </a>
        ) : null}
      />
    </article>
  );
}

function CareClinicResultCard({
  clinic,
  session,
  onSwitch,
  switching,
}: {
  clinic: CareClinicCard;
  session: PatientPortalPatientSession;
  onSwitch: (tenantId: string) => void;
  switching: boolean;
}) {
  const bookingPath = patientBookingPath(session, `/patient/book-appointment?clinicSlug=${encodeURIComponent(clinic.slug ?? clinic.explicitKey)}`);
  return (
    <article className="patient-record-card patient-discovery-card">
      <div className="record-card-top">
        <div>
          <strong>{clinic.displayName}</strong>
          <span>{clinic.clinicName}</span>
        </div>
        <span className={`status-pill status-pill--compact ${clinic.selected ? "status-success" : clinic.active ? "status-warning" : "status-neutral"}`}>
          {clinic.selected ? "Current clinic" : clinic.active ? "Authorized" : "Inactive"}
        </span>
      </div>
      <div className="record-card-meta">
        <span>{clinic.address ?? "Address available on profile"}</span>
        <span>{buildDiscoveryLocationLabel(null, clinic.city)}</span>
      </div>
      <DiscoveryActionRow
        primary={clinic.selected ? (
          <Link className="primary-button" to="/patient/dashboard">
            View dashboard
          </Link>
        ) : (
          <button className="primary-button" type="button" disabled={switching} onClick={() => onSwitch(clinic.tenantId)}>
            {switching ? "Switching..." : "Switch clinic"}
          </button>
        )}
        secondary={clinic.slug ? (
          <Link className="secondary-button" to={bookingPath}>
            Book online
          </Link>
        ) : null}
      />
    </article>
  );
}

function PublicClinicResultCard({ clinic, session }: { clinic: PublicClinicCard; session: PatientPortalSession | null }) {
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}`;
  const bookingPath = patientBookingPath(session, `/patient/book-appointment?clinicSlug=${encodeURIComponent(clinic.clinicSlug)}`);
  const callHref = resolveTelHref(clinic.phone);
  return (
    <article className="patient-record-card patient-discovery-card">
      <div className="record-card-top">
        <div>
          <strong>{clinic.clinicName}</strong>
          <span>{clinic.availableToday ? "Available today" : "Published clinic profile"}</span>
        </div>
        <span className={`status-pill status-pill--compact ${clinic.bookingMode ? `booking-mode-chip--${bookingModeTone(clinic.bookingMode)}` : ""}`}>
          {bookingModeLabel(clinic.bookingMode)}
        </span>
      </div>
      <div className="record-card-meta">
        <span>{clinic.address ?? "Address available on profile"}</span>
        <span>{buildDiscoveryLocationLabel(clinic.area, clinic.city)}</span>
      </div>
      {clinic.specialities.length ? (
        <div className="booking-service-list">
          {clinic.specialities.slice(0, 4).map((speciality) => (
            <span key={speciality} className="booking-service-pill">
              {speciality}
            </span>
          ))}
        </div>
      ) : null}
      <DiscoveryActionRow
        primary={clinic.bookingMode === "ONLINE_BOOKING" ? (
          <Link className="primary-button" to={bookingPath}>
            Book online
          </Link>
        ) : (
          <Link
            className="primary-button"
            to={`/patient/clinics/${encodeURIComponent(clinic.clinicSlug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View profile
          </Link>
        )}
        secondary={(
          <Link
            className="secondary-button"
            to={`/patient/clinics/${encodeURIComponent(clinic.clinicSlug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View profile
          </Link>
        )}
        tertiary={callHref ? (
          <a className="ghost-button" href={callHref}>
            Call clinic
          </a>
        ) : null}
      />
    </article>
  );
}

function PublicHospitalResultCard({ hospital, session }: { hospital: PublicHospitalCard; session: PatientPortalSession | null }) {
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}`;
  const bookingPath = patientBookingPath(session, `/patient/book-appointment?clinicSlug=${encodeURIComponent(hospital.hospitalSlug)}`);
  const callHref = resolveTelHref(hospital.phone);
  return (
    <article className="patient-record-card patient-discovery-card">
      <div className="record-card-top">
        <div>
          <strong>{hospital.hospitalName}</strong>
          <span>{hospital.availableToday ? "Available today" : "Published hospital profile"}</span>
        </div>
        <span className={`status-pill status-pill--compact ${hospital.bookingMode ? `booking-mode-chip--${bookingModeTone(hospital.bookingMode)}` : ""}`}>
          {bookingModeLabel(hospital.bookingMode)}
        </span>
      </div>
      <div className="record-card-meta">
        <span>{hospital.address ?? "Address available on profile"}</span>
        <span>{buildDiscoveryLocationLabel(hospital.area, hospital.city)}</span>
      </div>
      {hospital.specialities.length ? (
        <div className="booking-service-list">
          {hospital.specialities.slice(0, 4).map((speciality) => (
            <span key={speciality} className="booking-service-pill booking-service-pill--muted">
              {speciality}
            </span>
          ))}
        </div>
      ) : null}
      <DiscoveryActionRow
        primary={hospital.bookingMode === "ONLINE_BOOKING" ? (
          <Link className="primary-button" to={bookingPath}>
            Book online
          </Link>
        ) : (
          <Link
            className="primary-button"
            to={`/patient/hospitals/${encodeURIComponent(hospital.hospitalSlug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View profile
          </Link>
        )}
        secondary={(
          <Link
            className="secondary-button"
            to={`/patient/hospitals/${encodeURIComponent(hospital.hospitalSlug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View profile
          </Link>
        )}
        tertiary={callHref ? (
          <a className="ghost-button" href={callHref}>
            Call hospital
          </a>
        ) : null}
      />
    </article>
  );
}

function SpecialityResultCard({ speciality, city, tab }: { speciality: SpecialityCard; city: string; tab: DiscoveryTab }) {
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}`;
  const searchDoctorsPath = `/patient/doctors?q=${encodeURIComponent(speciality.speciality)}${city ? `&city=${encodeURIComponent(city)}` : ""}`;
  const searchClinicsPath = `/patient/clinics?q=${encodeURIComponent(speciality.speciality)}${city ? `&city=${encodeURIComponent(city)}` : ""}`;
  return (
    <article className="patient-record-card patient-discovery-card">
      <div className="record-card-top">
        <div>
          <strong>{speciality.speciality}</strong>
          <span>Published speciality</span>
        </div>
        <span className="status-pill status-pill--compact">Browse</span>
      </div>
      <div className="record-card-meta">
        <span>{speciality.doctorsCount} doctors</span>
        <span>{speciality.clinicsCount} clinics · {speciality.hospitalsCount} hospitals</span>
      </div>
      <DiscoveryActionRow
        primary={(
          <Link className="primary-button" to={searchDoctorsPath}>
            Search doctors
          </Link>
        )}
        secondary={(
          <Link className="secondary-button" to={searchClinicsPath}>
            Search clinics
          </Link>
        )}
        tertiary={tab === "services" ? (
          <Link
            className="ghost-button"
            to={`/patient/specialities/${encodeURIComponent(speciality.specialitySlug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View service
          </Link>
        ) : (
          <Link
            className="ghost-button"
            to={`/patient/specialities/${encodeURIComponent(speciality.specialitySlug)}`}
            state={buildCareProfileBackState(currentPath)}
          >
            View speciality
          </Link>
        )}
      />
    </article>
  );
}

export function PatientUnifiedDiscoveryPage({
  session,
  onSaveSession,
  onSignOut,
  defaultTab,
}: {
  session: PatientPortalSession | null;
  onSaveSession: (session: PatientPortalSession) => void;
  onSignOut: () => void;
  defaultTab: DiscoveryTab;
}) {
  if (!session || session.sessionRole !== "patient") {
    return <Navigate to="/patient/login" replace />;
  }

  return (
    <PatientDiscoveryWorkspace
      session={session}
      onSaveSession={onSaveSession}
      onSignOut={onSignOut}
      defaultTab={defaultTab}
    />
  );
}

function PatientDiscoveryWorkspace({
  session,
  onSaveSession,
  onSignOut,
  defaultTab,
}: {
  session: PatientPortalPatientSession;
  onSaveSession: (session: PatientPortalSession) => void;
  onSignOut: () => void;
  defaultTab: DiscoveryTab;
}) {
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}`;
  const [searchParams, setSearchParams] = useSearchParams();
  const { locationState } = usePublicLocation();
  const queryParam = searchParams.get("q")?.trim() ?? "";
  const areaParam = searchParams.get("area")?.trim() ?? "";
  const cityParam = searchParams.get("city")?.trim() ?? "";
  const tab = getDiscoveryTab(searchParams.get("tab"), defaultTab);
  const [query, setQuery] = useState(queryParam);
  const [city, setCity] = useState(cityParam || locationState.location.trim() || "");
  const [area, setArea] = useState(areaParam);
  const [refreshKey, setRefreshKey] = useState(0);
  const [switchingTenantId, setSwitchingTenantId] = useState<string | null>(null);
  const [switchError, setSwitchError] = useState<string | null>(null);

  useEffect(() => {
    setQuery(queryParam);
  }, [queryParam]);

  useEffect(() => {
    setCity(cityParam || locationState.location.trim() || "");
  }, [cityParam, locationState.location]);

  useEffect(() => {
    setArea(areaParam);
  }, [areaParam]);

  const currentClinic = usePatientPortalResource<PatientPortalClinicResponse | null>(session, "/api/patient-portal/clinic", null, refreshKey);
  const careDoctors = usePatientPortalResource<PatientPortalDoctorResponse[]>(session, "/api/patient-portal/doctors", [], refreshKey);
  const authorizedClinics = usePatientPortalResource<PatientPortalAuthorizedClinicResponse[]>(session, "/api/patient-portal/clinics", [], refreshKey);
  const publicSearch = usePublicSearch(query, city, area, refreshKey);

  const careClinicCards = useMemo(() => {
    const selectedClinic = currentClinic.data;
    return authorizedClinics.data.map((clinic) => careClinicCard(clinic, clinic.selected ? selectedClinic : null));
  }, [authorizedClinics.data, currentClinic.data]);

  const careDoctorCards = useMemo(() => {
    const selectedClinic = currentClinic.data;
    return careDoctors.data.map((doctor) => careDoctorCard(doctor, selectedClinic));
  }, [careDoctors.data, currentClinic.data]);

  const publicDoctorCards = useMemo(() => {
    return publicSearch.data.doctors.items.map((doctor) => publicDoctorCard(doctor));
  }, [publicSearch.data.doctors.items]);

  const publicClinicCards = useMemo(() => {
    return publicSearch.data.clinics.items.map((clinic) => publicClinicCard(clinic));
  }, [publicSearch.data.clinics.items]);

  const publicHospitalCards = useMemo(() => {
    return publicSearch.data.hospitals.items.map((hospital) => publicHospitalCard(hospital));
  }, [publicSearch.data.hospitals.items]);

  const specialityCards = useMemo(() => {
    return publicSearch.data.specialities.map((speciality) => specialtyCard(speciality));
  }, [publicSearch.data.specialities]);

  const mergedDoctorCards = useMemo(
    () => dedupeByExplicitKey([...careDoctorCards, ...publicDoctorCards], (item) => item.explicitKey),
    [careDoctorCards, publicDoctorCards],
  );

  const mergedClinicCards = useMemo(
    () => dedupeByExplicitKey([...careClinicCards, ...publicClinicCards], (item) => item.explicitKey),
    [careClinicCards, publicClinicCards],
  );

  const filteredDoctorCards = useMemo(() => {
    return mergedDoctorCards.filter((doctor) =>
      matchesSearch(
        [
          doctor.doctorName,
          doctor.specialization,
          doctor.qualification,
          "clinicName" in doctor ? doctor.clinicName : null,
          "area" in doctor ? doctor.area : null,
          "city" in doctor ? doctor.city : null,
          "nextAvailableSlotSummary" in doctor ? doctor.nextAvailableSlotSummary : null,
        ],
        query,
      ),
    );
  }, [mergedDoctorCards, query]);

  const filteredClinicCards = useMemo(() => {
    return mergedClinicCards.filter((clinic) =>
      matchesSearch(
        [
          clinic.source === "care" ? clinic.displayName : clinic.clinicName,
          clinic.source === "care" ? clinic.clinicName : clinic.clinicName,
          clinic.source === "care" ? clinic.address : clinic.address,
          clinic.source === "care" ? clinic.city : clinic.city,
          clinic.source === "public" ? clinic.specialities.join(" ") : clinic.authorizationSource,
        ],
        query,
      ),
    );
  }, [mergedClinicCards, query]);

  const filteredHospitals = useMemo(() => {
    return publicHospitalCards.filter((hospital) =>
      matchesSearch([hospital.hospitalName, hospital.address, hospital.area, hospital.city, hospital.specialities.join(" ")], query),
    );
  }, [publicHospitalCards, query]);

  const filteredSpecialities = useMemo(() => {
    return specialityCards.filter((speciality) =>
      matchesSearch([speciality.speciality, speciality.doctorsCount, speciality.clinicsCount, speciality.hospitalsCount], query),
    );
  }, [query, specialityCards]);

  const careDoctorsVisible = tab === "all" || tab === "doctors";
  const careClinicsVisible = tab === "all" || tab === "clinics";
  const hospitalsVisible = tab === "all" || tab === "hospitals";
  const specialitiesVisible = tab === "all" || tab === "specialities" || tab === "services";

  async function handleClinicSwitch(targetTenantId: string) {
    if (switchingTenantId) {
      return;
    }
    setSwitchError(null);
    setSwitchingTenantId(targetTenantId);
    try {
      const response = await postPatientPortalClinicSwitch<PatientPortalClinicSwitchResponse>(
        `/api/patient-portal/clinics/${targetTenantId}/switch`,
        session,
      );
      onSaveSession({
        ...session,
        tenantCode: response.tenantCode,
        tenantId: response.tenantId,
        patientLabel: response.patientDisplayName,
        patientSessionToken: response.patientSessionToken,
        createdAt: new Date().toISOString(),
      });
      setRefreshKey((current) => current + 1);
    } catch (error) {
      setSwitchError(error instanceof Error ? error.message : "Unable to switch clinic context right now.");
    } finally {
      setSwitchingTenantId(null);
    }
  }

  function updateQuery(next: string) {
    setQuery(next);
    const nextParams = new URLSearchParams(searchParams);
    if (next.trim()) {
      nextParams.set("q", next.trim());
    } else {
      nextParams.delete("q");
    }
    nextParams.set("tab", tab);
    setSearchParams(nextParams, { replace: true });
  }

  function updateCity(next: string) {
    setCity(next);
    const nextParams = new URLSearchParams(searchParams);
    if (next.trim()) {
      nextParams.set("city", next.trim());
    } else {
      nextParams.delete("city");
    }
    nextParams.set("tab", tab);
    setSearchParams(nextParams, { replace: true });
  }

  function updateArea(next: string) {
    setArea(next);
    const nextParams = new URLSearchParams(searchParams);
    if (next.trim()) {
      nextParams.set("area", next.trim());
    } else {
      nextParams.delete("area");
    }
    nextParams.set("tab", tab);
    setSearchParams(nextParams, { replace: true });
  }

  function updateTab(nextTab: DiscoveryTab) {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", nextTab);
    setSearchParams(nextParams, { replace: true });
  }

  const subtitle = "Search your authorized clinics and published Discover providers without leaving Care.";
  const discoverFallbackHref = careConfig.discoverAppUrl || "/doctors";

  return (
    <PatientPortalShell
      session={session}
      title="Find care"
      subtitle={subtitle}
      onSignOut={onSignOut}
    >
      <div className="patient-discovery-stack">
        <section className="patient-panel patient-panel-wide patient-discovery-search-panel">
          <ResultHeader title="Search care" caption="Care network + published discovery" />
          <div className="patient-form-grid patient-discovery-search-grid">
            <label className="patient-form-span-2 patient-filter-field">
              <span>Search doctor, clinic, specialty, service, or area</span>
              <input
                value={query}
                onChange={(event) => updateQuery(event.target.value)}
                placeholder="e.g. cardiology, diagnostics, Pune, Dr Arjun"
              />
            </label>
            <label className="patient-filter-field">
              <span>City</span>
              <input
                value={city}
                onChange={(event) => updateCity(event.target.value)}
                placeholder="Pune"
              />
            </label>
            <label className="patient-filter-field">
              <span>Area</span>
              <input
                value={area}
                onChange={(event) => updateArea(event.target.value)}
                placeholder="Baner"
              />
            </label>
          </div>
          <div className="booking-filter-chips patient-discovery-tabs" role="tablist" aria-label="Discovery categories">
            {(["all", "doctors", "clinics", "hospitals", "specialities", "services"] as DiscoveryTab[]).map((item) => (
              <button
                key={item}
                type="button"
                className={`booking-chip${tab === item ? " is-active" : ""}`}
                aria-pressed={tab === item}
                onClick={() => updateTab(item)}
              >
                {discoveryTabLabel(item)}
              </button>
            ))}
          </div>
          <div className="booking-capability-legend">
            <span className="booking-mode-chip booking-mode-chip--is-online">Book online through Care</span>
            <span className="booking-mode-chip booking-mode-chip--is-call">Call clinic or view profile</span>
          </div>
        </section>

        <section className="patient-panel patient-panel-wide">
          <ResultHeader title="My Care Network" caption="Authorized clinics and doctors" />
          {switchError ? (
            <div className="patient-inline-empty">
              <strong>Unable to switch clinic</strong>
              <p>{switchError}</p>
            </div>
          ) : null}
          {authorizedClinics.loading || currentClinic.loading || careDoctors.loading ? (
            <div className="patient-inline-empty">Loading your care network...</div>
          ) : null}
          {!authorizedClinics.loading && !currentClinic.loading && !careDoctors.loading ? (
            <>
              <div className="patient-subcard-groups">
                <section className="patient-subcard-group">
                  <div className="patient-subcard-group__heading">
                    <strong>Clinics</strong>
                    <span>{authorizedClinics.data.length} authorized</span>
                  </div>
                  <div className="patient-subcard-list">
                    {careClinicCards.length ? careClinicCards.map((clinic) => (
                      <CareClinicResultCard
                        key={clinic.explicitKey}
                        clinic={clinic}
                        session={session}
                        onSwitch={handleClinicSwitch}
                        switching={switchingTenantId === clinic.tenantId}
                      />
                    )) : (
                      <div className="patient-inline-empty">
                        <strong>No care network clinics found.</strong>
                        <p>Approved clinics will appear here after your Care account is linked.</p>
                      </div>
                    )}
                  </div>
                </section>
                <section className="patient-subcard-group">
                  <div className="patient-subcard-group__heading">
                    <strong>Doctors</strong>
                    <span>{careDoctors.data.length} active</span>
                  </div>
                  <div className="patient-subcard-list">
                    {careDoctors.data.length ? careDoctorCards.map((doctor) => (
                      <CareDoctorResultCard
                        key={doctor.explicitKey}
                        doctor={doctor}
                        clinic={currentClinic.data}
                        session={session}
                        currentPath={currentPath}
                      />
                    )) : (
                      <div className="patient-inline-empty">
                        <strong>No active doctors are available right now.</strong>
                        <p>Switch clinic context to see the doctors authorized for that clinic.</p>
                      </div>
                    )}
                  </div>
                </section>
              </div>
            </>
          ) : null}
        </section>

        <section className="patient-panel patient-panel-wide">
          <ResultHeader
            title={tab === "clinics" ? "Published clinics" : tab === "hospitals" ? "Published hospitals" : tab === "specialities" || tab === "services" ? "Published specialties and services" : "Published providers"}
            caption="Discover data stays read-only"
          />
          {publicSearch.loading ? (
            <div className="patient-inline-empty">Loading published providers...</div>
          ) : publicSearch.error ? (
            <div className="patient-inline-empty">
              <strong>Unable to load published providers</strong>
              <p>{publicSearch.error}</p>
            </div>
          ) : null}

          {!publicSearch.loading && !publicSearch.error ? (
            <>
              {careDoctorsVisible ? (
                <div className="patient-subcard-groups">
                  <section className="patient-subcard-group">
                    <div className="patient-subcard-group__heading">
                      <strong>Care network doctors</strong>
                      <span>{filteredDoctorCards.filter((item) => item.source === "care").length} results</span>
                    </div>
                    <div className="patient-subcard-list">
                      {filteredDoctorCards.filter((item) => item.source === "care").length ? filteredDoctorCards
                        .filter((item): item is CareDoctorCard => item.source === "care")
                        .map((doctor) => (
                          <CareDoctorResultCard
                            key={doctor.explicitKey}
                            doctor={doctor}
                            clinic={currentClinic.data}
                            session={session}
                            currentPath={currentPath}
                          />
                        )) : (
                        <div className="patient-inline-empty">
                          <strong>No matching doctors in your care network.</strong>
                          <p>Try another doctor, clinic, specialty, or nearby location.</p>
                        </div>
                      )}
                    </div>
                  </section>
                  <section className="patient-subcard-group">
                    <div className="patient-subcard-group__heading">
                      <strong>Published doctors</strong>
                      <span>{filteredDoctorCards.filter((item) => item.source === "public").length} results</span>
                    </div>
                    <div className="patient-subcard-list">
                      {filteredDoctorCards.filter((item) => item.source === "public").length ? filteredDoctorCards
                        .filter((item): item is PublicDoctorCard => item.source === "public")
                        .map((doctor) => (
                          <PublicDoctorResultCard key={doctor.explicitKey} doctor={doctor} session={session} currentPath={currentPath} />
                        )) : (
                        <div className="patient-inline-empty">
                          <strong>No published doctors matched this search.</strong>
                          <p>Try another location or broaden the search query.</p>
                        </div>
                      )}
                    </div>
                  </section>
                </div>
              ) : null}

              {careClinicsVisible ? (
                <div className="patient-subcard-groups">
                  <section className="patient-subcard-group">
                    <div className="patient-subcard-group__heading">
                      <strong>Care network clinics</strong>
                      <span>{filteredClinicCards.filter((item) => item.source === "care").length} results</span>
                    </div>
                    <div className="patient-subcard-list">
                      {filteredClinicCards.filter((item) => item.source === "care").length ? filteredClinicCards
                        .filter((item): item is CareClinicCard => item.source === "care")
                        .map((clinic) => (
                          <CareClinicResultCard
                            key={clinic.explicitKey}
                            clinic={clinic}
                            session={session}
                            onSwitch={handleClinicSwitch}
                            switching={switchingTenantId === clinic.tenantId}
                          />
                        )) : (
                        <div className="patient-inline-empty">
                          <strong>No matching clinics in your care network.</strong>
                          <p>Authorized clinics will appear here once they are linked to your Care account.</p>
                        </div>
                      )}
                    </div>
                  </section>
                  <section className="patient-subcard-group">
                    <div className="patient-subcard-group__heading">
                      <strong>Published clinics</strong>
                      <span>{filteredClinicCards.filter((item) => item.source === "public").length} results</span>
                    </div>
                    <div className="patient-subcard-list">
                      {filteredClinicCards.filter((item) => item.source === "public").length ? filteredClinicCards
                        .filter((item): item is PublicClinicCard => item.source === "public")
                        .map((clinic) => (
                          <PublicClinicResultCard key={clinic.explicitKey} clinic={clinic} session={session} />
                        )) : (
                        <div className="patient-inline-empty">
                          <strong>No published clinics matched this search.</strong>
                          <p>Try another area or a broader search term.</p>
                        </div>
                      )}
                    </div>
                  </section>
                </div>
              ) : null}

              {hospitalsVisible ? (
                <section className="patient-subcard-group">
                  <div className="patient-subcard-group__heading">
                    <strong>Published hospitals</strong>
                    <span>{filteredHospitals.length} results</span>
                  </div>
                  <div className="patient-subcard-list">
                    {filteredHospitals.length ? filteredHospitals.map((hospital) => (
                      <PublicHospitalResultCard key={hospital.explicitKey} hospital={hospital} session={session} />
                    )) : (
                      <div className="patient-inline-empty">
                        <strong>No published hospitals matched this search.</strong>
                        <p>Try another city or broaden the query.</p>
                      </div>
                    )}
                  </div>
                </section>
              ) : null}

              {specialitiesVisible ? (
                <section className="patient-subcard-group">
                  <div className="patient-subcard-group__heading">
                    <strong>{tab === "services" ? "Services" : "Specialities"}</strong>
                    <span>{filteredSpecialities.length} results</span>
                  </div>
                  <div className="patient-subcard-list patient-discovery-speciality-list">
                    {filteredSpecialities.length ? filteredSpecialities.map((speciality) => (
                      <SpecialityResultCard key={speciality.explicitKey} speciality={speciality} city={city} tab={tab} />
                    )) : (
                      <div className="patient-inline-empty">
                        <strong>No specialities matched this search.</strong>
                        <p>Try another specialty or nearby location.</p>
                      </div>
                    )}
                  </div>
                </section>
              ) : null}

              <div className="patient-discovery-footer">
                <p>Logged-in patients can continue normal booking and profile viewing inside Care.</p>
                <Link className="text-link patient-discover-link" to={discoverFallbackHref} target="_blank" rel="noreferrer">
                  Open Jeevanam Discover
                </Link>
              </div>
            </>
          ) : null}
        </section>
      </div>
    </PatientPortalShell>
  );
}

function usePublicDetail<T>(path: string, initialValue: T): PublicProfileFetchState<T> {
  const initialValueRef = useRef(initialValue);
  const [state, setState] = useState<PublicProfileFetchState<T>>({
    data: initialValue,
    status: PUBLIC_PROFILE_STATUS.LOADING,
    errorMessage: null,
  });
  const lifecycleRef = useRef<PublicProfileLifecycle<T> | null>(null);

  if (!lifecycleRef.current) {
    lifecycleRef.current = createPublicProfileLifecycle<T>({
      fetchDetail: (requestPath, signal) => fetchPublicJson<T>(requestPath, {}, signal),
      onState: setState,
      isNotFoundError: isPublicCatalogNotFoundError,
      loadingErrorMessage: "We couldn't load this profile right now.",
      notFoundErrorMessage: "Profile not found.",
    });
  }

  useEffect(() => lifecycleRef.current?.update(path, initialValueRef.current), [path]);
  useEffect(() => () => lifecycleRef.current?.dispose(), []);

  return state;
}

function ProfileFrame({
  session,
  title,
  subtitle,
  backLabel = "Back",
  children,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  title: string;
  subtitle: string;
  backLabel?: string;
  children: ReactNode;
  onSignOut: () => void;
}) {
  const location = useLocation();
  const navigate = useNavigate();
  const backNavigationLockedRef = useRef(false);
  if (!session || session.sessionRole !== "patient") {
    return <Navigate to="/patient/login" replace />;
  }
  const handleBack = () => {
    if (backNavigationLockedRef.current) {
      return;
    }
    backNavigationLockedRef.current = true;
    const backTarget = hasCareProfileBackState(location.state)
      ? resolveCareProfileBackTarget(location.state)
      : resolveCareProfileFallbackPath();
    navigate(backTarget, { replace: true });
  };
  return (
    <PatientPortalShell
      session={session}
      title={title}
      subtitle={subtitle}
      topbarBeforeTitle={(
        <button
          type="button"
          className="patient-profile-back-link text-link"
          onClick={handleBack}
          aria-label="Back to previous Care page"
        >
          ← {backLabel}
        </button>
      )}
      onSignOut={onSignOut}
    >
      {children}
    </PatientPortalShell>
  );
}

export function PatientPublicDoctorProfilePage({
  session,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  onSignOut: () => void;
}) {
  const { doctorSlug = "" } = useParams();
  const detail = usePublicDetail<PublicDoctorDetailResponse>(`/api/public/doctors/${encodeURIComponent(doctorSlug)}`, {
    publicDoctorId: "",
    doctorSlug: "",
    canonicalSlug: "",
    publicPath: "",
    doctorDisplayName: "",
    photoUrl: null,
    bookingMode: null,
    qualification: null,
    medicalCouncil: null,
    yearsOfExperience: null,
    summary: null,
    biography: null,
    specialities: [],
    subSpecialities: [],
    languages: [],
    consultationModes: [],
    services: [],
    locations: [],
    galleryImageUrls: [],
    coverUrl: null,
    logoUrl: null,
    contactPhone: null,
    contactEmail: null,
    website: null,
    area: null,
    city: null,
    state: null,
    country: null,
    primarySpeciality: null,
    consultationFee: null,
    reviewsComingSoon: false,
    subtitle: null,
    bookingSummary: null,
    practices: [],
    clinics: [],
    availableDays: [],
    nextAvailableSlots: [],
    availableToday: false,
    bookingReference: null,
    canBookOnline: false,
  });

  const practices = detail.data.practices ?? [];
  const primaryPractice =
    practices.find((practice) => normalizePublicClinicDisplayName(practice.practiceDisplayName))
    ?? practices[0]
    ?? null;
  const primaryClinic =
    detail.data.clinics.find((clinic) => normalizePublicClinicDisplayName(clinic.clinicDisplayName))
    ?? detail.data.clinics[0]
    ?? null;
  const clinicDisplayName =
    normalizePublicClinicDisplayName(primaryPractice?.practiceDisplayName)
    ?? normalizePublicClinicDisplayName(primaryClinic?.clinicDisplayName)
    ?? null;
  const bookingClinicSlug = primaryPractice?.practiceSlug ?? primaryClinic?.clinicSlug ?? null;
  const bookingPath = patientBookingPath(
    session,
    `/patient/book-appointment?doctorSlug=${encodeURIComponent(doctorSlug)}${bookingClinicSlug ? `&clinicSlug=${encodeURIComponent(bookingClinicSlug)}` : ""}`,
  );
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}`;
  const callHref = resolveTelHref(detail.data.contactPhone);
  const specialities = detail.data.specialities ?? [];
  const services = detail.data.services ?? [];

  return (
    <ProfileFrame
      session={session}
      title={detail.data.doctorDisplayName || "Doctor profile"}
      subtitle="View a published doctor profile inside Care."
      onSignOut={onSignOut}
    >
      {detail.status === PUBLIC_PROFILE_STATUS.LOADING ? (
        <div className="patient-inline-empty">Loading doctor profile...</div>
      ) : detail.status === PUBLIC_PROFILE_STATUS.NOT_FOUND ? (
        <div className="patient-inline-empty">
          <strong>Doctor profile not found.</strong>
          <p>The requested doctor profile is not available.</p>
        </div>
      ) : detail.status === PUBLIC_PROFILE_STATUS.ERROR ? (
        <div className="patient-inline-empty">
          <strong>We couldn't load this doctor profile right now.</strong>
          <p>Please try again in a moment.</p>
        </div>
      ) : (
        <div className="patient-discovery-stack">
          <section className="patient-panel patient-panel-wide">
            <ResultHeader title="Doctor profile" caption={detail.data.bookingMode ? bookingModeLabel(detail.data.bookingMode) : "Published profile"} />
            <div className="patient-highlight-card">
              <strong>{detail.data.doctorDisplayName}</strong>
              <span>{detail.data.primarySpeciality ?? detail.data.specialities[0] ?? "Speciality not published"}</span>
              <p>{detail.data.summary ?? detail.data.subtitle ?? "Published doctor profile"}</p>
            </div>
            <div className="booking-service-list">
              {specialities.slice(0, 6).map((item) => (
                <span key={item} className="booking-service-pill">
                  {item}
                </span>
              ))}
              {services.slice(0, 6).map((item) => (
                <span key={item} className="booking-service-pill booking-service-pill--muted">
                  {item}
                </span>
              ))}
            </div>
            <div className="patient-detail-list">
              <div>
                <strong>Clinic</strong>
                <span>{clinicDisplayName ?? "Clinic details available on profile"}</span>
              </div>
              <div>
                <strong>Location</strong>
                <span>{buildDiscoveryLocationLabel(detail.data.area, detail.data.city)}</span>
              </div>
              <div>
                <strong>Consultation</strong>
                <span>{detail.data.bookingSummary ?? bookingModeLabel(detail.data.bookingMode)}</span>
              </div>
              <div>
                <strong>Experience</strong>
                <span>{formatYears(detail.data.yearsOfExperience) ?? "Not published"}</span>
              </div>
            </div>
            <DiscoveryActionRow
              primary={detail.data.canBookOnline ? (
                <Link className="primary-button" to={bookingPath}>
                  Book online
                </Link>
              ) : null}
              secondary={callHref ? (
                <a className="secondary-button" href={callHref}>
                  Call clinic
                </a>
              ) : null}
              tertiary={primaryClinic?.clinicSlug ? (
                <Link
                  className="ghost-button"
                  to={`/patient/clinics/${encodeURIComponent(primaryClinic.clinicSlug)}`}
                  state={buildCareProfileBackState(currentPath)}
                >
                  View clinic profile
                </Link>
              ) : null}
            />
          </section>

          <section className="patient-panel patient-panel-wide">
            <ResultHeader title="Published details" caption="Public projection only" />
            <div className="patient-detail-list">
              <div>
                <strong>Qualifications</strong>
                <span>{detail.data.qualification ?? "Not published"}</span>
              </div>
              <div>
                <strong>Languages</strong>
                <span>{detail.data.languages.length ? detail.data.languages.join(", ") : "Not published"}</span>
              </div>
              <div>
                <strong>Available days</strong>
                <span>{detail.data.availableDays.length ? detail.data.availableDays.join(", ") : "Not published"}</span>
              </div>
              <div>
                <strong>Next available</strong>
                <span>{detail.data.nextAvailableSlots.length ? detail.data.nextAvailableSlots.join(" · ") : "Not published"}</span>
              </div>
            </div>
          </section>
        </div>
      )}
    </ProfileFrame>
  );
}

export function PatientPublicClinicProfilePage({
  session,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  onSignOut: () => void;
}) {
  const { clinicSlug = "" } = useParams();
  const detail = usePublicDetail<PublicClinicDetailResponse>(`/api/public/clinics/${encodeURIComponent(clinicSlug)}`, {
    clinicSlug: "",
    canonicalSlug: "",
    publicPath: "",
    clinicDisplayName: "",
    logoUrl: null,
    coverUrl: null,
    bookingMode: null,
    address: null,
    area: null,
    city: null,
    summary: null,
    description: null,
    specialities: [],
    services: [],
    departments: [],
    facilities: [],
    consultationModes: [],
    locations: [],
    galleryImageUrls: [],
    doctors: [],
    contactPhone: null,
    contactEmail: null,
    website: null,
    emergencyAvailable: false,
    timings: [],
    availableToday: false,
    reviewsComingSoon: false,
    subtitle: null,
  });

  const callHref = resolveTelHref(detail.data.contactPhone);
  const specialities = detail.data.specialities ?? [];
  const services = detail.data.services ?? [];
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}`;
  return (
    <ProfileFrame
      session={session}
      title={detail.data.clinicDisplayName || "Clinic profile"}
      subtitle="View a published clinic profile inside Care."
      onSignOut={onSignOut}
    >
      {detail.status === PUBLIC_PROFILE_STATUS.LOADING ? (
        <div className="patient-inline-empty">Loading clinic profile...</div>
      ) : detail.status === PUBLIC_PROFILE_STATUS.NOT_FOUND ? (
        <div className="patient-inline-empty">
          <strong>Clinic profile not found.</strong>
          <p>The requested clinic profile is not available.</p>
        </div>
      ) : detail.status === PUBLIC_PROFILE_STATUS.ERROR ? (
        <div className="patient-inline-empty">
          <strong>We couldn't load this clinic profile right now.</strong>
          <p>Please try again in a moment.</p>
        </div>
      ) : (
        <div className="patient-discovery-stack">
          <section className="patient-panel patient-panel-wide">
            <ResultHeader title="Clinic profile" caption={detail.data.bookingMode ? bookingModeLabel(detail.data.bookingMode) : "Published profile"} />
            <div className="patient-highlight-card">
              <strong>{detail.data.clinicDisplayName}</strong>
              <span>{buildDiscoveryLocationLabel(detail.data.area, detail.data.city)}</span>
              <p>{detail.data.summary ?? detail.data.description ?? "Published clinic profile"}</p>
            </div>
            <div className="booking-service-list">
              {specialities.slice(0, 6).map((item) => (
                <span key={item} className="booking-service-pill">
                  {item}
                </span>
              ))}
              {services.slice(0, 6).map((item) => (
                <span key={item} className="booking-service-pill booking-service-pill--muted">
                  {item}
                </span>
              ))}
            </div>
            <DiscoveryActionRow
              primary={detail.data.bookingMode === "ONLINE_BOOKING" ? (
                <Link className="primary-button" to={patientBookingPath(session, `/patient/book-appointment?clinicSlug=${encodeURIComponent(clinicSlug)}`)}>
                  Book online
                </Link>
              ) : callHref ? (
                <a className="primary-button" href={callHref}>
                  Call clinic
                </a>
              ) : null}
              secondary={callHref ? (
                <a className="secondary-button" href={callHref}>
                  Call clinic
                </a>
              ) : null}
            />
          </section>

          <section className="patient-panel patient-panel-wide">
            <ResultHeader title="Doctors at this clinic" caption={`${detail.data.doctors.length} published doctors`} />
            <div className="patient-subcard-list">
              {detail.data.doctors.length ? detail.data.doctors.map((doctor) => (
                <article key={doctor.publicDoctorId} className="patient-record-card patient-discovery-card">
                  <div className="record-card-top">
                    <div>
                      <strong>{doctor.doctorDisplayName}</strong>
                      <span>{doctor.speciality ?? "General consultation"}</span>
                    </div>
                    <span className="status-pill status-pill--compact">Published doctor</span>
                  </div>
                  <div className="record-card-meta">
                    <span>{doctor.clinicDisplayName}</span>
                    <span>{buildDiscoveryLocationLabel(doctor.area, doctor.city)}</span>
                  </div>
                    <DiscoveryActionRow
                      primary={doctor.bookingMode === "ONLINE_BOOKING" ? (
                        <Link className="primary-button" to={patientBookingPath(session, `/patient/book-appointment?doctorSlug=${encodeURIComponent(doctor.doctorSlug)}&clinicSlug=${encodeURIComponent(doctor.clinicSlug)}`)}>
                          Book online
                        </Link>
                      ) : (
                      <Link
                        className="primary-button"
                        to={`/patient/doctors/${encodeURIComponent(doctor.doctorSlug)}`}
                        state={buildCareProfileBackState(currentPath)}
                      >
                        View profile
                      </Link>
                    )}
                    secondary={(
                      <Link
                        className="secondary-button"
                        to={`/patient/doctors/${encodeURIComponent(doctor.doctorSlug)}`}
                        state={buildCareProfileBackState(currentPath)}
                      >
                        View profile
                      </Link>
                    )}
                  />
                </article>
              )) : (
                <div className="patient-inline-empty">
                  <strong>No doctors are published on this clinic profile.</strong>
                  <p>Try another clinic or another location.</p>
                </div>
              )}
            </div>
          </section>
        </div>
      )}
    </ProfileFrame>
  );
}

export function PatientPublicHospitalProfilePage({
  session,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  onSignOut: () => void;
}) {
  const { hospitalSlug = "" } = useParams();
  const detail = usePublicDetail<PublicHospitalDetailResponse>(`/api/public/hospitals/${encodeURIComponent(hospitalSlug)}`, {
    hospitalSlug: "",
    canonicalSlug: "",
    publicPath: "",
    hospitalDisplayName: "",
    logoUrl: null,
    coverUrl: null,
    bookingMode: null,
    address: null,
    area: null,
    city: null,
    summary: null,
    description: null,
    specialities: [],
    services: [],
    departments: [],
    facilities: [],
    consultationModes: [],
    locations: [],
    galleryImageUrls: [],
    doctors: [],
    contactPhone: null,
    contactEmail: null,
    website: null,
    emergencyAvailable: false,
    timings: [],
    availableToday: false,
    reviewsComingSoon: false,
    subtitle: null,
  });

  const callHref = resolveTelHref(detail.data.contactPhone);
  const specialities = detail.data.specialities ?? [];
  const services = detail.data.services ?? [];
  return (
    <ProfileFrame
      session={session}
      title={detail.data.hospitalDisplayName || "Hospital profile"}
      subtitle="View a published hospital profile inside Care."
      onSignOut={onSignOut}
    >
      {detail.status === PUBLIC_PROFILE_STATUS.LOADING ? (
        <div className="patient-inline-empty">Loading hospital profile...</div>
      ) : detail.status === PUBLIC_PROFILE_STATUS.NOT_FOUND ? (
        <div className="patient-inline-empty">
          <strong>Hospital profile not found.</strong>
          <p>The requested hospital profile is not available.</p>
        </div>
      ) : detail.status === PUBLIC_PROFILE_STATUS.ERROR ? (
        <div className="patient-inline-empty">
          <strong>We couldn't load this hospital profile right now.</strong>
          <p>Please try again in a moment.</p>
        </div>
      ) : (
        <div className="patient-discovery-stack">
          <section className="patient-panel patient-panel-wide">
            <ResultHeader title="Hospital profile" caption={detail.data.bookingMode ? bookingModeLabel(detail.data.bookingMode) : "Published profile"} />
            <div className="patient-highlight-card">
              <strong>{detail.data.hospitalDisplayName}</strong>
              <span>{buildDiscoveryLocationLabel(detail.data.area, detail.data.city)}</span>
              <p>{detail.data.summary ?? detail.data.description ?? "Published hospital profile"}</p>
            </div>
            <div className="booking-service-list">
              {specialities.slice(0, 6).map((item) => (
                <span key={item} className="booking-service-pill">
                  {item}
                </span>
              ))}
              {services.slice(0, 6).map((item) => (
                <span key={item} className="booking-service-pill booking-service-pill--muted">
                  {item}
                </span>
              ))}
            </div>
            <DiscoveryActionRow
              primary={detail.data.bookingMode === "ONLINE_BOOKING" ? (
                <Link className="primary-button" to={patientBookingPath(session, `/patient/book-appointment?clinicSlug=${encodeURIComponent(hospitalSlug)}`)}>
                  Book online
                </Link>
              ) : callHref ? (
                <a className="primary-button" href={callHref}>
                  Call hospital
                </a>
              ) : null}
              secondary={callHref ? (
                <a className="secondary-button" href={callHref}>
                  Call hospital
                </a>
              ) : null}
            />
          </section>
        </div>
      )}
    </ProfileFrame>
  );
}

export function PatientPublicSpecialityPage({
  session,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  onSignOut: () => void;
}) {
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}`;
  const { specialitySlug = "" } = useParams();
  const detail = usePublicDetail<PublicPageResponse<PublicDoctorSummaryResponse>>(
    `/api/public/specialities/${encodeURIComponent(specialitySlug)}`,
    { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 },
  );

  return (
    <ProfileFrame
      session={session}
      title="Speciality profile"
      subtitle="View published doctor results inside Care."
      onSignOut={onSignOut}
    >
      {detail.status === PUBLIC_PROFILE_STATUS.LOADING ? (
        <div className="patient-inline-empty">Loading speciality profile...</div>
      ) : detail.status === PUBLIC_PROFILE_STATUS.NOT_FOUND ? (
        <div className="patient-inline-empty">
          <strong>Speciality profile not found.</strong>
          <p>The requested speciality profile is not available.</p>
        </div>
      ) : detail.status === PUBLIC_PROFILE_STATUS.ERROR ? (
        <div className="patient-inline-empty">
          <strong>We couldn't load this speciality profile right now.</strong>
          <p>Please try again in a moment.</p>
        </div>
      ) : (
        <div className="patient-discovery-stack">
          <section className="patient-panel patient-panel-wide">
            <ResultHeader title={detail.data.items[0]?.speciality ?? "Speciality"} caption="Published doctor results" />
            <div className="patient-subcard-list">
              {detail.data.items.length ? detail.data.items.map((doctor) => (
                <PublicDoctorResultCard
                  key={doctor.publicDoctorId}
                  doctor={publicDoctorCard(doctor)}
                  session={session}
                  currentPath={currentPath}
                />
              )) : (
                <div className="patient-inline-empty">
                  <strong>No doctors found for this speciality.</strong>
                  <p>Try another speciality or location.</p>
                </div>
              )}
            </div>
          </section>
        </div>
      )}
    </ProfileFrame>
  );
}

export function PatientPublicServicesPage({
  session,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  onSignOut: () => void;
}) {
  return (
    <PatientPublicSpecialityPage
      session={session}
      onSignOut={onSignOut}
    />
  );
}
