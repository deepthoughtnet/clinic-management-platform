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
  type PatientPortalOtpVerifyResponse,
  type PatientPortalPrescriptionResponse,
  type PatientPortalSession,
  fetchPatientPortalJson,
  openPatientPortalPdf,
  postPatientPortalJson,
  postPatientPortalSessionJson,
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
  session: PatientPortalSession;
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
          tenantCode: tenantCode.trim(),
          tenantId: response.tenantId,
          phone: phone.trim(),
          patientLabel: response.patientDisplayName,
          createdAt: new Date().toISOString(),
          patientSessionToken: response.patientSessionToken,
        });
        const nextPath = searchParams.get("next");
        navigate(nextPath?.startsWith("/patient/") ? nextPath : "/patient/dashboard");
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

export function PatientDashboardPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const dashboard = usePatientPortalResource<PatientPortalDashboardResponse | null>(session, "/api/patient-portal/dashboard", null);

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
  const appointments = usePatientPortalResource<PatientPortalAppointmentResponse[]>(session, "/api/patient-portal/appointments", []);

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
  const doctorsState = usePatientPortalResource<PatientPortalDoctorResponse[]>(session, "/api/patient-portal/doctors", []);
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

    if (!session || !selectedDoctorId || !selectedDate) {
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
      session,
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
  }, [selectedDate, selectedDoctorId, session]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!session || !selectedDoctorId || !selectedDate || !selectedSlotTime) {
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
        session,
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
  const prescriptions = usePatientPortalResource<PatientPortalPrescriptionResponse[]>(session, "/api/patient-portal/prescriptions", []);
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
                        session,
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
  const bills = usePatientPortalResource<PatientPortalBillResponse[]>(session, "/api/patient-portal/bills", []);
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
                        session,
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
  const [messages, setMessages] = useState<PatientCareAiChatEntry[]>([
    {
      id: "assistant-intro",
      role: "assistant",
      text: "Tell me the doctor or speciality, date, and time window you want. I will only book after you explicitly confirm.",
    },
  ]);
  const [draft, setDraft] = useState("");
  const [state, setState] = useState<PatientPortalCareAiStateResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chatEndRef = useRef<HTMLDivElement | null>(null);
  const bookingProgress = [
    { label: "Doctor", complete: Boolean(state?.doctorName) },
    { label: "Date", complete: Boolean(state?.preferredDate) },
    { label: "Slot", complete: Boolean(state?.suggestedSlot) },
    { label: "Confirm", complete: Boolean(state?.confirmationPending || state?.booked) },
    { label: "Booked", complete: Boolean(state?.booked) },
  ];

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages]);

  useEffect(() => {
    setMessages([
      {
        id: "assistant-intro",
        role: "assistant",
        text: "Tell me the doctor or speciality, date, and time window you want. I will only book after you explicitly confirm.",
      },
    ]);
    setDraft("");
    setState(null);
    setError(null);
    setSubmitting(false);
    setResetting(false);
  }, [session?.patientSessionToken]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !draft.trim() || submitting) {
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
        session,
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
    if (!session || resetting) {
      return;
    }
    setResetting(true);
    setError(null);
    try {
      const response = await postPatientPortalSessionJson<PatientPortalCareAiResetResponse>(
        "/api/patient-portal/careai/reset",
        {},
        session,
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
            <button className="ghost-button" type="button" disabled={!session || resetting} onClick={handleReset}>
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
                  placeholder="Example: Book appointment with Dr Suresh tomorrow morning for fever."
                  rows={4}
                  disabled={!session || submitting}
                />
              </label>
              <div className="patient-action-row">
                <button className="primary-button" type="submit" disabled={!session || submitting || !draft.trim()}>
                  {submitting ? "Sending..." : "Send to CareAI"}
                </button>
                <span className="patient-inline-note">CareAI never books until you explicitly confirm.</span>
              </div>
              {error ? <div className="patient-inline-empty patient-inline-error">{error}</div> : null}
            </form>
          </div>
        </article>

        <article className="patient-panel">
          <div className="patient-panel-heading">
            <h2>Booking state</h2>
          </div>
          {state ? (
            <div className="patient-careai-state">
              <div className="patient-careai-progress" aria-label="CareAI booking progress">
                {bookingProgress.map((step) => (
                  <div
                    key={step.label}
                    className={`patient-careai-progress-step${step.complete ? " is-complete" : ""}`}
                  >
                    <strong>{step.label}</strong>
                    <span>{step.complete ? "Ready" : "Waiting"}</span>
                  </div>
                ))}
              </div>
              <div className="patient-detail-list">
                <div>
                  <strong>Doctor</strong>
                  <span>{state.doctorName ?? "Waiting for doctor or speciality"}</span>
                </div>
                <div>
                  <strong>Speciality</strong>
                  <span>{state.speciality ?? "Not selected yet"}</span>
                </div>
                <div>
                  <strong>Date</strong>
                  <span>{state.preferredDate ? formatDate(state.preferredDate) : "Not selected yet"}</span>
                </div>
                <div>
                  <strong>Time window</strong>
                  <span>{state.preferredTimeWindow ?? "Not selected yet"}</span>
                </div>
                <div>
                  <strong>Suggested slot</strong>
                  <span>{state.suggestedSlot ? formatTime(state.suggestedSlot) : "No slot suggested yet"}</span>
                </div>
                <div>
                  <strong>Status</strong>
                  <span>
                    {state.booked
                      ? `Booked${state.bookingStatus ? ` · ${formatStatusLabel(state.bookingStatus)}` : ""}`
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
                  {state.doctorOptions.map((option) => (
                    <div key={option} className="patient-subcard">
                      <strong>Doctor option</strong>
                      <span>{option}</span>
                    </div>
                  ))}
                </div>
              ) : null}

              {state.slotOptions.length ? (
                <div className="patient-subcard-list">
                  {state.slotOptions.map((option) => (
                    <div key={option} className="patient-subcard">
                      <strong>Available slot</strong>
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
              <p>Start with a doctor or speciality, preferred date, and time window. You can reply in English or Hindi.</p>
            </div>
          )}
        </article>

        <article className="patient-panel">
          <div className="patient-panel-heading">
            <h2>What CareAI can do now</h2>
          </div>
          <div className="patient-subcard-list">
            <div className="patient-subcard">
              <strong>Guide clinic appointment booking</strong>
              <span>CareAI can help collect speciality, doctor, date, time window, and a final confirmation for your visit.</span>
            </div>
            <div className="patient-subcard">
              <strong>No booking before confirmation</strong>
              <span>A slot is only created after you explicitly confirm the suggested time.</span>
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
  const profile = usePatientPortalResource<PatientPortalMeResponse | null>(session, "/api/patient-portal/me", null);
  const ageGender = useMemo(() => {
    if (!profile.data) {
      return "Not available yet";
    }
    const values = [profile.data.gender ? formatStatusLabel(profile.data.gender) : null, profile.data.ageYears != null ? `${profile.data.ageYears} years` : null].filter(Boolean);
    return values.length ? values.join(" · ") : "Not available yet";
  }, [profile.data]);

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
        empty={!profile.data}
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
                <strong>{profile.data?.fullName ?? "Patient"}</strong>
                <span>Patient number {profile.data?.patientNumber ?? "Not available"}</span>
              </div>
              <div>
                <strong>Clinic</strong>
                <span>{profile.data?.clinicName ?? "Not available yet"}</span>
              </div>
              <div>
                <strong>Gender / age</strong>
                <span>{ageGender}</span>
              </div>
              <div>
                <strong>Mobile</strong>
                <span>{profile.data?.mobile ?? "Not available yet"}</span>
              </div>
              <div>
                <strong>Email</strong>
                <span>{profile.data?.email ?? "Not available yet"}</span>
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
                <span>{profile.data?.allergies ?? "No allergy summary available."}</span>
              </div>
              <div>
                <strong>Chronic conditions</strong>
                <span>{profile.data?.chronicConditions ?? "No chronic condition summary available."}</span>
              </div>
              <div>
                <strong>Long-term medications</strong>
                <span>{profile.data?.longTermMedications ?? "No long-term medication summary available."}</span>
              </div>
              <div>
                <strong>Blood group</strong>
                <span>{profile.data?.bloodGroup ?? "Not available yet"}</span>
              </div>
            </div>
          </article>
        </div>
      </PatientPortalApiState>
    </PatientAccessBoundary>
  );
}
