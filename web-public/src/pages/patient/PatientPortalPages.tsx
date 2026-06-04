import { type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  type PatientPortalAppointmentResponse,
  type PatientPortalAppointmentBookingRequest,
  type PatientPortalAppointmentConfirmationResponse,
  type PatientPortalCareAiMessageRequest,
  type PatientPortalCareAiMessageResponse,
  type PatientPortalCareAiResetResponse,
  type PatientPortalCareAiStateResponse,
  type PatientPortalBillResponse,
  type PatientPortalDashboardResponse,
  type PatientPortalDoctorResponse,
  type PatientPortalDoctorSlotResponse,
  type PatientPortalMeResponse,
  type PatientPortalOtpRequestResponse,
  type PatientPortalPatientSession,
  type PatientPortalProfileUpdateRequest,
  type PatientPortalRegistrationRequest,
  type PatientPortalRegistrationResponse,
  type PatientPortalRegistrationSession,
  type PatientPortalOtpVerifyResponse,
  type PatientPortalPrescriptionResponse,
  type PatientPortalSession,
  fetchPatientPortalJson,
  isPatientPortalPatientSession,
  isPatientPortalRegistrationSession,
  openPatientPortalPdf,
  patientPortalHomePath,
  postPatientPortalJson,
  postPatientPortalSessionJson,
  putPatientPortalSessionJson,
} from "../../api/patientPortal";

type FetchState<T> = {
  data: T;
  loading: boolean;
  error: string | null;
};

type PatientCareAiChatEntry = {
  id: string;
  role: "patient" | "assistant";
  text: string;
};

const patientNavItems = [
  { to: "/patient/dashboard", label: "Dashboard", shortLabel: "Home" },
  { to: "/patient/book-appointment", label: "Book Visit", shortLabel: "Book" },
  { to: "/patient/appointments", label: "Appointments", shortLabel: "Visits" },
  { to: "/patient/prescriptions", label: "Prescriptions", shortLabel: "Rx" },
  { to: "/patient/bills", label: "Bills", shortLabel: "Bills" },
  { to: "/patient/careai", label: "CareAI", shortLabel: "CareAI" },
  { to: "/patient/profile", label: "Profile", shortLabel: "Profile" },
];

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

function usePatientPortalResource<T>(
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
    const abortController = new AbortController();

    if (!session) {
      setState({
        data: initialValue,
        loading: false,
        error: "Sign in with your patient OTP to load portal data.",
      });
      return () => abortController.abort();
    }

    setState((current) => ({
      data: current.data,
      loading: true,
      error: null,
    }));

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
          data: initialValue,
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load patient portal data.",
        });
      });

    return () => abortController.abort();
  }, [path, session]);

  return state;
}

function PatientPortalShell({
  session,
  title,
  subtitle,
  children,
  onSignOut,
}: {
  session: PatientPortalPatientSession;
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
            <h2>{session.patientLabel}</h2>
            <p>Read-only patient access stays separate from web-admin and staff workflows.</p>
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
              <span className="eyebrow">Verified patient access</span>
              <h1>{title}</h1>
              <p>{subtitle}</p>
            </div>
            <div className="patient-status-card">
              <strong>Patient OTP session</strong>
              <small>
                {session.tenantCode} · {session.phone}
              </small>
            </div>
          </div>

          <div className="portal-preview-banner">
            <strong>Tenant-aware patient data</strong>
            <p>
              These pages only use `/api/patient-portal/*`, which resolves the patient from the verified session and
              keeps cross-patient and cross-tenant records out of view.
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
            className={({ isActive }) =>
              `patient-mobile-link${isActive || location.pathname === item.to ? " is-active" : ""}`
            }
          >
            {item.shortLabel}
          </NavLink>
        ))}
      </nav>
    </section>
  );
}

function RegistrationRequiredCard({
  onClearSession,
  nextPath,
}: {
  onClearSession: () => void;
  nextPath?: string;
}) {
  return (
    <section className="page-section narrow-page">
      <div className="login-placeholder patient-guard-card">
        <span className="eyebrow">Registration required</span>
        <h1>Finish quick registration to open the patient portal.</h1>
        <p>Your OTP is verified for this clinic, but the portal only unlocks after your tenant-scoped patient profile is linked.</p>
        <div className="cta-row">
          <Link className="primary-button" to={nextPath ? `/patient/register?next=${encodeURIComponent(nextPath)}` : "/patient/register"}>
            Continue registration
          </Link>
          <button className="ghost-button" type="button" onClick={onClearSession}>
            Clear patient session
          </button>
        </div>
      </div>
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
          <p>The patient session stays separate from public discovery and from all staff/admin authentication.</p>
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

  if (!isPatientPortalPatientSession(session)) {
    return <RegistrationRequiredCard onClearSession={onSignOut} />;
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
    return <div className="patient-empty-card">Loading patient-scoped data...</div>;
  }
  if (error) {
    return (
      <div className="patient-empty-card">
        <strong>Portal data is unavailable right now</strong>
        <p>{error}</p>
        <p>Only patient-safe fields are exposed here.</p>
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

async function openPortalDocument(
  session: PatientPortalSession | null,
  path: string,
  setBusy: (value: string | null) => void,
  busyKey: string,
  setError: (value: string | null) => void,
) {
  if (!session) {
    setError("Sign in again to open patient documents.");
    return;
  }
  setBusy(busyKey);
  setError(null);
  try {
    await openPatientPortalPdf(path, session);
  } catch (error) {
    setError(error instanceof Error ? error.message : "Unable to open the document.");
  } finally {
    setBusy(null);
  }
}

export function PatientLoginPage({
  session,
  onSaveSession,
  onClearSession,
  clinicLoginUrl,
}: {
  session: PatientPortalSession | null;
  onSaveSession: (session: PatientPortalSession) => void;
  onClearSession: () => void;
  clinicLoginUrl: string;
}) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tenantCode, setTenantCode] = useState("");
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [requestState, setRequestState] = useState<PatientPortalOtpRequestResponse | null>(null);
  const [requestMessage, setRequestMessage] = useState<string | null>(null);
  const [verifyMessage, setVerifyMessage] = useState<string | null>(null);
  const [requestPending, setRequestPending] = useState(false);
  const [verifyPending, setVerifyPending] = useState(false);

  useEffect(() => {
    if (!session) {
      return;
    }
    setTenantCode(session.tenantCode);
    setPhone(session.phone);
  }, [session]);

  async function handleRequestOtp(event: FormEvent) {
    event.preventDefault();
    setRequestPending(true);
    setVerifyMessage(null);
    try {
      const response = await postPatientPortalJson<PatientPortalOtpRequestResponse>("/api/patient-portal/auth/otp/request", {
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
      const response = await postPatientPortalJson<PatientPortalOtpVerifyResponse>("/api/patient-portal/auth/otp/verify", {
        tenantCode,
        phone,
        otp,
      });
      setVerifyMessage(response.message);
      if (response.verified && response.patientSessionToken && response.tenantId && response.patientDisplayName) {
        onSaveSession({
          mode: "otp",
          sessionRole: "patient",
          tenantCode: tenantCode.trim(),
          tenantId: response.tenantId,
          phone: phone.trim(),
          patientLabel: response.patientDisplayName,
          createdAt: new Date().toISOString(),
          patientSessionToken: response.patientSessionToken,
        });
        const nextPath = searchParams.get("next");
        navigate(nextPath?.startsWith("/patient/") ? nextPath : "/patient/dashboard");
        return;
      }
      if (response.verified && response.registrationRequired && response.registrationSessionToken && response.tenantId) {
        onSaveSession({
          mode: "otp",
          sessionRole: "registration",
          tenantCode: tenantCode.trim(),
          tenantId: response.tenantId,
          phone: phone.trim(),
          patientLabel: "New patient",
          createdAt: new Date().toISOString(),
          patientSessionToken: response.registrationSessionToken,
        });
        const nextPath = searchParams.get("next");
        navigate(nextPath?.startsWith("/patient/") ? `/patient/register?next=${encodeURIComponent(nextPath)}` : "/patient/register");
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
        <p>This stays separate from staff/admin access and uses the patient-only session boundary.</p>
      </div>
      <div className="login-placeholder portal-login-card">
        <div className="portal-banner">
          <strong>Read-only patient rollout</strong>
          <p>OTP sign-in unlocks patient-scoped appointments, prescriptions, bills, profile details, and CareAI entry points.</p>
        </div>
        <div className="portal-feature-list">
          <article className="feature-card">
            <strong>Tenant-aware session</strong>
            <p>The backend resolves the patient from the verified session, not from arbitrary patient IDs.</p>
          </article>
          <article className="feature-card">
            <strong>Patient-safe cards</strong>
            <p>Mobile-first cards show only safe fields, with clear empty and error states.</p>
          </article>
          <article className="feature-card">
            <strong>Separate from admin</strong>
            <p>Patient access remains isolated from receptionist, doctor, and billing workflows.</p>
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
              <span>
                Dev OTP: <strong>{requestState.devOtp}</strong>
              </span>
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
            {verifyPending ? "Verifying..." : "Verify and continue"}
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
              <button className="secondary-button" type="button" onClick={() => navigate(patientPortalHomePath(session))}>
                {isPatientPortalRegistrationSession(session) ? "Continue registration" : "Open current session"}
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

export function PatientRegistrationPage({
  session,
  onSaveSession,
  onClearSession,
}: {
  session: PatientPortalSession | null;
  onSaveSession: (session: PatientPortalSession) => void;
  onClearSession: () => void;
}) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [gender, setGender] = useState("UNKNOWN");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [ageYears, setAgeYears] = useState("");
  const [city, setCity] = useState("");
  const [email, setEmail] = useState("");
  const [addressLine1, setAddressLine1] = useState("");
  const [addressLine2, setAddressLine2] = useState("");
  const [state, setState] = useState("");
  const [country, setCountry] = useState("India");
  const [postalCode, setPostalCode] = useState("");
  const [emergencyContactName, setEmergencyContactName] = useState("");
  const [emergencyContactMobile, setEmergencyContactMobile] = useState("");
  const [submitPending, setSubmitPending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isPatientPortalPatientSession(session)) {
      return;
    }
    const nextPath = searchParams.get("next");
    navigate(nextPath?.startsWith("/patient/") ? nextPath : "/patient/dashboard");
  }, [navigate, searchParams, session]);

  if (!session) {
    return (
      <section className="page-section narrow-page">
        <div className="login-placeholder patient-guard-card">
          <span className="eyebrow">Patient login required</span>
          <h1>Verify your mobile number first.</h1>
          <p>Quick registration only opens after OTP verification for the selected clinic code.</p>
          <div className="cta-row">
            <Link className="primary-button" to="/patient/login">
              Go to patient login
            </Link>
          </div>
        </div>
      </section>
    );
  }

  if (!isPatientPortalRegistrationSession(session)) {
    return null;
  }
  const registrationSession = session;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!dateOfBirth && !ageYears.trim()) {
      setError("Date of birth or age is required.");
      return;
    }

    setSubmitPending(true);
    setError(null);
    setMessage(null);
    try {
      const payload: PatientPortalRegistrationRequest = {
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        gender,
        dateOfBirth: dateOfBirth || null,
        ageYears: ageYears.trim() ? Number(ageYears.trim()) : null,
        email: email.trim() || null,
        addressLine1: addressLine1.trim() || null,
        addressLine2: addressLine2.trim() || null,
        city: city.trim(),
        state: state.trim() || null,
        country: country.trim() || null,
        postalCode: postalCode.trim() || null,
        emergencyContactName: emergencyContactName.trim() || null,
        emergencyContactMobile: emergencyContactMobile.trim() || null,
      };
      const response = await postPatientPortalSessionJson<PatientPortalRegistrationResponse>(
        "/api/patient-portal/registration/complete",
        payload,
        registrationSession,
      );
      onSaveSession({
        mode: "otp",
        sessionRole: "patient",
        tenantCode: registrationSession.tenantCode,
        tenantId: response.tenantId,
        phone: registrationSession.phone,
        patientLabel: response.patientDisplayName,
        createdAt: new Date().toISOString(),
        patientSessionToken: response.patientSessionToken,
      });
      setMessage(response.message);
      const nextPath = searchParams.get("next");
      navigate(nextPath?.startsWith("/patient/") ? nextPath : "/patient/dashboard");
    } catch (submitError: unknown) {
      setError(submitError instanceof Error ? submitError.message : "Unable to complete patient registration.");
    } finally {
      setSubmitPending(false);
    }
  }

  return (
    <section className="page-section narrow-page">
      <div className="section-heading">
        <span className="eyebrow">Quick registration</span>
        <h1>Finish your first-time patient onboarding.</h1>
        <p>Step 1 is already complete. Add only the patient-safe details needed to create or link your clinic record and continue booking.</p>
      </div>
      <div className="login-placeholder portal-login-card">
        <div className="portal-feature-list portal-step-list">
          <article className="feature-card">
            <strong>Step 1</strong>
            <p>Clinic code and OTP verified for {session.tenantCode}.</p>
          </article>
          <article className="feature-card">
            <strong>Step 2</strong>
            <p>Complete quick registration or link an existing patient record in the same tenant.</p>
          </article>
          <article className="feature-card">
            <strong>Step 3</strong>
            <p>Open the portal or continue straight into appointment booking without signing in again.</p>
          </article>
        </div>

        <form className="patient-login-form patient-registration-form" onSubmit={handleSubmit}>
          <div className="patient-form-grid">
            <label>
              <span>First name</span>
              <input value={firstName} onChange={(event) => setFirstName(event.target.value)} required autoComplete="given-name" />
            </label>
            <label>
              <span>Last name</span>
              <input value={lastName} onChange={(event) => setLastName(event.target.value)} required autoComplete="family-name" />
            </label>
            <label>
              <span>Gender</span>
              <select value={gender} onChange={(event) => setGender(event.target.value)}>
                <option value="UNKNOWN">Prefer not to say</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
            </label>
            <label>
              <span>Date of birth</span>
              <input type="date" value={dateOfBirth} onChange={(event) => setDateOfBirth(event.target.value)} max={new Date().toISOString().slice(0, 10)} />
            </label>
            <label>
              <span>Age</span>
              <input value={ageYears} onChange={(event) => setAgeYears(event.target.value)} inputMode="numeric" placeholder="If DOB is not available" />
            </label>
            <label>
              <span>Mobile number</span>
              <input value={session.phone} readOnly />
            </label>
            <label>
              <span>City</span>
              <input value={city} onChange={(event) => setCity(event.target.value)} required autoComplete="address-level2" />
            </label>
            <label>
              <span>Email</span>
              <input value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" />
            </label>
            <label className="patient-form-span-2">
              <span>Address line 1</span>
              <input value={addressLine1} onChange={(event) => setAddressLine1(event.target.value)} autoComplete="address-line1" />
            </label>
            <label className="patient-form-span-2">
              <span>Address line 2</span>
              <input value={addressLine2} onChange={(event) => setAddressLine2(event.target.value)} autoComplete="address-line2" />
            </label>
            <label>
              <span>State</span>
              <input value={state} onChange={(event) => setState(event.target.value)} autoComplete="address-level1" />
            </label>
            <label>
              <span>Country</span>
              <input value={country} onChange={(event) => setCountry(event.target.value)} autoComplete="country-name" />
            </label>
            <label>
              <span>Postal code</span>
              <input value={postalCode} onChange={(event) => setPostalCode(event.target.value)} autoComplete="postal-code" />
            </label>
            <label>
              <span>Emergency contact</span>
              <input value={emergencyContactName} onChange={(event) => setEmergencyContactName(event.target.value)} />
            </label>
            <label>
              <span>Emergency mobile</span>
              <input value={emergencyContactMobile} onChange={(event) => setEmergencyContactMobile(event.target.value)} inputMode="tel" />
            </label>
          </div>
          {message ? (
            <div className="patient-success-card">
              <strong>Registration complete</strong>
              <p>{message}</p>
            </div>
          ) : null}
          {error ? (
            <div className="patient-inline-empty">
              <strong>Registration unavailable</strong>
              <p>{error}</p>
            </div>
          ) : null}
          <div className="patient-action-row">
            <button className="primary-button" type="submit" disabled={submitPending}>
              {submitPending ? "Completing..." : "Complete registration"}
            </button>
            <button className="ghost-button" type="button" onClick={onClearSession}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </section>
  );
}

export function PatientDashboardPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const dashboard = usePatientPortalResource<PatientPortalDashboardResponse | null>(portalSession, "/api/patient-portal/dashboard", null);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Your care at a glance"
      subtitle="See the next visit, latest prescription, unpaid dues, and recent bill activity without exposing staff-only data."
    >
      <PatientPortalApiState
        loading={dashboard.loading}
        error={dashboard.error}
        empty={!dashboard.data}
        emptyTitle="Your dashboard is not ready yet"
        emptyMessage="Patient-safe summaries will appear here once your clinic has linked your records to this portal session."
      >
        <div className="patient-summary-grid">
          <article className="patient-stat-card">
            <span>Next visit</span>
            <strong>{dashboard.data?.nextAppointment ? formatDate(dashboard.data.nextAppointment.appointmentDate) : "None"}</strong>
            <small>{dashboard.data?.nextAppointment?.doctorName ?? "No upcoming appointment is linked yet."}</small>
            <Link className="text-link" to="/patient/book-appointment">
              Book appointment
            </Link>
          </article>
          <article className="patient-stat-card">
            <span>Latest prescription</span>
            <strong>{dashboard.data?.recentPrescription?.prescriptionNumber ?? "Not available"}</strong>
            <small>{dashboard.data?.recentPrescription?.doctorName ?? "No prescription has been published yet."}</small>
          </article>
          <article className="patient-stat-card">
            <span>Unpaid due</span>
            <strong>{formatCurrency(dashboard.data?.unpaidDueAmount ?? null)}</strong>
            <small>Billing totals only. Ledger internals remain hidden.</small>
          </article>
        </div>

        <div className="patient-content-grid">
          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Next upcoming appointment</h2>
              <div className="panel-link-row">
                <Link to="/patient/book-appointment">Book new</Link>
                <Link to="/patient/appointments">View all</Link>
              </div>
            </div>
            {dashboard.data?.nextAppointment ? (
              <div className="patient-highlight-card">
                <strong>{dashboard.data.nextAppointment.doctorName ?? "Doctor to be confirmed"}</strong>
                <span>
                  {dashboard.data.nextAppointment.clinicName ?? dashboard.data.clinicName} · {formatDate(dashboard.data.nextAppointment.appointmentDate)} ·{" "}
                  {formatTime(dashboard.data.nextAppointment.appointmentTime)}
                </span>
                <p>{dashboard.data.nextAppointment.reason ?? "Visit reason will appear when the clinic shares it safely."}</p>
              </div>
            ) : (
              <div className="patient-inline-empty">No upcoming appointment has been linked to your portal yet.</div>
            )}
          </article>

          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Recent prescription summary</h2>
              <Link to="/patient/prescriptions">View all</Link>
            </div>
            {dashboard.data?.recentPrescription ? (
              <div className="patient-highlight-card">
                <strong>{dashboard.data.recentPrescription.prescriptionNumber}</strong>
                <span>
                  {dashboard.data.recentPrescription.doctorName ?? "Doctor pending"} · {formatStatusLabel(dashboard.data.recentPrescription.status)}
                </span>
                <p>{dashboard.data.recentPrescription.adviceSummary ?? "Advice summary will appear when available."}</p>
              </div>
            ) : (
              <div className="patient-inline-empty">No patient-safe prescription summary is available yet.</div>
            )}
          </article>

          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Latest bill and receipt</h2>
              <Link to="/patient/bills">View all</Link>
            </div>
            {dashboard.data?.latestBill ? (
              <div className="patient-highlight-card">
                <strong>{dashboard.data.latestBill.billNumber}</strong>
                <span>
                  {formatDate(dashboard.data.latestBill.billDate)} · {formatStatusLabel(dashboard.data.latestBill.status)}
                </span>
                <p>
                  Total {formatCurrency(dashboard.data.latestBill.totalAmount)} · Due {formatCurrency(dashboard.data.latestBill.dueAmount)}
                </p>
                {dashboard.data.latestBill.latestReceipt ? (
                  <small>
                    Latest receipt {dashboard.data.latestBill.latestReceipt.receiptNumber} on{" "}
                    {formatDate(dashboard.data.latestBill.latestReceipt.receiptDate)}
                  </small>
                ) : null}
              </div>
            ) : (
              <div className="patient-inline-empty">No bill summary is available yet.</div>
            )}
          </article>

        <article className="patient-panel">
          <div className="patient-panel-heading">
            <h2>CareAI</h2>
            <Link to="/patient/careai">Open CareAI</Link>
          </div>
          <div className="patient-highlight-card">
            <strong>Use CareAI for guided booking and visit prep</strong>
            <span>{dashboard.data?.clinicName ?? "Clinic"} patient view</span>
            <p>Ask CareAI to help book a clinic visit, summarize medicines, or explain billing totals using only your patient-safe data.</p>
          </div>
        </article>
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

export function PatientAppointmentsPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const appointments = usePatientPortalResource<PatientPortalAppointmentResponse[]>(portalSession, "/api/patient-portal/appointments", []);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Appointments"
      subtitle="Only your own tenant-scoped appointments appear here, in patient-friendly cards rather than staff tables."
    >
      <PatientPortalApiState
        loading={appointments.loading}
        error={appointments.error}
        empty={appointments.data.length === 0}
        emptyTitle="No appointments yet"
        emptyMessage="Your visits will appear here once the clinic has linked them to this patient session. You can also book a new clinic visit now."
      >
        <div className="patient-action-strip">
          <Link className="primary-button" to="/patient/book-appointment">
            Book appointment
          </Link>
        </div>
        <div className="patient-card-stack">
          {appointments.data.map((appointment) => (
            <article key={`${appointment.doctorName ?? "doctor"}-${appointment.appointmentDate}-${appointment.appointmentTime ?? "time"}`} className="patient-record-card">
              <div className="record-card-top">
                <div>
                  <strong>{appointment.doctorName ?? "Doctor to be confirmed"}</strong>
                  <span>
                    {appointment.clinicName ?? "Clinic"} · {formatDate(appointment.appointmentDate)} · {formatTime(appointment.appointmentTime)}
                  </span>
                </div>
                <span className="status-pill">{formatStatusLabel(appointment.status)}</span>
              </div>
              <p>{appointment.reason ?? "Visit reason is not available for this appointment."}</p>
              <div className="record-card-meta">
                <span>Source: {appointment.source ?? "Not available"}</span>
              </div>
            </article>
          ))}
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

export function PatientBookAppointmentPage({
  session,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  onSignOut: () => void;
}) {
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const doctorsState = usePatientPortalResource<PatientPortalDoctorResponse[]>(portalSession, "/api/patient-portal/doctors", []);
  const [selectedSpeciality, setSelectedSpeciality] = useState("All");
  const [selectedDoctorId, setSelectedDoctorId] = useState("");
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [reason, setReason] = useState("");
  const [slots, setSlots] = useState<PatientPortalDoctorSlotResponse[]>([]);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [slotsError, setSlotsError] = useState<string | null>(null);
  const [selectedSlotTime, setSelectedSlotTime] = useState("");
  const [submitPending, setSubmitPending] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<PatientPortalAppointmentConfirmationResponse | null>(null);

  const doctorOptions = useMemo(() => {
    if (selectedSpeciality === "All") {
      return doctorsState.data;
    }
    return doctorsState.data.filter((doctor) => doctor.specialization === selectedSpeciality);
  }, [doctorsState.data, selectedSpeciality]);

  const specialities = useMemo(() => {
    const values = new Set<string>();
    doctorsState.data.forEach((doctor) => {
      if (doctor.specialization) {
        values.add(doctor.specialization);
      }
    });
    return ["All", ...Array.from(values).sort((left, right) => left.localeCompare(right))];
  }, [doctorsState.data]);

  useEffect(() => {
    if (!doctorOptions.some((doctor) => doctor.publicDoctorId === selectedDoctorId)) {
      setSelectedDoctorId(doctorOptions[0]?.publicDoctorId ?? "");
    }
  }, [doctorOptions, selectedDoctorId]);

  useEffect(() => {
    setSelectedSlotTime("");
    setConfirmation(null);
    setSubmitError(null);

    if (!portalSession || !selectedDoctorId || !selectedDate) {
      setSlots([]);
      setSlotsLoading(false);
      setSlotsError(null);
      return;
    }

    const abortController = new AbortController();
    setSlotsLoading(true);
    setSlotsError(null);

    fetchPatientPortalJson<PatientPortalDoctorSlotResponse[]>(
      `/api/patient-portal/doctors/${selectedDoctorId}/slots?date=${selectedDate}`,
      portalSession,
      abortController.signal,
    )
      .then((result) => {
        setSlots(result);
        setSlotsLoading(false);
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) {
          return;
        }
        setSlots([]);
        setSlotsLoading(false);
        setSlotsError(error instanceof Error ? error.message : "Unable to load appointment slots.");
      });

    return () => abortController.abort();
  }, [selectedDate, selectedDoctorId, portalSession]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!portalSession || !selectedDoctorId || !selectedDate || !selectedSlotTime) {
      setSubmitError("Choose a doctor, date, and available time slot before confirming.");
      return;
    }

    setSubmitPending(true);
    setSubmitError(null);
    setConfirmation(null);
    try {
      const payload: PatientPortalAppointmentBookingRequest = {
        publicDoctorId: selectedDoctorId,
        appointmentDate: selectedDate,
        appointmentTime: selectedSlotTime,
        reason: reason.trim() ? reason.trim() : null,
      };
      const response = await postPatientPortalSessionJson<PatientPortalAppointmentConfirmationResponse>(
        "/api/patient-portal/appointments",
        payload,
        portalSession,
      );
      setConfirmation(response);
      setReason("");
      setSelectedSlotTime("");
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "Unable to confirm the appointment.");
    } finally {
      setSubmitPending(false);
    }
  }

  const selectedDoctor = doctorOptions.find((doctor) => doctor.publicDoctorId === selectedDoctorId) ?? null;
  const availableSlots = slots.filter((slot) => slot.selectable);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Book an in-clinic appointment"
      subtitle="Pick a tenant-scoped doctor, choose an available slot, and book the visit for your verified patient profile only."
    >
      <PatientPortalApiState
        loading={doctorsState.loading}
        error={doctorsState.error}
        empty={doctorsState.data.length === 0}
        emptyTitle="No doctors are ready for online booking"
        emptyMessage="The clinic has not published any active doctors with patient-safe booking slots yet."
      >
        <div className="patient-booking-grid">
          <section className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Choose your doctor</h2>
              <span className="panel-caption">Step 1</span>
            </div>
            <div className="booking-filter-chips">
              {specialities.map((speciality) => (
                <button
                  key={speciality}
                  className={`booking-chip${selectedSpeciality === speciality ? " is-active" : ""}`}
                  type="button"
                  onClick={() => setSelectedSpeciality(speciality)}
                >
                  {speciality}
                </button>
              ))}
            </div>
            <div className="patient-subcard-list">
              {doctorOptions.map((doctor) => (
                <button
                  key={doctor.publicDoctorId}
                  className={`doctor-choice-card${selectedDoctorId === doctor.publicDoctorId ? " is-active" : ""}`}
                  type="button"
                  onClick={() => setSelectedDoctorId(doctor.publicDoctorId)}
                >
                  <strong>{doctor.doctorName}</strong>
                  <span>{doctor.specialization ?? "General consultation"}</span>
                  <small>
                    {doctor.qualification ?? "Clinic doctor"}
                    {doctor.consultationRoom ? ` · ${doctor.consultationRoom}` : ""}
                    {doctor.yearsOfExperience ? ` · ${doctor.yearsOfExperience} yrs exp` : ""}
                  </small>
                </button>
              ))}
            </div>
          </section>

          <section className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Select date and slot</h2>
              <span className="panel-caption">Step 2</span>
            </div>
            <label className="patient-form-field">
              <span>Date</span>
              <input
                type="date"
                min={new Date().toISOString().slice(0, 10)}
                value={selectedDate}
                onChange={(event) => setSelectedDate(event.target.value)}
              />
            </label>

            {slotsLoading ? <div className="patient-inline-empty">Loading available slots...</div> : null}
            {slotsError ? (
              <div className="patient-inline-empty">
                <strong>Slots unavailable</strong>
                <p>{slotsError}</p>
              </div>
            ) : null}
            {!slotsLoading && !slotsError && selectedDoctor && availableSlots.length === 0 ? (
              <div className="patient-inline-empty">
                No patient-bookable slots are available for {selectedDoctor.doctorName} on {formatDate(selectedDate)}.
              </div>
            ) : null}

            {availableSlots.length > 0 ? (
              <div className="booking-slot-grid">
                {availableSlots.map((slot) => (
                  <button
                    key={`${slot.appointmentDate}-${slot.slotTime}`}
                    className={`booking-slot-card${selectedSlotTime === slot.slotTime ? " is-active" : ""}`}
                    type="button"
                    onClick={() => setSelectedSlotTime(slot.slotTime)}
                  >
                    <strong>{formatTime(slot.slotTime)}</strong>
                    <span>{slot.slotEndTime ? `Until ${formatTime(slot.slotEndTime)}` : "Available"}</span>
                  </button>
                ))}
              </div>
            ) : null}
          </section>
        </div>

        <section className="patient-panel">
          <div className="patient-panel-heading">
            <h2>Confirm booking</h2>
            <span className="panel-caption">Step 3</span>
          </div>
          <form className="patient-booking-form" onSubmit={handleSubmit}>
            <label className="patient-form-field">
              <span>Reason for visit</span>
              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                maxLength={300}
                rows={4}
                placeholder="Optional symptoms or complaint"
              />
            </label>
            <div className="booking-summary-card">
              <strong>{selectedDoctor?.doctorName ?? "Select a doctor"}</strong>
              <span>{selectedDoctor?.specialization ?? "Choose a speciality and doctor first."}</span>
              <small>
                {selectedDate ? formatDate(selectedDate) : "Pick a date"} {selectedSlotTime ? `· ${formatTime(selectedSlotTime)}` : ""}
              </small>
            </div>
            {submitError ? (
              <div className="patient-inline-empty">
                <strong>Booking unavailable</strong>
                <p>{submitError}</p>
              </div>
            ) : null}
            {confirmation ? (
              <div className="patient-success-card">
                <strong>{confirmation.message}</strong>
                <p>
                  {confirmation.doctorName ?? "Doctor"} · {formatDate(confirmation.appointmentDate)} ·{" "}
                  {formatTime(confirmation.appointmentTime)}
                </p>
                <span>
                  {confirmation.clinicName ?? "Clinic"} · {formatStatusLabel(confirmation.status)}
                </span>
                <Link className="secondary-button" to="/patient/appointments">
                  View appointments
                </Link>
              </div>
            ) : null}
            <div className="patient-action-row">
              <button className="primary-button" type="submit" disabled={submitPending || !selectedDoctorId || !selectedSlotTime}>
                {submitPending ? "Confirming..." : "Confirm booking"}
              </button>
              <Link className="ghost-button" to="/patient/appointments">
                Cancel
              </Link>
            </div>
          </form>
        </section>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

export function PatientPrescriptionsPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const prescriptions = usePatientPortalResource<PatientPortalPrescriptionResponse[]>(portalSession, "/api/patient-portal/prescriptions", []);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [documentError, setDocumentError] = useState<string | null>(null);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Prescriptions"
      subtitle="Read-only prescription cards show patient-safe medicine and follow-up summaries."
    >
      <PatientPortalApiState
        loading={prescriptions.loading}
        error={prescriptions.error}
        empty={prescriptions.data.length === 0}
        emptyTitle="No prescriptions yet"
        emptyMessage="Published prescriptions will appear here once your clinic finalizes them for patient access."
      >
        {documentError ? (
          <div className="patient-inline-empty">
            <strong>Document unavailable</strong>
            <p>{documentError}</p>
          </div>
        ) : null}
        <div className="patient-card-stack">
          {prescriptions.data.map((prescription) => (
            <article key={prescription.prescriptionNumber} className="patient-record-card">
              <div className="record-card-top">
                <div>
                  <strong>{prescription.prescriptionNumber}</strong>
                  <span>
                    {prescription.doctorName ?? "Doctor pending"} · {formatDate(prescription.prescriptionDate)}
                  </span>
                </div>
                <span className="status-pill">{formatStatusLabel(prescription.status)}</span>
              </div>

              <div className="patient-detail-list">
                <div>
                  <strong>Diagnosis summary</strong>
                  <span>{prescription.diagnosisSummary ?? "No patient-safe diagnosis summary is available."}</span>
                </div>
                <div>
                  <strong>Advice</strong>
                  <span>{prescription.adviceSummary ?? "No advice summary is available yet."}</span>
                </div>
              </div>

              <div className="patient-prescription-grid">
                <div>
                  <h3>Medicines</h3>
                  {prescription.medicines.length ? (
                    <div className="patient-subcard-list">
                      {prescription.medicines.map((medicine) => (
                        <div key={`${prescription.prescriptionNumber}-${medicine.medicineName}`} className="patient-subcard">
                          <strong>{medicine.medicineName}</strong>
                          <span>{medicine.instructions}</span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="patient-inline-empty">No medicines were added to this prescription.</div>
                  )}
                </div>
                <div>
                  <h3>Recommended tests</h3>
                  {prescription.recommendedTests.length ? (
                    <div className="patient-subcard-list">
                      {prescription.recommendedTests.map((test) => (
                        <div key={`${prescription.prescriptionNumber}-${test.testName}`} className="patient-subcard">
                          <strong>{test.testName}</strong>
                          <span>{test.instructions ?? "No extra instructions"}</span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="patient-inline-empty">No tests were recommended for this prescription.</div>
                  )}
                </div>
              </div>

              <div className="record-card-meta">
                <span>Follow-up: {formatDate(prescription.followUpDate)}</span>
                <span>{prescription.clinicName ?? "Clinic"}</span>
              </div>

              {prescription.pdfAvailable ? (
                <div className="patient-action-row">
                  <button
                    className="secondary-button"
                    type="button"
                    disabled={busyKey === prescription.prescriptionNumber}
                    onClick={() =>
                      openPortalDocument(
                        portalSession,
                        `/api/patient-portal/prescriptions/${encodeURIComponent(prescription.prescriptionNumber)}/pdf`,
                        setBusyKey,
                        prescription.prescriptionNumber,
                        setDocumentError,
                      )
                    }
                  >
                    {busyKey === prescription.prescriptionNumber ? "Opening..." : "View PDF"}
                  </button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

export function PatientBillsPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const bills = usePatientPortalResource<PatientPortalBillResponse[]>(portalSession, "/api/patient-portal/bills", []);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [documentError, setDocumentError] = useState<string | null>(null);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Bills"
      subtitle="Review patient-safe bill totals, due amounts, and receipt summaries without exposing billing ledger internals."
    >
      <PatientPortalApiState
        loading={bills.loading}
        error={bills.error}
        empty={bills.data.length === 0}
        emptyTitle="No bills yet"
        emptyMessage="Your bill summaries will appear here when the clinic publishes them to the patient portal."
      >
        {documentError ? (
          <div className="patient-inline-empty">
            <strong>Document unavailable</strong>
            <p>{documentError}</p>
          </div>
        ) : null}
        <div className="patient-card-stack">
          {bills.data.map((bill) => (
            <article key={bill.billNumber} className="patient-record-card">
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

              {bill.latestReceipt ? (
                <div className="patient-highlight-card">
                  <strong>Latest receipt {bill.latestReceipt.receiptNumber}</strong>
                  <span>{formatDate(bill.latestReceipt.receiptDate)}</span>
                  <p>Receipt amount: {formatCurrency(bill.latestReceipt.amount)}</p>
                </div>
              ) : (
                <div className="patient-inline-empty">No receipt has been generated for this bill yet.</div>
              )}

              {bill.lines.length ? (
                <div className="patient-subcard-list">
                  {bill.lines.map((line) => (
                    <div key={`${bill.billNumber}-${line.itemName}-${line.summary}`} className="patient-subcard">
                      <strong>{line.itemName}</strong>
                      <span>{line.summary}</span>
                    </div>
                  ))}
                </div>
              ) : null}

              <div className="patient-action-row">
                <button
                  className="secondary-button"
                  type="button"
                  disabled={busyKey === `${bill.billNumber}-bill`}
                  onClick={() =>
                    openPortalDocument(
                      session,
                      `/api/patient-portal/bills/${encodeURIComponent(bill.billNumber)}/pdf`,
                      setBusyKey,
                      `${bill.billNumber}-bill`,
                      setDocumentError,
                    )
                  }
                >
                  {busyKey === `${bill.billNumber}-bill` ? "Opening..." : "View bill"}
                </button>
                {bill.latestReceipt ? (
                  <button
                    className="ghost-button"
                    type="button"
                    disabled={busyKey === `${bill.billNumber}-receipt`}
                    onClick={() =>
                      openPortalDocument(
                        portalSession,
                        `/api/patient-portal/bills/${encodeURIComponent(bill.billNumber)}/receipt.pdf`,
                        setBusyKey,
                        `${bill.billNumber}-receipt`,
                        setDocumentError,
                      )
                    }
                  >
                    {busyKey === `${bill.billNumber}-receipt` ? "Opening..." : "View receipt"}
                  </button>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}

export function PatientCareAiPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const [messages, setMessages] = useState<PatientCareAiChatEntry[]>([
    {
      id: "assistant-intro",
      role: "assistant",
      text: "I can help book, reschedule, cancel, or check appointments. I only complete changes after you explicitly confirm.",
    },
  ]);
  const [draft, setDraft] = useState("");
  const [state, setState] = useState<PatientPortalCareAiStateResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chatEndRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages]);

  useEffect(() => {
    setMessages([
      {
        id: "assistant-intro",
        role: "assistant",
        text: "I can help book, reschedule, cancel, or check appointments. I only complete changes after you explicitly confirm.",
      },
    ]);
    setDraft("");
    setState(null);
    setError(null);
    setSubmitting(false);
    setResetting(false);
  }, [portalSession?.patientSessionToken]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!portalSession || !draft.trim() || submitting) {
      return;
    }

    const trimmed = draft.trim();
    const patientMessage: PatientCareAiChatEntry = {
      id: `patient-${Date.now()}`,
      role: "patient",
      text: trimmed,
    };
    setMessages((current) => [...current, patientMessage]);
    setDraft("");
    setSubmitting(true);
    setError(null);

    try {
      const request: PatientPortalCareAiMessageRequest = {
        message: trimmed,
        language: "auto",
      };
      const response = await postPatientPortalSessionJson<PatientPortalCareAiMessageResponse>(
        "/api/patient-portal/careai/message",
        request,
        portalSession,
      );
      setState(response.state);
      setMessages((current) => [
        ...current,
        {
          id: `assistant-${Date.now()}`,
          role: "assistant",
          text: response.assistantMessage,
        },
      ]);
    } catch (submitError: unknown) {
      setError(submitError instanceof Error ? submitError.message : "CareAI could not process that request.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReset() {
    if (!portalSession || resetting) {
      return;
    }
    setResetting(true);
    setError(null);
    try {
      const response = await postPatientPortalSessionJson<PatientPortalCareAiResetResponse>(
        "/api/patient-portal/careai/reset",
        {},
        portalSession,
      );
      setMessages([
        {
          id: "assistant-reset",
          role: "assistant",
          text: response.message,
        },
        {
          id: "assistant-reset-followup",
          role: "assistant",
          text: "Start a new booking request when you are ready.",
        },
      ]);
      setState(null);
      setDraft("");
    } catch (resetError: unknown) {
      setError(resetError instanceof Error ? resetError.message : "CareAI could not reset the booking context.");
    } finally {
      setResetting(false);
    }
  }

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Patient CareAI"
      subtitle="Chat-based booking uses your verified patient session, asks for explicit confirmation, and stays scoped to your tenant."
    >
      <div className="patient-content-grid">
        <article className="patient-panel patient-panel-wide">
          <div className="patient-panel-heading">
            <h2>CareAI booking chat</h2>
            <button className="ghost-button" type="button" disabled={!portalSession || resetting} onClick={handleReset}>
              {resetting ? "Resetting..." : "Reset"}
            </button>
          </div>
          <div className="patient-careai-chat">
            <div className="patient-chat-stream" aria-live="polite">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`patient-chat-bubble ${message.role === "patient" ? "patient-chat-bubble-self" : "patient-chat-bubble-ai"}`}
                >
                  <strong>{message.role === "patient" ? "You" : "CareAI"}</strong>
                  <p>{message.text}</p>
                </div>
              ))}
              {submitting ? (
                <div className="patient-chat-bubble patient-chat-bubble-ai">
                  <strong>CareAI</strong>
                  <p>Checking doctors and slots for you...</p>
                </div>
              ) : null}
              <div ref={chatEndRef} />
            </div>
            <form className="patient-careai-form" onSubmit={handleSubmit}>
              <label className="patient-form-field">
                <span>Message</span>
                <textarea
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder="Example: Reschedule my appointment, Book with Dr Neha tomorrow, Cancel next appointment."
                  rows={4}
                  disabled={!portalSession || submitting}
                />
              </label>
              <div className="patient-action-row">
                <button className="primary-button" type="submit" disabled={!portalSession || submitting || !draft.trim()}>
                  {submitting ? "Sending..." : "Send to CareAI"}
                </button>
                <span className="patient-inline-note">CareAI never books, reschedules, or cancels until you explicitly confirm.</span>
              </div>
              {error ? <div className="patient-inline-empty patient-inline-error">{error}</div> : null}
            </form>
          </div>
        </article>

        <article className="patient-panel">
          <div className="patient-panel-heading">
            <h2>Conversation state</h2>
          </div>
          {state ? (
            <div className="patient-careai-state">
              <div className="patient-detail-list">
                <div>
                  <strong>Current intent</strong>
                  <span>{state.currentIntent ? formatStatusLabel(state.currentIntent.replaceAll("_", " ")) : "Waiting for request"}</span>
                </div>
                <div>
                  <strong>Selected doctor</strong>
                  <span>{state.doctorName ?? "Not selected yet"}</span>
                </div>
                <div>
                  <strong>Selected appointment</strong>
                  <span>{state.selectedAppointment ?? "Not selected yet"}</span>
                </div>
                <div>
                  <strong>Selected date</strong>
                  <span>{state.preferredDate ? formatDate(state.preferredDate) : "Not selected yet"}</span>
                </div>
                <div>
                  <strong>Selected slot</strong>
                  <span>{state.suggestedSlot ? formatTime(state.suggestedSlot) : "No slot suggested yet"}</span>
                </div>
                <div>
                  <strong>Status</strong>
                  <span>
                    {state.actionCompleted
                      ? `${state.lastAction ? formatStatusLabel(state.lastAction.replaceAll("_", " ")) : "Completed"}${state.bookingStatus ? ` · ${formatStatusLabel(state.bookingStatus)}` : ""}`
                      : state.confirmationPending
                        ? "Confirmation pending"
                        : state.handoffRequired
                          ? "Reception handoff needed"
                          : "Collecting details"}
                  </span>
                </div>
              </div>

              {state.doctorOptions.length ? (
                <div className="patient-subcard-list">
                  {state.doctorOptions.map((option, index) => (
                    <div key={option} className="patient-subcard">
                      <strong>Doctor option {index + 1}</strong>
                      <span>{option}</span>
                    </div>
                  ))}
                </div>
              ) : null}

              {state.appointmentOptions.length ? (
                <div className="patient-subcard-list">
                  {state.appointmentOptions.map((option, index) => (
                    <div key={option} className="patient-subcard">
                      <strong>Appointment {index + 1}</strong>
                      <span>{option}</span>
                    </div>
                  ))}
                </div>
              ) : null}

              {state.slotOptions.length ? (
                <div className="patient-subcard-list">
                  {state.slotOptions.map((option, index) => (
                    <div key={option} className="patient-subcard">
                      <strong>Slot {index + 1}</strong>
                      <span>{formatTime(option)}</span>
                    </div>
                  ))}
                </div>
              ) : null}

              {state.handoffRequired ? (
                <div className="patient-highlight-card patient-careai-alert">
                  <strong>Reception follow-up recommended</strong>
                  <span>{state.handoffReason ?? "manual-assist"}</span>
                  <p>CareAI could not safely finish this request. The clinic team should help complete the booking.</p>
                </div>
              ) : null}
            </div>
          ) : (
            <div className="patient-empty-card">
              <strong>CareAI is ready</strong>
              <p>Ask to book, reschedule, cancel, or check an appointment. You can reply in English or Hindi.</p>
            </div>
          )}
        </article>

        <article className="patient-panel">
          <div className="patient-panel-heading">
            <h2>What CareAI can do now</h2>
          </div>
          <div className="patient-subcard-list">
            <div className="patient-subcard">
              <strong>Book and confirm appointments</strong>
              <span>CareAI can collect doctor, date, slot, and explicit confirmation before creating the appointment.</span>
            </div>
            <div className="patient-subcard">
              <strong>Reschedule and cancel safely</strong>
              <span>CareAI can list your upcoming appointments, ask you to choose one, and only change it after confirmation.</span>
            </div>
            <div className="patient-subcard">
              <strong>Confirmed patient session only</strong>
              <span>CareAI uses your verified portal session and never accepts another patient identity from chat.</span>
            </div>
            <div className="patient-subcard">
              <strong>Urgent symptoms are handed off</strong>
              <span>Emergency-sounding symptoms trigger an urgent-care message instead of an automated booking.</span>
            </div>
          </div>
        </article>
      </div>
    </PatientAccessBoundary>
  );
}

export function PatientProfilePage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const profile = usePatientPortalResource<PatientPortalMeResponse | null>(portalSession, "/api/patient-portal/me", null);
  const [profileView, setProfileView] = useState<PatientPortalMeResponse | null>(null);
  const profileData = profileView ?? profile.data;
  const ageGender = useMemo(() => {
    if (!profileData) {
      return "Not available yet";
    }
    const values = [profileData.gender ? formatStatusLabel(profileData.gender) : null, profileData.ageYears != null ? `${profileData.ageYears} years` : null].filter(Boolean);
    return values.length ? values.join(" · ") : "Not available yet";
  }, [profileData]);
  const [formState, setFormState] = useState<PatientPortalProfileUpdateRequest | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (!profile.data) {
      setFormState(null);
      setProfileView(null);
      return;
    }
    setProfileView(profile.data);
    const [firstName, ...lastNameParts] = profile.data.fullName.split(" ");
    setFormState({
      firstName: firstName ?? "",
      lastName: lastNameParts.join(" "),
      gender: profile.data.gender ?? "UNKNOWN",
      dateOfBirth: profile.data.dateOfBirth,
      ageYears: profile.data.ageYears,
      email: profile.data.email,
      addressLine1: profile.data.addressLine1,
      addressLine2: profile.data.addressLine2,
      city: profile.data.city,
      state: profile.data.state,
      country: profile.data.country,
      postalCode: profile.data.postalCode,
      emergencyContactName: profile.data.emergencyContactName,
      emergencyContactMobile: profile.data.emergencyContactMobile,
    });
  }, [profile.data]);

  async function handleProfileSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!portalSession || !formState) {
      return;
    }
    setSaving(true);
    setSaveError(null);
    setSaveMessage(null);
    try {
      const response = await putPatientPortalSessionJson<PatientPortalMeResponse>(
        "/api/patient-portal/me",
        formState,
        portalSession,
      );
      setProfileView(response);
      setSaveMessage("Profile updated.");
    } catch (updateError: unknown) {
      setSaveError(updateError instanceof Error ? updateError.message : "Unable to save your profile.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Profile"
      subtitle="Only your own patient-safe identity and health summary fields appear here."
    >
      <PatientPortalApiState
        loading={profile.loading}
        error={profile.error}
        empty={!profileData}
        emptyTitle="Your profile is not available yet"
        emptyMessage="The portal will show your patient-safe demographic and history summary once the clinic has linked it."
      >
        <div className="patient-content-grid">
          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Identity</h2>
            </div>
            <div className="patient-detail-list">
              <div>
                <strong>{profileData?.fullName ?? "Patient"}</strong>
                <span>Patient number {profileData?.patientNumber ?? "Not available"}</span>
              </div>
              <div>
                <strong>Clinic</strong>
                <span>{profileData?.clinicName ?? "Not available yet"}</span>
              </div>
              <div>
                <strong>Gender / age</strong>
                <span>{ageGender}</span>
              </div>
              <div>
                <strong>Mobile</strong>
                <span>{profileData?.mobile ?? "Not available yet"}</span>
              </div>
              <div>
                <strong>Email</strong>
                <span>{profileData?.email ?? "Not available yet"}</span>
              </div>
              <div>
                <strong>Address</strong>
                <span>
                  {[profileData?.addressLine1, profileData?.addressLine2, profileData?.city].filter(Boolean).join(", ") || "Not available yet"}
                </span>
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
                <span>{profileData?.allergies ?? "No allergy summary available."}</span>
              </div>
              <div>
                <strong>Chronic conditions</strong>
                <span>{profileData?.chronicConditions ?? "No chronic condition summary available."}</span>
              </div>
              <div>
                <strong>Long-term medications</strong>
                <span>{profileData?.longTermMedications ?? "No long-term medication summary available."}</span>
              </div>
              <div>
                <strong>Blood group</strong>
                <span>{profileData?.bloodGroup ?? "Not available yet"}</span>
              </div>
            </div>
          </article>
        </div>

        {formState ? (
          <article className="patient-panel">
            <div className="patient-panel-heading">
              <h2>Edit safe profile fields</h2>
              <span className="panel-caption">Patient number, tenant, and mobile remain locked</span>
            </div>
            <form className="patient-login-form patient-registration-form" onSubmit={handleProfileSave}>
              <div className="patient-form-grid">
                <label>
                  <span>First name</span>
                  <input value={formState.firstName} onChange={(event) => setFormState((current) => current ? { ...current, firstName: event.target.value } : current)} required />
                </label>
                <label>
                  <span>Last name</span>
                  <input value={formState.lastName} onChange={(event) => setFormState((current) => current ? { ...current, lastName: event.target.value } : current)} required />
                </label>
                <label>
                  <span>Gender</span>
                  <select value={formState.gender} onChange={(event) => setFormState((current) => current ? { ...current, gender: event.target.value } : current)}>
                    <option value="UNKNOWN">Prefer not to say</option>
                    <option value="MALE">Male</option>
                    <option value="FEMALE">Female</option>
                    <option value="OTHER">Other</option>
                  </select>
                </label>
                <label>
                  <span>Date of birth</span>
                  <input
                    type="date"
                    max={new Date().toISOString().slice(0, 10)}
                    value={formState.dateOfBirth ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, dateOfBirth: event.target.value || null } : current)}
                  />
                </label>
                <label>
                  <span>Age</span>
                  <input
                    value={formState.ageYears ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, ageYears: event.target.value ? Number(event.target.value) : null } : current)}
                  />
                </label>
                <label>
                  <span>Mobile</span>
                  <input value={profileData?.mobile ?? ""} readOnly />
                </label>
                <label>
                  <span>Email</span>
                  <input value={formState.email ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, email: event.target.value || null } : current)} />
                </label>
                <label className="patient-form-span-2">
                  <span>Address line 1</span>
                  <input value={formState.addressLine1 ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, addressLine1: event.target.value || null } : current)} />
                </label>
                <label className="patient-form-span-2">
                  <span>Address line 2</span>
                  <input value={formState.addressLine2 ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, addressLine2: event.target.value || null } : current)} />
                </label>
                <label>
                  <span>City</span>
                  <input value={formState.city ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, city: event.target.value || null } : current)} />
                </label>
                <label>
                  <span>State</span>
                  <input value={formState.state ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, state: event.target.value || null } : current)} />
                </label>
                <label>
                  <span>Country</span>
                  <input value={formState.country ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, country: event.target.value || null } : current)} />
                </label>
                <label>
                  <span>Postal code</span>
                  <input value={formState.postalCode ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, postalCode: event.target.value || null } : current)} />
                </label>
                <label>
                  <span>Emergency contact</span>
                  <input value={formState.emergencyContactName ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, emergencyContactName: event.target.value || null } : current)} />
                </label>
                <label>
                  <span>Emergency mobile</span>
                  <input value={formState.emergencyContactMobile ?? ""} onChange={(event) => setFormState((current) => current ? { ...current, emergencyContactMobile: event.target.value || null } : current)} />
                </label>
              </div>
              {saveMessage ? (
                <div className="patient-success-card">
                  <strong>{saveMessage}</strong>
                </div>
              ) : null}
              {saveError ? (
                <div className="patient-inline-empty">
                  <strong>Profile update unavailable</strong>
                  <p>{saveError}</p>
                </div>
              ) : null}
              <div className="patient-action-row">
                <button className="primary-button" type="submit" disabled={saving}>
                  {saving ? "Saving..." : "Save profile"}
                </button>
              </div>
            </form>
          </article>
        ) : null}
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}
