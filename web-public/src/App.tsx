import { Link, NavLink, Route, Routes, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { type FormEvent, type ReactNode, useEffect, useMemo, useState } from "react";

type Clinic = {
  clinicDisplayName: string;
  city: string;
  locationLabel: string;
  specialities: string[];
};

type Doctor = {
  doctorDisplayName: string;
  clinicDisplayName: string;
  city: string;
  speciality: string;
  consultationFee: number | null;
  languages: string[];
  nextAvailableSlotSummary: string | null;
};

type SearchResponse = {
  clinics: Clinic[];
  doctors: Doctor[];
  specialities: string[];
};

type PatientPortalSession = {
  mode: "otp";
  tenantCode: string;
  tenantId: string;
  phone: string;
  patientLabel: string;
  createdAt: string;
  patientSessionToken: string;
};

type PatientPortalOtpRequestResponse = {
  accepted: boolean;
  message: string;
  expiresInSeconds: number;
  resendAvailableInSeconds: number;
  devOtp: string | null;
};

type PatientPortalOtpVerifyResponse = {
  verified: boolean;
  message: string;
  tenantId: string | null;
  patientDisplayName: string | null;
  patientSessionToken: string | null;
};

type PatientPortalMeResponse = {
  patientId: string;
  patientNumber: string;
  firstName: string;
  lastName: string;
  fullName: string;
  gender: string | null;
  dateOfBirth: string | null;
  ageYears: number | null;
  mobile: string | null;
  email: string | null;
  bloodGroup: string | null;
  allergies: string | null;
  existingConditions: string | null;
  longTermMedications: string | null;
  surgicalHistory: string | null;
};

type PatientPortalAppointmentResponse = {
  appointmentId: string;
  appointmentDate: string;
  appointmentTime: string | null;
  doctorName: string;
  reason: string | null;
  type: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

type PatientPortalPrescriptionMedicineResponse = {
  medicineName: string;
  medicineType: string | null;
  strength: string | null;
  dosage: string | null;
  frequency: string | null;
  duration: string | null;
  timing: string | null;
  instructions: string | null;
  sortOrder: number | null;
};

type PatientPortalPrescriptionTestResponse = {
  testName: string;
  instructions: string | null;
  sortOrder: number | null;
};

type PatientPortalPrescriptionResponse = {
  prescriptionId: string;
  prescriptionNumber: string;
  versionNumber: number | null;
  doctorName: string;
  diagnosisSnapshot: string | null;
  advice: string | null;
  followUpDate: string | null;
  status: string;
  finalizedAt: string | null;
  createdAt: string;
  updatedAt: string;
  medicines: PatientPortalPrescriptionMedicineResponse[];
  recommendedTests: PatientPortalPrescriptionTestResponse[];
};

type PatientPortalBillLineResponse = {
  itemType: string;
  itemName: string;
  quantity: number | null;
  unitPrice: number | null;
  totalPrice: number | null;
  lineDiscountAmount: number | null;
  sortOrder: number | null;
};

type PatientPortalBillResponse = {
  billId: string;
  billNumber: string;
  billDate: string;
  status: string;
  subtotalAmount: number | null;
  discountAmount: number | null;
  taxAmount: number | null;
  totalAmount: number | null;
  paidAmount: number | null;
  refundedAmount: number | null;
  dueAmount: number | null;
  createdAt: string;
  updatedAt: string;
  lines: PatientPortalBillLineResponse[];
};

type FetchState<T> = {
  data: T;
  loading: boolean;
  error: string | null;
};

const apiBaseUrl = import.meta.env.VITE_PUBLIC_API_BASE_URL?.trim() ?? "";
const clinicLoginUrl = "http://localhost:5174";
const patientSessionStorageKey = "clinic-web-public-patient-session";

const navItems = [
  { to: "/", label: "Home" },
  { to: "/doctors", label: "Doctors" },
  { to: "/clinics", label: "Clinics" },
  { to: "/specialities", label: "Specialities" },
  { to: "/careai", label: "CareAI" },
];

const patientNavItems = [
  { to: "/patient/dashboard", label: "Dashboard", shortLabel: "Home" },
  { to: "/patient/appointments", label: "Appointments", shortLabel: "Visits" },
  { to: "/patient/prescriptions", label: "Prescriptions", shortLabel: "Rx" },
  { to: "/patient/bills", label: "Bills", shortLabel: "Bills" },
  { to: "/patient/careai", label: "CareAI", shortLabel: "CareAI" },
  { to: "/patient/profile", label: "Profile", shortLabel: "Profile" },
];

function buildUrl(path: string, params?: Record<string, string | undefined>) {
  const url = new URL(`${apiBaseUrl}${path}`, window.location.origin);
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value && value.trim()) {
      url.searchParams.set(key, value.trim());
    }
  });
  return url.toString();
}

async function fetchJson<T>(
  path: string,
  params?: Record<string, string | undefined>,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(buildUrl(path, params), init);
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
  return response.json() as Promise<T>;
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  return fetchJson<T>(path, undefined, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
}

async function fetchPatientPortalJson<T>(path: string, session: PatientPortalSession | null): Promise<T> {
  const headers = new Headers();
  headers.set("Accept", "application/json");
  if (session) {
    headers.set("X-Patient-Session", session.patientSessionToken);
    headers.set("X-Tenant-Id", session.tenantId);
  }
  return fetchJson<T>(path, undefined, {
    credentials: "include",
    headers,
  });
}

function formatDate(value: string | null | undefined) {
  if (!value) {
    return "Not available yet";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(parsed);
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "Not available yet";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(parsed);
}

function formatTime(value: string | null | undefined) {
  if (!value) {
    return "Time to be confirmed";
  }
  const [hourText = "0", minuteText = "0"] = value.split(":");
  const hour = Number(hourText);
  const minute = Number(minuteText);
  if (Number.isNaN(hour) || Number.isNaN(minute)) {
    return value;
  }
  const date = new Date();
  date.setHours(hour, minute, 0, 0);
  return new Intl.DateTimeFormat(undefined, { timeStyle: "short" }).format(date);
}

function formatCurrency(value: number | null | undefined) {
  if (value == null) {
    return "Not available yet";
  }
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(value);
}

function formatStatusLabel(value: string | null | undefined) {
  if (!value) {
    return "Pending";
  }
  return value
    .toLowerCase()
    .split("_")
    .map((item) => item.charAt(0).toUpperCase() + item.slice(1))
    .join(" ");
}

function useAsyncData<T>(loader: () => Promise<T>, initialValue: T, deps: unknown[]) {
  const [data, setData] = useState<T>(initialValue);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    loader()
      .then((result) => {
        if (!cancelled) {
          setData(result);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load data");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, deps);

  return { data, loading, error };
}

function usePatientPortalData<T>(
  session: PatientPortalSession | null,
  path: string,
  initialValue: T,
): FetchState<T> {
  const [state, setState] = useState<FetchState<T>>({
    data: initialValue,
    loading: false,
    error: null,
  });

  useEffect(() => {
    let cancelled = false;

    if (!session) {
      setState({
        data: initialValue,
        loading: false,
        error: "Sign in to load your patient portal.",
      });
      return () => {
        cancelled = true;
      };
    }

    setState((current) => ({
      data: current.data,
      loading: true,
      error: null,
    }));

    fetchPatientPortalJson<T>(path, session)
      .then((result) => {
        if (!cancelled) {
          setState({
            data: result,
            loading: false,
            error: null,
          });
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({
            data: initialValue,
            loading: false,
            error: err instanceof Error ? err.message : "Unable to load portal data",
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [initialValue, path, session]);

  return state;
}

function readStoredPatientSession() {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(patientSessionStorageKey);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as PatientPortalSession;
    if (parsed?.mode === "otp" && parsed.patientSessionToken && parsed.tenantId && parsed.tenantCode) {
      return parsed;
    }
  } catch {
    window.localStorage.removeItem(patientSessionStorageKey);
  }
  return null;
}

function usePatientPortalSession() {
  const [session, setSession] = useState<PatientPortalSession | null>(() => readStoredPatientSession());

  function saveSession(nextSession: PatientPortalSession) {
    window.localStorage.setItem(patientSessionStorageKey, JSON.stringify(nextSession));
    setSession(nextSession);
  }

  function clearSession() {
    window.localStorage.removeItem(patientSessionStorageKey);
    setSession(null);
  }

  return { session, saveSession, clearSession };
}

function AppShell({ children }: { children: ReactNode }) {
  const location = useLocation();

  return (
    <div className="site-shell">
      <header className="site-header">
        <Link to="/" className="brand">
          <span className="brand-badge">CM</span>
          <span>
            <strong>Clinic Discovery</strong>
            <small>In-clinic care search for patients and families</small>
          </span>
        </Link>
        <nav className="main-nav" aria-label="Main navigation">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `nav-link${isActive || location.pathname === item.to ? " is-active" : ""}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="header-actions">
          <a className="ghost-button" href={clinicLoginUrl}>
            Clinic Login
          </a>
          <Link className="primary-button" to="/patient/login">
            Patient Login
          </Link>
        </div>
      </header>
      <main>{children}</main>
      <footer className="site-footer">
        <div>
          <strong>Clinic Discovery</strong>
          <p>Find in-clinic specialists, compare locations, and prepare for your next visit.</p>
        </div>
        <div>
          <p>Patient access is rolling out in phases.</p>
          <Link to="/patient/login">Open patient portal</Link>
        </div>
      </footer>
    </div>
  );
}

function HeroSearch() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [city, setCity] = useState("");

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (query.trim()) {
      params.set("q", query.trim());
    }
    if (city.trim()) {
      params.set("city", city.trim());
    }
    navigate(`/doctors?${params.toString()}`);
  }

  return (
    <section className="hero">
      <div className="hero-copy">
        <span className="eyebrow">Patient discovery foundation</span>
        <h1>Find the right doctor, clinic, or speciality for your next in-clinic appointment.</h1>
        <p>
          Search by city and speciality, review trusted clinic cards, and use CareAI guidance to shortlist the right
          in-clinic care option.
        </p>
        <div className="cta-row">
          <Link className="primary-button" to="/doctors">
            Find Doctor
          </Link>
          <Link className="secondary-button" to="/careai">
            Talk to CareAI
          </Link>
          <Link className="ghost-button" to="/patient/dashboard">
            Open patient portal
          </Link>
        </div>
      </div>
      <form className="hero-search-card" onSubmit={onSubmit}>
        <h2>Search care near you</h2>
        <label>
          <span>Location</span>
          <input value={city} onChange={(event) => setCity(event.target.value)} placeholder="City or locality" />
        </label>
        <label>
          <span>Doctor, clinic, or speciality</span>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Dermatology, pediatrics, Sunrise Clinic"
          />
        </label>
        <button className="primary-button wide-button" type="submit">
          Search in-clinic care
        </button>
      </form>
    </section>
  );
}

function HomePage() {
  const featuredClinics = useAsyncData<Clinic[]>(() => fetchJson("/api/public/clinics"), [], []);
  const featuredDoctors = useAsyncData<Doctor[]>(() => fetchJson("/api/public/doctors"), [], []);
  const specialities = useAsyncData<string[]>(() => fetchJson("/api/public/specialities"), [], []);

  return (
    <>
      <HeroSearch />
      <section className="content-section">
        <div className="section-heading">
          <span className="eyebrow">Popular specialities</span>
          <h2>Browse common in-clinic care needs</h2>
        </div>
        <div className="card-grid">
          {(specialities.data.length
            ? specialities.data.slice(0, 6)
            : ["General Medicine", "Pediatrics", "Dermatology", "ENT", "Dental Care", "Orthopedics"]).map((item) => (
            <Link key={item} className="feature-card" to={`/specialities?q=${encodeURIComponent(item)}`}>
              <strong>{item}</strong>
              <p>Explore doctors and clinics offering {item.toLowerCase()} care.</p>
            </Link>
          ))}
        </div>
      </section>

      <section className="content-section spotlight-grid">
        <div>
          <div className="section-heading">
            <span className="eyebrow">Featured clinics</span>
            <h2>Clinic cards built for quick comparisons</h2>
          </div>
          <CardState
            loading={featuredClinics.loading}
            error={featuredClinics.error}
            emptyMessage="Clinic listings will appear here soon."
            isEmpty={featuredClinics.data.length === 0}
          >
            <div className="stack-list">
              {featuredClinics.data.slice(0, 3).map((clinic) => (
                <article key={`${clinic.clinicDisplayName}-${clinic.city}`} className="info-card">
                  <h3>{clinic.clinicDisplayName}</h3>
                  <p>{clinic.locationLabel}</p>
                  <div className="chip-row">
                    {clinic.specialities.slice(0, 4).map((item) => (
                      <span key={item} className="chip">
                        {item}
                      </span>
                    ))}
                  </div>
                </article>
              ))}
            </div>
          </CardState>
        </div>
        <div>
          <div className="section-heading">
            <span className="eyebrow">Featured doctors</span>
            <h2>Doctor discovery stays card-based and mobile-first</h2>
          </div>
          <CardState
            loading={featuredDoctors.loading}
            error={featuredDoctors.error}
            emptyMessage="Doctor listings will appear here soon."
            isEmpty={featuredDoctors.data.length === 0}
          >
            <div className="stack-list">
              {featuredDoctors.data.slice(0, 3).map((doctor) => (
                <article key={`${doctor.doctorDisplayName}-${doctor.clinicDisplayName}`} className="info-card">
                  <h3>{doctor.doctorDisplayName}</h3>
                  <p>{doctor.speciality}</p>
                  <small>
                    {doctor.clinicDisplayName} · {doctor.city}
                  </small>
                </article>
              ))}
            </div>
          </CardState>
        </div>
      </section>

      <section className="content-section trust-section">
        <div className="section-heading">
          <span className="eyebrow">Trust and safety</span>
          <h2>Phase 1 keeps patient discovery separate from staff systems.</h2>
        </div>
        <div className="card-grid">
          <article className="feature-card">
            <strong>Read-only public catalog</strong>
            <p>Only public-safe clinic and doctor summary fields are surfaced in this phase.</p>
          </article>
          <article className="feature-card">
            <strong>Tenant-aware design</strong>
            <p>Listings are scoped to active tenant data and prepared for future clinic slug or domain resolution.</p>
          </article>
          <article className="feature-card">
            <strong>Patient portal access</strong>
            <p>The `/patient/*` routes now live inside this app with a separate patient OTP session boundary.</p>
          </article>
        </div>
      </section>

      <section className="content-section callback-panel" id="request-callback">
        <div>
          <span className="eyebrow">Patient portal</span>
          <h2>Use phone OTP to open the patient portal in local and testing environments.</h2>
          <p>Booking mutation remains out of scope. This phase is read-only and patient-safe.</p>
        </div>
        <Link className="secondary-button" to="/patient/login">
          Open patient access
        </Link>
      </section>
    </>
  );
}

function DoctorsPage() {
  const [searchParams] = useSearchParams();
  const q = searchParams.get("q") ?? "";
  const city = searchParams.get("city") ?? "";
  const speciality = searchParams.get("speciality") ?? "";
  const doctors = useAsyncData<Doctor[]>(
    () => fetchJson("/api/public/doctors", { q, city, speciality }),
    [],
    [q, city, speciality],
  );

  return (
    <ListingPageLayout
      eyebrow="Doctor discovery"
      title="Search doctors for your next in-clinic visit"
      description="Compare speciality, city, and clinic context without exposing private schedules or staff-only data."
      searchTarget="/doctors"
    >
      <CardState
        loading={doctors.loading}
        error={doctors.error}
        emptyMessage="No doctors match this search yet."
        isEmpty={doctors.data.length === 0}
      >
        <div className="card-grid">
          {doctors.data.map((doctor) => (
            <article key={`${doctor.doctorDisplayName}-${doctor.clinicDisplayName}`} className="info-card">
              <h3>{doctor.doctorDisplayName}</h3>
              <p>{doctor.speciality}</p>
              <small>
                {doctor.clinicDisplayName} · {doctor.city}
              </small>
              <div className="meta-block">
                <span>Fee visibility: available in a later public-listing phase</span>
                <span>Next slot summary: coming soon</span>
              </div>
            </article>
          ))}
        </div>
      </CardState>
    </ListingPageLayout>
  );
}

function ClinicsPage() {
  const [searchParams] = useSearchParams();
  const q = searchParams.get("q") ?? "";
  const city = searchParams.get("city") ?? "";
  const speciality = searchParams.get("speciality") ?? "";
  const clinics = useAsyncData<Clinic[]>(
    () => fetchJson("/api/public/clinics", { q, city, speciality }),
    [],
    [q, city, speciality],
  );

  return (
    <ListingPageLayout
      eyebrow="Clinic discovery"
      title="Browse clinics by city and speciality"
      description="Clinic cards stay concise, mobile-friendly, and focused on public-safe summary information."
      searchTarget="/clinics"
    >
      <CardState
        loading={clinics.loading}
        error={clinics.error}
        emptyMessage="No clinics match this search yet."
        isEmpty={clinics.data.length === 0}
      >
        <div className="card-grid">
          {clinics.data.map((clinic) => (
            <article key={`${clinic.clinicDisplayName}-${clinic.city}`} className="info-card">
              <h3>{clinic.clinicDisplayName}</h3>
              <p>{clinic.locationLabel}</p>
              <div className="chip-row">
                {clinic.specialities.map((item) => (
                  <span key={item} className="chip">
                    {item}
                  </span>
                ))}
              </div>
            </article>
          ))}
        </div>
      </CardState>
    </ListingPageLayout>
  );
}

function SpecialitiesPage() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get("q")?.toLowerCase() ?? "";
  const specialities = useAsyncData<string[]>(() => fetchJson("/api/public/specialities"), [], []);
  const filtered = useMemo(
    () => specialities.data.filter((item) => item.toLowerCase().includes(query)),
    [query, specialities.data],
  );

  return (
    <ListingPageLayout
      eyebrow="Specialities"
      title="Explore specialities offered across active clinics"
      description="Use speciality cards to jump into the clinic and doctor listing flows."
      searchTarget="/specialities"
    >
      <CardState
        loading={specialities.loading}
        error={specialities.error}
        emptyMessage="Specialities will appear here as clinics publish more doctor profiles."
        isEmpty={filtered.length === 0}
      >
        <div className="card-grid">
          {filtered.map((item) => (
            <Link key={item} className="feature-card" to={`/doctors?speciality=${encodeURIComponent(item)}`}>
              <strong>{item}</strong>
              <p>Open matching doctors and clinic cards for {item.toLowerCase()} care.</p>
            </Link>
          ))}
        </div>
      </CardState>
    </ListingPageLayout>
  );
}

function CareAiPage() {
  const search = useAsyncData<SearchResponse>(() => fetchJson("/api/public/search", { q: "care" }), {
    clinics: [],
    doctors: [],
    specialities: [],
  }, []);

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">CareAI foundation</span>
        <h1>CareAI helps patients narrow down in-clinic care options.</h1>
        <p>Phase 1 keeps this informational. Patient login is separate, and there is still no booking or appointment mutation here.</p>
      </div>
      <div className="careai-panel">
        <div>
          <h2>What CareAI can do next</h2>
          <ul className="plain-list">
            <li>Guide patients to the right speciality before they visit a clinic.</li>
            <li>Highlight public-safe clinic and doctor options in the right city.</li>
            <li>Prepare callback and patient-auth flows in later phases.</li>
          </ul>
        </div>
        <CardState
          loading={search.loading}
          error={search.error}
          emptyMessage="CareAI suggestions will appear here as public listings grow."
          isEmpty={search.data.specialities.length === 0}
        >
          <div className="info-card accent-card">
            <strong>{search.data.specialities[0] ?? "General Medicine"}</strong>
            <p>Suggested starting point for common in-clinic questions.</p>
          </div>
        </CardState>
      </div>
    </section>
  );
}

function PatientLoginPage({
  session,
  onSaveSession,
  onClearSession,
}: {
  session: PatientPortalSession | null;
  onSaveSession: (session: PatientPortalSession) => void;
  onClearSession: () => void;
}) {
  const navigate = useNavigate();
  const [tenantCode, setTenantCode] = useState("");
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [requestState, setRequestState] = useState<PatientPortalOtpRequestResponse | null>(null);
  const [requestMessage, setRequestMessage] = useState<string | null>(null);
  const [verifyMessage, setVerifyMessage] = useState<string | null>(null);
  const [requestPending, setRequestPending] = useState(false);
  const [verifyPending, setVerifyPending] = useState(false);

  useEffect(() => {
    if (session) {
      setTenantCode(session.tenantCode);
      setPhone(session.phone);
    }
  }, [session]);

  async function handleRequestOtp(event: FormEvent) {
    event.preventDefault();
    setRequestPending(true);
    setVerifyMessage(null);
    try {
      const response = await postJson<PatientPortalOtpRequestResponse>("/api/patient-portal/auth/otp/request", {
        tenantCode,
        phone,
      });
      setRequestState(response);
      setRequestMessage(response.message);
    } catch (error) {
      setRequestState(null);
      setRequestMessage(error instanceof Error ? error.message : "Unable to request OTP.");
    } finally {
      setRequestPending(false);
    }
  }

  async function handleVerifyOtp(event: FormEvent) {
    event.preventDefault();
    setVerifyPending(true);
    try {
      const response = await postJson<PatientPortalOtpVerifyResponse>("/api/patient-portal/auth/otp/verify", {
        tenantCode,
        phone,
        otp,
      });
      setVerifyMessage(response.message);
      if (response.verified && response.patientSessionToken && response.tenantId && response.patientDisplayName) {
        onSaveSession({
          mode: "otp",
          tenantCode: tenantCode.trim(),
          tenantId: response.tenantId,
          phone: phone.trim(),
          patientLabel: response.patientDisplayName,
          createdAt: new Date().toISOString(),
          patientSessionToken: response.patientSessionToken,
        });
        navigate("/patient/dashboard");
      }
    } catch (error) {
      setVerifyMessage(error instanceof Error ? error.message : "Unable to verify OTP.");
    } finally {
      setVerifyPending(false);
    }
  }

  return (
    <section className="page-section narrow-page">
      <div className="section-heading">
        <span className="eyebrow">Patient portal access</span>
        <h1>Sign in with clinic code, phone number, and OTP.</h1>
        <p>
          This login stays separate from staff and admin access. For local and docker testing, the backend can expose a
          development OTP while production keeps the code hidden.
        </p>
      </div>
      <div className="login-placeholder portal-login-card">
        <div className="portal-banner">
          <strong>Current phase</strong>
          <p>Patient portal access is read-only. Request an OTP, verify it, and open the mobile-first patient shell.</p>
        </div>
        <div className="portal-feature-list">
          <article className="feature-card">
            <strong>Read-only routes</strong>
            <p>Dashboard, appointments, prescriptions, bills, CareAI, and profile are all view-only.</p>
          </article>
          <article className="feature-card">
            <strong>Patient-safe cards</strong>
            <p>The shell uses compact cards and mobile navigation instead of staff-facing tables.</p>
          </article>
          <article className="feature-card">
            <strong>Tenant-aware session</strong>
            <p>Each page uses `/api/patient-portal/*` with a separate patient session token and tenant header.</p>
          </article>
        </div>
        <form className="patient-login-form" onSubmit={handleRequestOtp}>
          <label>
            <span>Clinic code</span>
            <input
              value={tenantCode}
              onChange={(event) => setTenantCode(event.target.value)}
              placeholder="clinic-demo"
              autoComplete="organization"
            />
          </label>
          <label>
            <span>Phone number</span>
            <input
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              placeholder="+91 98765 43210"
              autoComplete="tel"
            />
          </label>
          <button className="primary-button wide-button" type="submit" disabled={requestPending}>
            {requestPending ? "Requesting OTP..." : "Request OTP"}
          </button>
        </form>
        {requestMessage ? (
          <div className="patient-inline-empty">
            <strong>OTP request</strong>
            <p>{requestMessage}</p>
            {requestState?.accepted ? (
              <span>
                Code expires in {requestState.expiresInSeconds}s. Resend available in {requestState.resendAvailableInSeconds}s.
              </span>
            ) : null}
            {requestState?.devOtp ? (
              <span>Dev OTP: <strong>{requestState.devOtp}</strong></span>
            ) : null}
          </div>
        ) : null}
        <form className="patient-login-form" onSubmit={handleVerifyOtp}>
          <label>
            <span>OTP code</span>
            <input
              value={otp}
              onChange={(event) => setOtp(event.target.value)}
              placeholder="6-digit code"
              inputMode="numeric"
              autoComplete="one-time-code"
            />
          </label>
          <button className="secondary-button wide-button" type="submit" disabled={verifyPending}>
            {verifyPending ? "Verifying..." : "Verify and open portal"}
          </button>
        </form>
        {verifyMessage ? (
          <div className="patient-inline-empty">
            <strong>OTP verification</strong>
            <p>{verifyMessage}</p>
          </div>
        ) : null}
        <div className="cta-row">
          <a className="ghost-button" href={clinicLoginUrl}>
            Clinic Login
          </a>
          {session ? (
            <>
              <button className="secondary-button" type="button" onClick={() => navigate("/patient/dashboard")}>
                Open current session
              </button>
              <button className="ghost-button" type="button" onClick={onClearSession}>
                Clear patient session
              </button>
            </>
          ) : null}
        </div>
      </div>
    </section>
  );
}

function PatientPortalShell({
  session,
  title,
  subtitle,
  children,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  title: string;
  subtitle: string;
  children: ReactNode;
  onSignOut: () => void;
}) {
  const location = useLocation();

  return (
    <section className="page-section patient-portal-page">
      <div className="patient-portal-shell">
        <aside className="patient-sidebar">
          <div className="patient-sidebar-card">
            <span className="eyebrow">Patient portal</span>
            <h2>{session?.patientLabel ?? "Patient session required"}</h2>
            <p>This shell stays separate from staff and admin experiences inside `web-public`.</p>
          </div>
          <nav className="patient-nav" aria-label="Patient portal navigation">
            {patientNavItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `patient-nav-link${isActive || location.pathname === item.to ? " is-active" : ""}`
                }
              >
                <span>{item.label}</span>
              </NavLink>
            ))}
          </nav>
          <button className="ghost-button patient-signout" type="button" onClick={onSignOut}>
            Clear session
          </button>
        </aside>

        <div className="patient-main">
          <div className="patient-topbar">
            <div>
              <span className="eyebrow">Patient access</span>
              <h1>{title}</h1>
              <p>{subtitle}</p>
            </div>
            <div className="patient-status-card">
              <strong>Patient OTP session</strong>
              <small>{session?.tenantCode ?? "Clinic"} · {session?.phone ?? "Phone not available"}</small>
            </div>
          </div>

          <div className="portal-preview-banner">
            <strong>Safe rollout mode</strong>
            <p>
              Portal routes call `/api/patient-portal/*` with a patient-only session token. If data is unavailable,
              these views fall back to safe empty states instead of exposing internal records.
            </p>
          </div>

          {children}
        </div>
      </div>

      <nav className="patient-mobile-nav" aria-label="Patient portal mobile navigation">
        {patientNavItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => `patient-mobile-link${isActive || location.pathname === item.to ? " is-active" : ""}`}
          >
            {item.shortLabel}
          </NavLink>
        ))}
      </nav>
    </section>
  );
}

function PatientAccessBoundary({
  session,
  onSignOut,
  title,
  subtitle,
  children,
}: {
  session: PatientPortalSession | null;
  onSignOut: () => void;
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  if (!session) {
    return (
      <section className="page-section narrow-page">
        <div className="login-placeholder patient-guard-card">
          <span className="eyebrow">Patient login required</span>
          <h1>Sign in with phone OTP to open the patient portal.</h1>
          <p>
            The patient session is separate from public discovery pages and from staff or admin authentication.
          </p>
          <div className="cta-row">
            <Link className="primary-button" to="/patient/login">
              Go to patient login
            </Link>
            <Link className="ghost-button" to="/">
              Back to public home
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <PatientPortalShell session={session} title={title} subtitle={subtitle} onSignOut={onSignOut}>
      {children}
    </PatientPortalShell>
  );
}

function PatientPortalApiState({
  loading,
  error,
  empty,
  emptyTitle,
  emptyMessage,
  children,
}: {
  loading: boolean;
  error: string | null;
  empty: boolean;
  emptyTitle: string;
  emptyMessage: string;
  children: ReactNode;
}) {
  if (loading) {
    return <div className="patient-empty-card">Loading patient-safe portal data...</div>;
  }
  if (error) {
    return (
      <div className="patient-empty-card">
        <strong>Portal data is unavailable right now</strong>
        <p>{error}</p>
        <p>These views only show patient-safe data from `/api/patient-portal/*`.</p>
      </div>
    );
  }
  if (empty) {
    return (
      <div className="patient-empty-card">
        <strong>{emptyTitle}</strong>
        <p>{emptyMessage}</p>
      </div>
    );
  }
  return <>{children}</>;
}

function PatientDashboardPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const me = usePatientPortalData<PatientPortalMeResponse | null>(session, "/api/patient-portal/me", null);
  const appointments = usePatientPortalData<PatientPortalAppointmentResponse[]>(session, "/api/patient-portal/appointments", []);
  const prescriptions = usePatientPortalData<PatientPortalPrescriptionResponse[]>(session, "/api/patient-portal/prescriptions", []);
  const bills = usePatientPortalData<PatientPortalBillResponse[]>(session, "/api/patient-portal/bills", []);

  const hasConnectedData = Boolean(me.data) || appointments.data.length > 0 || prescriptions.data.length > 0 || bills.data.length > 0;
  const combinedLoading = me.loading || appointments.loading || prescriptions.loading || bills.loading;
  const combinedError = me.error ?? appointments.error ?? prescriptions.error ?? bills.error;

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Your care at a glance"
      subtitle="See upcoming visits, recent prescriptions, and open balances in a mobile-friendly patient shell."
    >
      <PatientPortalApiState
        loading={combinedLoading}
        error={combinedError}
        empty={!hasConnectedData}
        emptyTitle="Your dashboard will appear here"
        emptyMessage="Once the patient portal session is live, this page will show your safe summary cards."
      >
        <div className="patient-summary-grid">
          <article className="patient-stat-card">
            <span>Upcoming visits</span>
            <strong>{appointments.data.length}</strong>
            <small>Read-only for now. No booking mutation yet.</small>
          </article>
          <article className="patient-stat-card">
            <span>Prescription records</span>
            <strong>{prescriptions.data.length}</strong>
            <small>Medication and test summaries only.</small>
          </article>
          <article className="patient-stat-card">
            <span>Open balance</span>
            <strong>
              {formatCurrency(
                bills.data.reduce((sum, bill) => sum + (bill.dueAmount ?? 0), 0),
              )}
            </strong>
            <small>Billing ledger internals stay hidden.</small>
          </article>
        </div>

        <div className="patient-content-grid">
          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Patient snapshot</h2>
              <span>{me.data?.patientNumber ?? "Awaiting connected session"}</span>
            </div>
            <div className="patient-detail-list">
              <div>
                <strong>{me.data?.fullName ?? "Patient profile pending"}</strong>
                <span>{me.data?.email ?? "Email will appear once profile sync is enabled."}</span>
              </div>
              <div>
                <strong>Blood group</strong>
                <span>{me.data?.bloodGroup ?? "Not available yet"}</span>
              </div>
              <div>
                <strong>Long-term medications</strong>
                <span>{me.data?.longTermMedications ?? "No long-term medication summary available yet."}</span>
              </div>
            </div>
          </article>

          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Next appointment</h2>
              <Link to="/patient/appointments">View all</Link>
            </div>
            {appointments.data[0] ? (
              <div className="patient-highlight-card">
                <strong>{appointments.data[0].doctorName}</strong>
                <span>{formatDate(appointments.data[0].appointmentDate)} · {formatTime(appointments.data[0].appointmentTime)}</span>
                <p>{appointments.data[0].reason ?? "Reason will appear after clinic confirmation."}</p>
              </div>
            ) : (
              <div className="patient-inline-empty">
                No appointments yet. Booking actions will be added in a later phase.
              </div>
            )}
          </article>

          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Recent prescription</h2>
              <Link to="/patient/prescriptions">View all</Link>
            </div>
            {prescriptions.data[0] ? (
              <div className="patient-highlight-card">
                <strong>{prescriptions.data[0].prescriptionNumber}</strong>
                <span>{prescriptions.data[0].doctorName} · {formatStatusLabel(prescriptions.data[0].status)}</span>
                <p>{prescriptions.data[0].advice ?? "Advice will appear once available."}</p>
              </div>
            ) : (
              <div className="patient-inline-empty">
                Prescriptions will appear here after your clinic finalizes them.
              </div>
            )}
          </article>

          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Billing overview</h2>
              <Link to="/patient/bills">View all</Link>
            </div>
            {bills.data[0] ? (
              <div className="patient-highlight-card">
                <strong>{bills.data[0].billNumber}</strong>
                <span>{formatDate(bills.data[0].billDate)} · {formatStatusLabel(bills.data[0].status)}</span>
                <p>Due amount: {formatCurrency(bills.data[0].dueAmount)}</p>
              </div>
            ) : (
              <div className="patient-inline-empty">
                Bills will appear here when a patient-safe billing summary is ready.
              </div>
            )}
          </article>
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

function PatientAppointmentsPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const appointments = usePatientPortalData<PatientPortalAppointmentResponse[]>(session, "/api/patient-portal/appointments", []);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Appointments"
      subtitle="Read-only visit cards optimized for phones, not staff scheduling tables."
    >
      <PatientPortalApiState
        loading={appointments.loading}
        error={appointments.error}
        empty={appointments.data.length === 0}
        emptyTitle="No appointments yet"
        emptyMessage="Your future in-clinic visits will appear here once the clinic links them to your portal."
      >
        <div className="patient-card-stack">
          {appointments.data.map((appointment) => (
            <article key={appointment.appointmentId} className="patient-record-card">
              <div className="record-card-top">
                <div>
                  <strong>{appointment.doctorName}</strong>
                  <span>{formatDate(appointment.appointmentDate)} · {formatTime(appointment.appointmentTime)}</span>
                </div>
                <span className="status-pill">{formatStatusLabel(appointment.status)}</span>
              </div>
              <p>{appointment.reason ?? "Reason will appear after the clinic confirms the visit details."}</p>
              <div className="record-card-meta">
                <span>Visit type: {formatStatusLabel(appointment.type)}</span>
                <span>Updated {formatDateTime(appointment.updatedAt)}</span>
              </div>
            </article>
          ))}
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

function PatientPrescriptionsPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const prescriptions = usePatientPortalData<PatientPortalPrescriptionResponse[]>(session, "/api/patient-portal/prescriptions", []);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Prescriptions"
      subtitle="Medication and test summaries stay patient-safe and easy to scan on a phone."
    >
      <PatientPortalApiState
        loading={prescriptions.loading}
        error={prescriptions.error}
        empty={prescriptions.data.length === 0}
        emptyTitle="No prescriptions yet"
        emptyMessage="Finalized prescriptions will show up here with medicine and test guidance once they are available."
      >
        <div className="patient-card-stack">
          {prescriptions.data.map((prescription) => (
            <article key={prescription.prescriptionId} className="patient-record-card">
              <div className="record-card-top">
                <div>
                  <strong>{prescription.prescriptionNumber}</strong>
                  <span>{prescription.doctorName}</span>
                </div>
                <span className="status-pill">{formatStatusLabel(prescription.status)}</span>
              </div>
              <p>{prescription.diagnosisSnapshot ?? "Diagnosis summary is not available yet."}</p>
              <div className="patient-prescription-grid">
                <div>
                  <h3>Medicines</h3>
                  {prescription.medicines.length ? (
                    <div className="patient-subcard-list">
                      {prescription.medicines.map((medicine) => (
                        <div key={`${prescription.prescriptionId}-${medicine.medicineName}`} className="patient-subcard">
                          <strong>{medicine.medicineName}</strong>
                          <span>
                            {[medicine.dosage, medicine.frequency, medicine.duration].filter(Boolean).join(" · ") || "Instructions pending"}
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="patient-inline-empty">No medicine rows were added to this prescription.</div>
                  )}
                </div>
                <div>
                  <h3>Recommended tests</h3>
                  {prescription.recommendedTests.length ? (
                    <div className="patient-subcard-list">
                      {prescription.recommendedTests.map((test) => (
                        <div key={`${prescription.prescriptionId}-${test.testName}`} className="patient-subcard">
                          <strong>{test.testName}</strong>
                          <span>{test.instructions ?? "No extra instructions"}</span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="patient-inline-empty">No tests recommended for this prescription.</div>
                  )}
                </div>
              </div>
              <div className="record-card-meta">
                <span>Follow-up: {formatDate(prescription.followUpDate)}</span>
                <span>Updated {formatDateTime(prescription.updatedAt)}</span>
              </div>
            </article>
          ))}
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

function PatientBillsPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const bills = usePatientPortalData<PatientPortalBillResponse[]>(session, "/api/patient-portal/bills", []);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Bills"
      subtitle="See patient-safe totals and line summaries without exposing internal billing ledger details."
    >
      <PatientPortalApiState
        loading={bills.loading}
        error={bills.error}
        empty={bills.data.length === 0}
        emptyTitle="No bills yet"
        emptyMessage="Patient-safe bill summaries will appear here when the clinic publishes them to your portal."
      >
        <div className="patient-card-stack">
          {bills.data.map((bill) => (
            <article key={bill.billId} className="patient-record-card">
              <div className="record-card-top">
                <div>
                  <strong>{bill.billNumber}</strong>
                  <span>{formatDate(bill.billDate)}</span>
                </div>
                <span className="status-pill">{formatStatusLabel(bill.status)}</span>
              </div>
              <div className="patient-bill-summary">
                <div>
                  <strong>{formatCurrency(bill.totalAmount)}</strong>
                  <span>Total</span>
                </div>
                <div>
                  <strong>{formatCurrency(bill.paidAmount)}</strong>
                  <span>Paid</span>
                </div>
                <div>
                  <strong>{formatCurrency(bill.dueAmount)}</strong>
                  <span>Due</span>
                </div>
              </div>
              {bill.lines.length ? (
                <div className="patient-subcard-list">
                  {bill.lines.map((line) => (
                    <div key={`${bill.billId}-${line.itemName}-${line.sortOrder ?? 0}`} className="patient-subcard">
                      <strong>{line.itemName}</strong>
                      <span>
                        {formatStatusLabel(line.itemType)} · Qty {line.quantity ?? 0} · {formatCurrency(line.totalPrice)}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="patient-inline-empty">Line-item details are not available yet for this bill.</div>
              )}
            </article>
          ))}
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

function PatientCareAiPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const me = usePatientPortalData<PatientPortalMeResponse | null>(session, "/api/patient-portal/me", null);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Patient CareAI"
      subtitle="CareAI stays informational here and does not book, mutate, or create appointments yet."
    >
      <div className="patient-content-grid">
        <article className="patient-panel">
          <div className="patient-panel-heading">
            <h2>What this page can do now</h2>
          </div>
          <ul className="plain-list">
            <li>Summarize your next steps before a visit.</li>
            <li>Point you to prescription or billing cards already in your portal.</li>
            <li>Stay read-only until patient authentication and booking actions are added later.</li>
          </ul>
        </article>

        <article className="patient-panel">
          <div className="patient-panel-heading">
            <h2>Suggested conversation starters</h2>
          </div>
          <div className="patient-subcard-list">
            <div className="patient-subcard">
              <strong>Review my medicines</strong>
              <span>Summarize instructions from my latest prescription in plain language.</span>
            </div>
            <div className="patient-subcard">
              <strong>Help me prepare</strong>
              <span>What should I carry for my next in-clinic appointment?</span>
            </div>
            <div className="patient-subcard">
              <strong>Explain my bill</strong>
              <span>Walk me through high-level bill totals without exposing internal billing notes.</span>
            </div>
          </div>
        </article>

        <article className="patient-panel patient-panel-wide">
          <div className="patient-panel-heading">
            <h2>Personalized context</h2>
          </div>
          <PatientPortalApiState
            loading={me.loading}
            error={me.error}
            empty={!me.data}
            emptyTitle="Personal context is not connected yet"
            emptyMessage="Once your patient session is live, CareAI can tailor prompts using your safe profile summary."
          >
            <div className="patient-highlight-card">
              <strong>{me.data?.fullName}</strong>
              <span>Age {me.data?.ageYears ?? "not available"} · {me.data?.bloodGroup ?? "Blood group unavailable"}</span>
              <p>
                Current medication summary: {me.data?.longTermMedications ?? "No long-term medication summary available yet."}
              </p>
            </div>
          </PatientPortalApiState>
        </article>
      </div>
    </PatientAccessBoundary>
  );
}

function PatientProfilePage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const me = usePatientPortalData<PatientPortalMeResponse | null>(session, "/api/patient-portal/me", null);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Profile"
      subtitle="Only patient-safe profile data appears here. Internal notes and staff-only fields stay out."
    >
      <PatientPortalApiState
        loading={me.loading}
        error={me.error}
        empty={!me.data}
        emptyTitle="Your profile is not available yet"
        emptyMessage="The portal will show your patient-safe demographic and history summary once the session is connected."
      >
        <div className="patient-content-grid">
          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Identity</h2>
            </div>
            <div className="patient-detail-list">
              <div>
                <strong>{me.data?.fullName}</strong>
                <span>Patient number {me.data?.patientNumber}</span>
              </div>
              <div>
                <strong>Mobile</strong>
                <span>{me.data?.mobile ?? "Not available yet"}</span>
              </div>
              <div>
                <strong>Email</strong>
                <span>{me.data?.email ?? "Not available yet"}</span>
              </div>
            </div>
          </article>

          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Health summary</h2>
            </div>
            <div className="patient-detail-list">
              <div>
                <strong>Allergies</strong>
                <span>{me.data?.allergies ?? "No allergy summary available yet"}</span>
              </div>
              <div>
                <strong>Existing conditions</strong>
                <span>{me.data?.existingConditions ?? "No condition summary available yet"}</span>
              </div>
              <div>
                <strong>Surgical history</strong>
                <span>{me.data?.surgicalHistory ?? "No surgical history summary available yet"}</span>
              </div>
            </div>
          </article>
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

function ListingPageLayout({
  eyebrow,
  title,
  description,
  searchTarget,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  searchTarget: string;
  children: ReactNode;
}) {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [city, setCity] = useState("");

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (query.trim()) {
      params.set("q", query.trim());
    }
    if (city.trim()) {
      params.set("city", city.trim());
    }
    navigate(`${searchTarget}?${params.toString()}`);
  }

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <form className="toolbar-card" onSubmit={onSubmit}>
        <input value={city} onChange={(event) => setCity(event.target.value)} placeholder="Filter by city" />
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search clinic, doctor, or speciality" />
        <button className="primary-button" type="submit">
          Apply
        </button>
      </form>
      {children}
    </section>
  );
}

function CardState({
  loading,
  error,
  emptyMessage,
  isEmpty,
  children,
}: {
  loading: boolean;
  error: string | null;
  emptyMessage: string;
  isEmpty: boolean;
  children: ReactNode;
}) {
  if (loading) {
    return <div className="state-card">Loading public listing data...</div>;
  }
  if (error) {
    return <div className="state-card">Unable to load public data: {error}</div>;
  }
  if (isEmpty) {
    return <div className="state-card">{emptyMessage}</div>;
  }
  return <>{children}</>;
}

export function App() {
  const { session, saveSession, clearSession } = usePatientPortalSession();

  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/doctors" element={<DoctorsPage />} />
        <Route path="/clinics" element={<ClinicsPage />} />
        <Route path="/specialities" element={<SpecialitiesPage />} />
        <Route path="/careai" element={<CareAiPage />} />
        <Route
          path="/patient/login"
          element={
            <PatientLoginPage
              session={session}
              onSaveSession={saveSession}
              onClearSession={clearSession}
            />
          }
        />
        <Route
          path="/patient/dashboard"
          element={<PatientDashboardPage session={session} onSignOut={clearSession} />}
        />
        <Route
          path="/patient/appointments"
          element={<PatientAppointmentsPage session={session} onSignOut={clearSession} />}
        />
        <Route
          path="/patient/prescriptions"
          element={<PatientPrescriptionsPage session={session} onSignOut={clearSession} />}
        />
        <Route path="/patient/bills" element={<PatientBillsPage session={session} onSignOut={clearSession} />} />
        <Route
          path="/patient/careai"
          element={<PatientCareAiPage session={session} onSignOut={clearSession} />}
        />
        <Route
          path="/patient/profile"
          element={<PatientProfilePage session={session} onSignOut={clearSession} />}
        />
      </Routes>
    </AppShell>
  );
}
