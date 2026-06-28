import { type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  indianMobileNumber,
  mapZodErrors,
  otpVerifySchema,
  patientProfileSchema,
  patientQuickRegisterSchema,
  bookAppointmentSchema,
} from "@deepthoughtnet/form-validation-kit";
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
  type PatientPortalDoctorSlotResponse,
  type PatientPortalMeResponse,
  type PatientPortalNotificationResponse,
  type PatientPortalOtpRequestResponse,
  type PatientPortalPatientSession,
  type PatientPortalProfileUpdateRequest,
  type PatientPortalRegistrationRequest,
  type PatientPortalRegistrationResponse,
  type PatientPortalRegistrationSession,
  type PatientPortalOtpVerifyResponse,
  type PatientPortalPrescriptionResponse,
  type PatientPortalSession,
  buildPatientPortalVoiceWebSocketUrl,
  fetchPatientPortalJson,
  isUuid,
  loadPatientPortalDoctorSlots,
  isPatientPortalPatientSession,
  isPatientPortalRegistrationSession,
  markPatientNotificationRead,
  openPatientPortalPdf,
  patientPortalHomePath,
  postPatientPortalJson,
  postPatientPortalSessionJson,
  putPatientPortalSessionJson,
} from "../../api/patientPortal";
import {
  type PublicClinicDetailResponse,
  type PublicDoctorDetailResponse,
  type PublicDoctorSummaryResponse,
  fetchPublicJson,
} from "../../api/publicCatalog";
import { branding } from "../../branding";
import { DoctorClinicSelector } from "./DoctorClinicSelector";
import {
  sanitizePatientOtpInput,
  sanitizePatientPhoneInput,
} from "./patientLoginInput.js";
import {
  clearPublicBookingContext,
  getPublicBookingContext,
  resolvePatientAuthContext,
  resolvePatientPortalContext,
  savePublicBookingContext,
} from "./patientPortalClinicContext";
import {
  PATIENT_PORTAL_PENDING_REGISTRATION_STORAGE_KEY,
  clearPatientRegistrationSession,
  isPatientRegistrationSessionActive,
} from "./patientPortalSessionState";
import {
  formatDisplayDate,
  formatDisplayDateTime,
  formatDisplayDateTimeFromParts,
  formatDisplayTime,
} from "../../utils/dateDisplay";

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

type PatientCareAiVoiceStatus =
  | "idle"
  | "connecting"
  | "session_started"
  | "listening"
  | "speech_detected"
  | "finalizing_audio"
  | "processing"
  | "playing_response"
  | "ending"
  | "ended"
  | "error";

type PatientCareAiVoiceProviderTrace = {
  sttProvider: string | null;
  llmProvider: string | null;
  ttsProvider: string | null;
};

type PatientCareAiVoiceConfig = {
  heartbeatIntervalMs: number;
  speechStartThreshold: number;
  speechEndThreshold: number;
  minSpeechMs: number;
  silenceTimeoutMs: number;
  maxUtteranceMs: number;
  autoResumeDelayMs: number;
};

type PatientCareAiVoiceTurnMetrics = {
  uploadDurationMs: number;
  uploadToProcessGapMs: number;
  sttDurationMs: number;
  careAiDurationMs: number;
  ttsDurationMs: number;
  totalDurationMs: number;
  captureBytes: number;
  sttProvider: string;
  llmProvider: string;
  ttsProvider: string;
  ttsFallbackReason: string;
};

const PATIENT_VOICE_AUDIO_BASE64_CHUNK_SIZE = 24 * 1024;
const PATIENT_VOICE_AUDIO_UNLOCK_SRC =
  "data:audio/wav;base64,UklGRlQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YTAAAAA=";
const DEFAULT_PATIENT_VOICE_CONFIG: PatientCareAiVoiceConfig = {
  heartbeatIntervalMs: 15000,
  speechStartThreshold: 0.03,
  speechEndThreshold: 0.015,
  minSpeechMs: 300,
  silenceTimeoutMs: 1500,
  maxUtteranceMs: 30000,
  autoResumeDelayMs: 500,
};

function selectPatientVoiceMimeType() {
  const candidates = [
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/ogg;codecs=opus",
    "audio/ogg",
  ];
  return candidates.find((candidate) => MediaRecorder.isTypeSupported(candidate)) || "";
}

function resolvePatientVoiceAudioExtension(type: string) {
  const normalized = type.toLowerCase();
  if (normalized.includes("ogg")) {
    return "ogg";
  }
  return "webm";
}

async function blobToBase64(blob: Blob) {
  const buffer = await blob.arrayBuffer();
  let binary = "";
  const bytes = new Uint8Array(buffer);
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
  }
  return btoa(binary);
}

function splitVoiceBase64Chunks(base64: string, chunkSize = PATIENT_VOICE_AUDIO_BASE64_CHUNK_SIZE) {
  const chunks: string[] = [];
  for (let index = 0; index < base64.length; index += chunkSize) {
    chunks.push(base64.slice(index, index + chunkSize));
  }
  return chunks;
}

function formatPatientVoiceProvider(value: string | null | undefined) {
  if (!value) return "Not used";
  return value.replace(/[_-]+/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
}

function isPatientPortalLocalDev() {
  if (typeof window === "undefined") {
    return false;
  }
  return window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
}

function sanitizePatientPortalErrorMessage(value: string) {
  const normalized = value.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, "[redacted]");
  const lower = normalized.toLowerCase();
  if (lower.includes("phone is required") || lower.includes("mobile") || lower.includes("patient mobile")) {
    return "Enter a valid 10-digit Indian mobile number.";
  }
  if (lower.includes("invalid otp") || lower.includes("otp must be 6 digits")) {
    return "Enter the valid 6-digit OTP.";
  }
  if (lower.includes("otp expired")) {
    return "OTP expired. Please request a new OTP.";
  }
  if (lower.includes("sms") || lower.includes("provider") || lower.includes("not available") || lower.includes("disabled")) {
    return "OTP is not available in this environment. Use dev OTP mode or check mock OTP config.";
  }
  return normalized;
}

function patientVoiceStatusLabel(status: PatientCareAiVoiceStatus) {
  switch (status) {
    case "idle":
      return "Idle";
    case "connecting":
      return "Connecting";
    case "session_started":
      return "Ready";
    case "listening":
      return "Listening";
    case "speech_detected":
      return "Listening";
    case "finalizing_audio":
    case "processing":
      return "Processing";
    case "playing_response":
      return "Speaking";
    case "ending":
      return "Stopping";
    case "ended":
      return "Stopped";
    case "error":
      return "Error";
    default:
      return status;
  }
}

function asNumber(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function parsePatientVoiceConfig(value: unknown): PatientCareAiVoiceConfig {
  const source = value && typeof value === "object" ? (value as Record<string, unknown>) : {};
  return {
    heartbeatIntervalMs: asNumber(source.heartbeatIntervalMs, DEFAULT_PATIENT_VOICE_CONFIG.heartbeatIntervalMs),
    speechStartThreshold: asNumber(source.speechStartThreshold, DEFAULT_PATIENT_VOICE_CONFIG.speechStartThreshold),
    speechEndThreshold: asNumber(source.speechEndThreshold, DEFAULT_PATIENT_VOICE_CONFIG.speechEndThreshold),
    minSpeechMs: asNumber(source.minSpeechMs, DEFAULT_PATIENT_VOICE_CONFIG.minSpeechMs),
    silenceTimeoutMs: asNumber(source.silenceTimeoutMs, DEFAULT_PATIENT_VOICE_CONFIG.silenceTimeoutMs),
    maxUtteranceMs: asNumber(source.maxUtteranceMs, DEFAULT_PATIENT_VOICE_CONFIG.maxUtteranceMs),
    autoResumeDelayMs: asNumber(source.autoResumeDelayMs, DEFAULT_PATIENT_VOICE_CONFIG.autoResumeDelayMs),
  };
}

function parsePatientVoiceTurnMetrics(value: unknown): PatientCareAiVoiceTurnMetrics | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const source = value as Record<string, unknown>;
  return {
    uploadDurationMs: asNumber(source.uploadDurationMs, 0),
    uploadToProcessGapMs: asNumber(source.uploadToProcessGapMs, 0),
    sttDurationMs: asNumber(source.sttDurationMs, 0),
    careAiDurationMs: asNumber(source.careAiDurationMs, 0),
    ttsDurationMs: asNumber(source.ttsDurationMs, 0),
    totalDurationMs: asNumber(source.totalDurationMs, 0),
    captureBytes: asNumber(source.captureBytes, 0),
    sttProvider: typeof source.sttProvider === "string" ? source.sttProvider : "",
    llmProvider: typeof source.llmProvider === "string" ? source.llmProvider : "",
    ttsProvider: typeof source.ttsProvider === "string" ? source.ttsProvider : "",
    ttsFallbackReason: typeof source.ttsFallbackReason === "string" ? source.ttsFallbackReason : "",
  };
}

const patientNavItems = [
  { to: "/patient/dashboard", label: "Dashboard", shortLabel: "Home" },
  { to: "/patient/book-appointment", label: "Book Visit", shortLabel: "Book" },
  { to: "/patient/appointments", label: "Appointments", shortLabel: "Visits" },
  { to: "/patient/prescriptions", label: "Prescriptions", shortLabel: "Rx" },
  { to: "/patient/bills", label: "Bills", shortLabel: "Bills" },
  { to: "/patient/notifications", label: "Notifications", shortLabel: "Notify" },
  { to: "/patient/lab", label: "Lab Reports", shortLabel: "Lab" },
  { to: "/patient/careai", label: "AIVA", shortLabel: "AIVA" },
  { to: "/patient/profile", label: "Profile", shortLabel: "Profile" },
];

function formatDate(value: string | null | undefined) {
  return formatDisplayDate(value);
}

function formatTime(value: string | null | undefined) {
  return formatDisplayTime(value);
}

function formatDateTime(value: string | null | undefined) {
  return formatDisplayDateTime(value);
}

function formatDateTimeFromParts(dateValue: string | null | undefined, timeValue: string | null | undefined) {
  return formatDisplayDateTimeFromParts(dateValue, timeValue);
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

function isUpcomingAppointment(appointment: PatientPortalAppointmentResponse) {
  const status = (appointment.status ?? "").toUpperCase();
  if (status === "CANCELLED" || status === "NO_SHOW" || status === "COMPLETED") {
    return false;
  }
  if (!appointment.appointmentDate) {
    return false;
  }
  const appointmentAt = new Date(`${appointment.appointmentDate}T${appointment.appointmentTime ?? "23:59"}:00`);
  if (Number.isNaN(appointmentAt.getTime())) {
    return true;
  }
  return appointmentAt.getTime() >= Date.now();
}

function formatDoctorDisplayName(value: string | null | undefined) {
  const normalized = value?.trim() ?? "";
  const withoutTitle = normalized.replace(/^dr\.?\s+/i, "").trim();
  return withoutTitle || normalized;
}

function normalizeUuidOrNull(value: string | null | undefined) {
  return isUuid(value) ? value.trim() : null;
}

type BookingDoctorChoice = {
  publicDoctorId: string;
  doctorSlug: string;
  doctorName: string;
  specialization: string | null;
  qualification: string | null;
  consultationRoom: string | null;
  yearsOfExperience: number | null;
  clinicName: string | null;
  clinicSlug: string | null;
  availableToday: boolean;
  nextAvailableSlotSummary: string | null;
};

function mapPublicDoctorSummaryToBookingChoice(doctor: PublicDoctorSummaryResponse): BookingDoctorChoice {
  return {
    publicDoctorId: doctor.publicDoctorId,
    doctorSlug: doctor.doctorSlug,
    doctorName: doctor.doctorDisplayName,
    specialization: doctor.speciality,
    qualification: null,
    consultationRoom: null,
    yearsOfExperience: doctor.yearsOfExperience,
    clinicName: doctor.clinicDisplayName,
    clinicSlug: doctor.clinicSlug,
    availableToday: doctor.availableToday,
    nextAvailableSlotSummary: doctor.nextAvailableSlotSummary,
  };
}

function mapPublicDoctorDetailToBookingChoice(
  doctor: PublicDoctorDetailResponse,
  clinicName: string | null,
  clinicSlug: string | null,
): BookingDoctorChoice {
  return {
    publicDoctorId: doctor.publicDoctorId,
    doctorSlug: doctor.doctorSlug,
    doctorName: doctor.doctorDisplayName,
    specialization: doctor.specialities[0] ?? null,
    qualification: doctor.qualification,
    consultationRoom: null,
    yearsOfExperience: doctor.yearsOfExperience,
    clinicName,
    clinicSlug,
    availableToday: doctor.availableToday,
    nextAvailableSlotSummary: doctor.nextAvailableSlots[0] ?? null,
  };
}

function buildPatientPortalOtpContext(context: {
  clinicId: string | null;
  clinicSlug: string | null;
  clinicCode?: string | null;
  tenantId: string | null;
  doctorId: string | null;
  nextPath: string | null;
}) {
  const payload = {
    clinicId: isUuid(context.clinicId) ? context.clinicId : undefined,
    clinicSlug: context.clinicSlug || context.clinicCode || undefined,
    tenantId: isUuid(context.tenantId) ? context.tenantId : undefined,
    doctorId: context.doctorId || undefined,
    appointmentIntent: context.nextPath || undefined,
  };
  const entries = Object.entries(payload).filter(([, value]) => Boolean(value));
  if (!entries.length) {
    return undefined;
  }
  return Object.fromEntries(entries) as Record<string, string>;
}

type PatientPortalPendingRegistrationState = {
  mobile: string;
  nextPath: string | null;
  clinicId: string | null;
  clinicSlug: string | null;
  clinicName: string | null;
  tenantId: string | null;
  tenantCode: string | null;
  doctorId: string | null;
  doctorSlug: string | null;
  doctorName: string | null;
  registrationSessionToken: string | null;
  createdAt: string | null;
};

function storePatientPortalPendingRegistration(state: PatientPortalPendingRegistrationState | null) {
  if (typeof window === "undefined") {
    return;
  }
  if (!state) {
    window.sessionStorage.removeItem(PATIENT_PORTAL_PENDING_REGISTRATION_STORAGE_KEY);
    return;
  }
  window.sessionStorage.setItem(PATIENT_PORTAL_PENDING_REGISTRATION_STORAGE_KEY, JSON.stringify(state));
}

function readPatientPortalPendingRegistration(): PatientPortalPendingRegistrationState | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.sessionStorage.getItem(PATIENT_PORTAL_PENDING_REGISTRATION_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<PatientPortalPendingRegistrationState>;
    if (!parsed.mobile) {
      return null;
    }
    return {
      mobile: parsed.mobile,
      nextPath: parsed.nextPath ?? null,
      clinicId: parsed.clinicId ?? null,
      clinicSlug: parsed.clinicSlug ?? null,
      clinicName: parsed.clinicName ?? null,
      tenantId: parsed.tenantId ?? null,
      tenantCode: parsed.tenantCode ?? null,
      doctorId: parsed.doctorId ?? null,
      doctorSlug: parsed.doctorSlug ?? null,
      doctorName: parsed.doctorName ?? null,
      registrationSessionToken: parsed.registrationSessionToken ?? null,
      createdAt: parsed.createdAt ?? null,
    };
  } catch {
    return null;
  }
}

function buildPatientRegistrationPath(
  mobile: string,
  context: {
    clinicId: string | null;
    clinicSlug: string | null;
    clinicName: string | null;
    tenantId: string | null;
    tenantCode?: string | null;
    doctorId: string | null;
    doctorSlug: string | null;
    doctorName: string | null;
    nextPath: string | null;
  },
) {
  const params = new URLSearchParams();
  params.set("mobile", mobile);
  if (context.nextPath) {
    params.set("next", context.nextPath);
  }
  if (context.clinicId) {
    params.set("clinicId", context.clinicId);
  }
  if (context.clinicSlug) {
    params.set("clinicSlug", context.clinicSlug);
    params.set("clinic", context.clinicSlug);
  }
  if (context.clinicName) {
    params.set("clinicName", context.clinicName);
  }
  if (context.tenantId) {
    params.set("tenantId", context.tenantId);
    params.set("tenant", context.tenantId);
  }
  if (context.tenantCode) {
    params.set("clinicCode", context.tenantCode);
  }
  if (context.doctorId) {
    params.set("doctorId", context.doctorId);
  }
  if (context.doctorSlug) {
    params.set("doctorSlug", context.doctorSlug);
  }
  if (context.doctorName) {
    params.set("doctorName", context.doctorName);
  }
  return `/patient/register?${params.toString()}`;
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
            <span className="eyebrow">Jeevanam Healthcare Patient Portal</span>
            <h2>{session.patientLabel}</h2>
            <p>{session.phone}</p>
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
            Sign out
          </button>
        </aside>

        <div className="patient-main">
          <div className="patient-topbar">
            <div>
              <span className="eyebrow">Patient Portal</span>
              <h1>{title}</h1>
              <p>{subtitle}</p>
            </div>
            <div className="patient-status-card">
              <strong>{session.patientLabel}</strong>
              <small>{session.phone}</small>
            </div>
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
        <h1>Complete registration to continue.</h1>
        <p>Your OTP is already verified. Open the registration form to finish patient setup and continue booking.</p>
        <div className="cta-row">
          <Link className="primary-button" to={nextPath ? `/patient/register?next=${encodeURIComponent(nextPath)}` : "/patient/register"}>
            Continue registration
          </Link>
          <button className="ghost-button" type="button" onClick={onClearSession}>
            Start over
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
  const location = useLocation();
  const nextPath = `${location.pathname}${location.search}`;
  if (!session) {
    return (
      <section className="page-section narrow-page">
        <div className="login-placeholder patient-guard-card">
          <span className="eyebrow">Patient login required</span>
          <h1>Sign in with phone OTP to continue.</h1>
          <p>Use your mobile number to open your patient portal and continue booking.</p>
          <div className="cta-row">
            <Link className="primary-button" to={`/patient/login?next=${encodeURIComponent(nextPath)}`}>
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
    return <RegistrationRequiredCard onClearSession={onSignOut} nextPath={nextPath} />;
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
    return <div className="patient-empty-card">Loading...</div>;
  }
  if (error) {
    return (
      <div className="patient-empty-card">
        <strong>Something went wrong</strong>
        <p>{error}</p>
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
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const portalClinicContext = useMemo(
    () => resolvePatientPortalContext(searchParams, location.state),
    [location.state, searchParams],
  );
  const patientAuthContext = useMemo(
    () => resolvePatientAuthContext(searchParams, location.state),
    [location.state, searchParams],
  );
  const [tenantCode, setTenantCode] = useState(portalClinicContext.clinicCode);
  const [clinicName, setClinicName] = useState(portalClinicContext.clinicName);
  const [phone, setPhone] = useState(patientAuthContext.mobile ?? "");
  const [otp, setOtp] = useState("");
  const [requestState, setRequestState] = useState<PatientPortalOtpRequestResponse | null>(null);
  const [requestMessage, setRequestMessage] = useState<string | null>(null);
  const [verifyMessage, setVerifyMessage] = useState<string | null>(null);
  const [requestPending, setRequestPending] = useState(false);
  const [verifyPending, setVerifyPending] = useState(false);
  const [phoneTouched, setPhoneTouched] = useState(false);
  const [otpTouched, setOtpTouched] = useState(false);
  const [requestAttempted, setRequestAttempted] = useState(false);
  const [verifyAttempted, setVerifyAttempted] = useState(false);
  const loginNotice = searchParams.get("message")?.trim() || null;
  const doctorSlug = portalClinicContext.doctorSlug;
  const [doctorDetail, setDoctorDetail] = useState<FetchState<PublicDoctorDetailResponse | null>>({
    data: null,
    loading: Boolean(doctorSlug),
    error: null,
  });

  useEffect(() => {
    if (!doctorSlug) {
      setDoctorDetail({
        data: null,
        loading: false,
        error: null,
      });
      return;
    }

    const abortController = new AbortController();
    setDoctorDetail((current) => ({
      data: current.data,
      loading: true,
      error: null,
    }));

    fetchPublicJson<PublicDoctorDetailResponse>(`/api/public/doctors/${doctorSlug}`, {}, abortController.signal)
      .then((result) => {
        setDoctorDetail({
          data: result,
          loading: false,
          error: null,
        });
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) {
          return;
        }
        setDoctorDetail({
          data: null,
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load clinic options.",
        });
      });

    return () => abortController.abort();
  }, [doctorSlug]);

  useEffect(() => {
    setTenantCode(portalClinicContext.clinicCode);
    setClinicName(portalClinicContext.clinicName);
    if (session) {
      setPhone(session.phone);
      return;
    }
    if (patientAuthContext.mobile) {
      setPhone(patientAuthContext.mobile);
    }
  }, [patientAuthContext.mobile, session, portalClinicContext.clinicCode, portalClinicContext.clinicName]);

  useEffect(() => {
    savePublicBookingContext({
      clinicId: portalClinicContext.clinicId,
      clinicSlug: portalClinicContext.clinicSlug,
      doctorId: portalClinicContext.doctorId,
      tenantId: portalClinicContext.tenantId,
      nextPath: portalClinicContext.nextPath,
      mobile: patientAuthContext.mobile ?? null,
    });
  }, [patientAuthContext.mobile, portalClinicContext]);

  useEffect(() => {
    if (!doctorDetail.data || session || doctorDetail.data.clinics.length !== 1) {
      return;
    }
    const onlyClinic = doctorDetail.data.clinics[0];
    if (tenantCode.trim() === onlyClinic.clinicSlug && clinicName === onlyClinic.clinicDisplayName) {
      return;
    }
    setTenantCode(onlyClinic.clinicSlug);
    setClinicName(onlyClinic.clinicDisplayName);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("clinicSlug", onlyClinic.clinicSlug);
    if (portalClinicContext.doctorId) {
      nextParams.set("doctorId", portalClinicContext.doctorId);
    }
    if (portalClinicContext.nextPath) {
      nextParams.set("next", portalClinicContext.nextPath);
    }
    setSearchParams(nextParams, { replace: true });
  }, [
    clinicName,
    doctorDetail.data,
    portalClinicContext.doctorName,
    portalClinicContext.doctorSlug,
    portalClinicContext.nextPath,
    searchParams,
    session,
    setSearchParams,
    tenantCode,
  ]);

  function syncClinicContext(nextClinicCode: string, nextClinicName: string | null) {
    setTenantCode(nextClinicCode);
    setClinicName(nextClinicName);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("clinicSlug", nextClinicCode);
    if (portalClinicContext.doctorId) {
      nextParams.set("doctorId", portalClinicContext.doctorId);
    }
    if (portalClinicContext.nextPath) {
      nextParams.set("next", portalClinicContext.nextPath);
    }
    setSearchParams(nextParams, { replace: true });
    savePublicBookingContext({
      clinicId: portalClinicContext.clinicId,
      clinicSlug: nextClinicCode,
      doctorId: portalClinicContext.doctorId,
      tenantId: portalClinicContext.tenantId,
      nextPath: portalClinicContext.nextPath,
    });
  }

  const resolvedClinicCode = tenantCode.trim();
  const resolvedClinicName = clinicName || portalClinicContext.clinicName || null;
  const hasClinicContext = Boolean(resolvedClinicCode);
  const doctorSelectionRequired = Boolean(doctorDetail.data?.clinics.length && doctorDetail.data.clinics.length > 1) && !hasClinicContext;
  const bookingContextLine = hasClinicContext && resolvedClinicName
    ? portalClinicContext.doctorName
      ? `Booking appointment with Dr ${portalClinicContext.doctorName} at ${resolvedClinicName}`
      : `Accessing patient portal for ${resolvedClinicName}`
    : null;
  const normalizedPhone = sanitizePatientPhoneInput(phone);
  const phoneValidation = indianMobileNumber().safeParse(normalizedPhone);
  const otpValidation = otpVerifySchema.shape.otp.safeParse(otp);
  const canRequestOtp = Boolean(phoneValidation.success) && !requestPending;
  const canVerifyOtp = Boolean(phoneValidation.success && otpValidation.success && requestState?.accepted) && !verifyPending;
  const phoneError =
    (phoneTouched || requestAttempted || verifyAttempted) && !phoneValidation.success
      ? "Enter a valid 10-digit Indian mobile number."
      : null;
  const otpError =
    (otpTouched || verifyAttempted) && !otpValidation.success
      ? "Enter a valid 6-digit OTP."
      : null;
  const otpDigits = sanitizePatientOtpInput(otp);

  function clearOtpFlowMessages() {
    setRequestMessage(null);
    setVerifyMessage(null);
    setRequestState(null);
  }

  async function handleRequestOtp(event: FormEvent) {
    event.preventDefault();
    setRequestAttempted(true);
    setPhoneTouched(true);
    if (import.meta.env.DEV) {
      console.debug("[patient-login-context]", portalClinicContext);
    }
    if (!phoneValidation.success) {
      setRequestMessage(null);
      setVerifyMessage(null);
      return;
    }
    setRequestPending(true);
    clearOtpFlowMessages();
    try {
      const context = buildPatientPortalOtpContext(portalClinicContext);
      const requestPayload = {
        mobile: normalizedPhone,
        ...(context ? { context } : {}),
      };
      if (import.meta.env.DEV) {
        console.debug("[patient-login-otp-payload]", requestPayload);
      }
      const response = await postPatientPortalJson<PatientPortalOtpRequestResponse>("/api/patient-portal/auth/otp/request", {
        mobile: normalizedPhone,
        ...(context ? { context } : {}),
      });
      setRequestState(response);
      setRequestMessage(response.message || "OTP sent successfully.");
    } catch (error) {
      setRequestState(null);
      setRequestMessage(
        error instanceof Error
          ? sanitizePatientPortalErrorMessage(error.message)
          : "OTP is not available in this environment. Use dev OTP mode or check mock OTP config.",
      );
    } finally {
      setRequestPending(false);
    }
  }

  async function handleVerifyOtp(event: FormEvent) {
    event.preventDefault();
    setVerifyAttempted(true);
    setPhoneTouched(true);
    setOtpTouched(true);
    if (!phoneValidation.success || !otpValidation.success) {
      setVerifyMessage(null);
      return;
    }
    if (!requestState?.accepted) {
      setVerifyMessage("Request an OTP first.");
      return;
    }
    setVerifyPending(true);
    try {
      const context = buildPatientPortalOtpContext(portalClinicContext);
      const response = await postPatientPortalJson<PatientPortalOtpVerifyResponse>("/api/patient-portal/auth/otp/verify", {
        mobile: normalizedPhone,
        otp: otpDigits,
        ...(context ? { context } : {}),
      });
      setVerifyMessage(response.message || "OTP verified successfully.");
      const sessionTenantCode = response.tenantCode || resolvedClinicCode;
      if (response.verified && response.patientSessionToken && response.tenantId && response.patientDisplayName && sessionTenantCode) {
        storePatientPortalPendingRegistration(null);
        onSaveSession({
          mode: "otp",
          sessionRole: "patient",
          tenantCode: sessionTenantCode,
          tenantId: response.tenantId,
          phone: normalizedPhone,
          patientLabel: response.patientDisplayName,
          createdAt: new Date().toISOString(),
          patientSessionToken: response.patientSessionToken,
        });
        navigate(portalClinicContext.nextPath?.startsWith("/patient/") ? portalClinicContext.nextPath : "/patient/dashboard");
        return;
      }
      if (response.verified && response.registrationRequired) {
        storePatientPortalPendingRegistration({
          mobile: normalizedPhone,
          nextPath: portalClinicContext.nextPath,
          clinicId: portalClinicContext.clinicId,
          clinicSlug: portalClinicContext.clinicSlug || portalClinicContext.clinicCode || null,
          clinicName: portalClinicContext.clinicName,
          tenantId: response.tenantId || portalClinicContext.tenantId,
          tenantCode: sessionTenantCode || null,
          doctorId: portalClinicContext.doctorId,
          doctorSlug: portalClinicContext.doctorSlug,
          doctorName: portalClinicContext.doctorName,
          registrationSessionToken: response.registrationSessionToken,
          createdAt: new Date().toISOString(),
        });
      }
      if (response.verified && response.registrationRequired && response.registrationSessionToken && response.tenantId && sessionTenantCode) {
        onSaveSession({
          mode: "otp",
          sessionRole: "registration",
          tenantCode: sessionTenantCode,
          tenantId: response.tenantId,
          phone: normalizedPhone,
          patientLabel: "New patient",
          createdAt: new Date().toISOString(),
          patientSessionToken: response.registrationSessionToken,
        });
        if (portalClinicContext.nextPath?.startsWith("/patient/")) {
          navigate(`/patient/register?next=${encodeURIComponent(portalClinicContext.nextPath)}`);
          return;
        }
        navigate("/patient/register");
        return;
      }
      if (response.verified && response.registrationRequired) {
        navigate(
          buildPatientRegistrationPath(normalizedPhone, {
            clinicId: portalClinicContext.clinicId,
            clinicSlug: portalClinicContext.clinicSlug || portalClinicContext.clinicCode || null,
            clinicName: portalClinicContext.clinicName,
            tenantId: response.tenantId || portalClinicContext.tenantId,
            tenantCode: sessionTenantCode || portalClinicContext.clinicCode || null,
            doctorId: portalClinicContext.doctorId,
            doctorSlug: portalClinicContext.doctorSlug,
            doctorName: portalClinicContext.doctorName,
            nextPath: portalClinicContext.nextPath,
          }),
        );
        return;
      }
      if (response.verified) {
        setVerifyMessage("Unable to resolve your patient portal session after OTP verification.");
      }
    } catch (error) {
      setVerifyMessage(
        error instanceof Error
          ? sanitizePatientPortalErrorMessage(error.message)
          : "Unable to verify OTP right now. Please try again.",
      );
    } finally {
      setVerifyPending(false);
    }
  }

  return (
    <section className="page-section narrow-page">
      <div className="section-heading">
        <span className="eyebrow">Patient portal access</span>
        <h1>Sign in with phone number and OTP.</h1>
        <p>Access appointments, prescriptions, bills, reports, and care updates securely.</p>
      </div>
      <div className="login-placeholder portal-login-card">
        {doctorDetail.loading ? <div className="patient-inline-empty">Loading clinic options...</div> : null}
        {doctorDetail.error ? (
          <div className="patient-inline-empty">
            <strong>Clinic options unavailable</strong>
            <p>Unable to load clinic options right now. Please try again.</p>
          </div>
        ) : null}
        {doctorSelectionRequired && doctorDetail.data ? (
          <DoctorClinicSelector
            doctorName={portalClinicContext.doctorName || doctorDetail.data.doctorDisplayName}
            clinics={doctorDetail.data.clinics}
            selectedClinicCode={resolvedClinicCode}
            nextAvailableSlot={doctorDetail.data.nextAvailableSlots[0] ?? null}
            onSelect={(clinic) => syncClinicContext(clinic.clinicSlug, clinic.clinicDisplayName)}
          />
        ) : null}
        {bookingContextLine ? (
          <div className="patient-inline-empty">
            <strong>{bookingContextLine}</strong>
          </div>
        ) : null}

        <form className="patient-login-form" onSubmit={handleRequestOtp} noValidate>
          {loginNotice ? (
            <div className="patient-inline-empty">
              <strong>Patient registration</strong>
              <p>{loginNotice}</p>
            </div>
          ) : null}
          <label>
            <span>Phone number</span>
            <input
              value={phone}
              onChange={(event) => {
                setPhone(event.target.value);
                clearOtpFlowMessages();
              }}
              onBlur={() => setPhoneTouched(true)}
              placeholder="Enter 10-digit mobile number"
              autoComplete="tel"
              inputMode="tel"
              aria-invalid={Boolean(phoneError)}
              aria-describedby={phoneError ? "patient-login-phone-error" : undefined}
            />
            {phoneError ? (
              <p id="patient-login-phone-error" className="patient-field-error">
                {phoneError}
              </p>
            ) : null}
          </label>
          <div className="patient-login-actions">
            <button className="primary-button wide-button" type="submit" disabled={requestPending || !canRequestOtp}>
              {requestPending ? "Requesting OTP..." : "Request OTP"}
            </button>
          </div>
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
            {isPatientPortalLocalDev() && requestState?.accepted ? (
              <span>
                Dev OTP: <strong>{requestState.devOtp || "123456"}</strong>
              </span>
            ) : null}
          </div>
        ) : null}

        <form className="patient-login-form" onSubmit={handleVerifyOtp} noValidate>
          <label>
            <span>OTP code</span>
            <input
              value={otp}
              onChange={(event) => {
                setOtp(sanitizePatientOtpInput(event.target.value));
                setVerifyMessage(null);
              }}
              onBlur={() => setOtpTouched(true)}
              placeholder="6-digit code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              aria-invalid={Boolean(otpError)}
              aria-describedby={otpError ? "patient-login-otp-error" : undefined}
            />
            {otpError ? (
              <p id="patient-login-otp-error" className="patient-field-error">
                {otpError}
              </p>
            ) : null}
          </label>
          <button className="secondary-button wide-button" type="submit" disabled={verifyPending || !canVerifyOtp}>
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
            Open {branding.productName} Admin Console
          </a>
          {session ? (
            <>
              <button className="secondary-button" type="button" onClick={() => navigate(patientPortalHomePath(session))}>
                {isPatientPortalRegistrationSession(session) ? "Continue registration" : "Open current session"}
              </button>
              <button className="ghost-button" type="button" onClick={onClearSession}>
                {isPatientPortalRegistrationSession(session) ? "Start over" : "Sign out"}
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
  const pendingRegistration = useMemo(readPatientPortalPendingRegistration, []);
  const nextPath = searchParams.get("next") || pendingRegistration?.nextPath || null;
  const recoverableRegistrationSession = useMemo<PatientPortalRegistrationSession | null>(() => {
    if (isPatientPortalRegistrationSession(session) && isPatientRegistrationSessionActive(session)) {
      return session;
    }
    if (
      pendingRegistration?.registrationSessionToken
      && pendingRegistration.tenantId
      && pendingRegistration.tenantCode
      && pendingRegistration.mobile
      && pendingRegistration.createdAt
    ) {
      const recoveredSession: PatientPortalRegistrationSession = {
        mode: "otp",
        sessionRole: "registration",
        tenantCode: pendingRegistration.tenantCode,
        tenantId: pendingRegistration.tenantId,
        phone: pendingRegistration.mobile,
        patientLabel: "New patient",
        createdAt: pendingRegistration.createdAt,
        patientSessionToken: pendingRegistration.registrationSessionToken,
      };
      return isPatientRegistrationSessionActive(recoveredSession) ? recoveredSession : null;
    }
    return null;
  }, [pendingRegistration, session]);
  const registrationMobile = recoverableRegistrationSession?.phone || pendingRegistration?.mobile || searchParams.get("mobile") || "";
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
  const registrationPayload = useMemo(() => ({
    firstName,
    lastName,
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
  }), [firstName, lastName, gender, dateOfBirth, ageYears, email, addressLine1, addressLine2, city, state, country, postalCode, emergencyContactName, emergencyContactMobile]);
  const registrationPreview = useMemo(
    () => patientQuickRegisterSchema.safeParse({ ...registrationPayload, mobile: registrationMobile }),
    [registrationMobile, registrationPayload],
  );
  const registrationFieldErrors: Record<string, string> = registrationPreview.success ? {} : mapZodErrors(registrationPreview.error);

  useEffect(() => {
    if (!isPatientPortalPatientSession(session)) {
      return;
    }
    navigate(nextPath?.startsWith("/patient/") ? nextPath : "/patient/dashboard");
  }, [navigate, nextPath, session]);

  useEffect(() => {
    if (!session && recoverableRegistrationSession) {
      onSaveSession(recoverableRegistrationSession);
    }
  }, [onSaveSession, recoverableRegistrationSession, session]);

  useEffect(() => {
    if (recoverableRegistrationSession) {
      return;
    }
    clearPatientRegistrationSession();
    onClearSession();
    navigate("/patient/login?message=Please%20verify%20your%20mobile%20number%20again.", { replace: true });
  }, [navigate, onClearSession, recoverableRegistrationSession]);

  function handleStartOver() {
    clearPatientRegistrationSession();
    onClearSession();
    navigate("/patient/login", { replace: true });
  }

  if (!recoverableRegistrationSession) {
    return (
      <section className="page-section narrow-page">
        <div className="section-heading">
          <span className="eyebrow">Quick registration</span>
          <h1>Complete patient registration.</h1>
          <p>Your verified mobile number is ready for registration. Continue from a verified OTP session to open the form.</p>
        </div>
        <div className="login-placeholder patient-guard-card">
          <div className="patient-inline-empty">
            <strong>Verified mobile</strong>
            <label>
              <span>Mobile number</span>
              <input value={registrationMobile || ""} readOnly />
            </label>
            {pendingRegistration?.doctorName || pendingRegistration?.clinicName ? (
              <p>
                {pendingRegistration.doctorName ? `Dr ${pendingRegistration.doctorName}` : "Clinic context"}
                {pendingRegistration.clinicName ? ` · ${pendingRegistration.clinicName}` : ""}
              </p>
            ) : null}
          </div>
          <p>Request and verify OTP again if the registration session expired.</p>
          <div className="cta-row">
            <Link className="primary-button" to={nextPath?.startsWith("/patient/") ? `/patient/login?next=${encodeURIComponent(nextPath)}` : "/patient/login"}>
              Go to patient login
            </Link>
          </div>
        </div>
      </section>
    );
  }

  if (!isPatientPortalRegistrationSession(recoverableRegistrationSession)) {
    return null;
  }
  const registrationSession = recoverableRegistrationSession;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!registrationPreview.success) {
      setError(registrationPreview.error.issues[0]?.message || "Unable to complete patient registration.");
      return;
    }
    const payload: PatientPortalRegistrationRequest = registrationPayload;

    setSubmitPending(true);
    setError(null);
    setMessage(null);
    try {
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
      storePatientPortalPendingRegistration(null);
      setMessage(response.message);
      const nextPath = searchParams.get("next") || pendingRegistration?.nextPath;
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
        <h1>Complete your patient registration.</h1>
        <p>Step 1 is already complete. Add the remaining details to finish registration and continue booking.</p>
      </div>
      <div className="login-placeholder portal-login-card">
        <div className="portal-feature-list portal-step-list">
          <article className="feature-card">
            <strong>Step 1</strong>
            <p>Phone OTP verification is complete.</p>
          </article>
          <article className="feature-card">
            <strong>Step 2</strong>
            <p>Complete quick registration or link an existing patient record.</p>
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
              <input value={firstName} onChange={(event) => setFirstName(event.target.value)} required autoComplete="given-name" aria-invalid={Boolean(registrationFieldErrors.firstName)} />
              {registrationFieldErrors.firstName ? <p className="patient-field-error">{registrationFieldErrors.firstName}</p> : null}
            </label>
            <label>
              <span>Last name</span>
              <input value={lastName} onChange={(event) => setLastName(event.target.value)} required autoComplete="family-name" />
              {registrationFieldErrors.lastName ? <p className="patient-field-error">{registrationFieldErrors.lastName}</p> : null}
            </label>
            <label>
              <span>Gender</span>
              <select value={gender} onChange={(event) => setGender(event.target.value)} aria-invalid={Boolean(registrationFieldErrors.gender)}>
                <option value="UNKNOWN">Prefer not to say</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
              {registrationFieldErrors.gender ? <p className="patient-field-error">{registrationFieldErrors.gender}</p> : null}
            </label>
            <label>
              <span>Date of birth</span>
              <input type="date" value={dateOfBirth} onChange={(event) => setDateOfBirth(event.target.value)} max={new Date().toISOString().slice(0, 10)} aria-invalid={Boolean(registrationFieldErrors.dateOfBirth)} />
              {registrationFieldErrors.dateOfBirth ? <p className="patient-field-error">{registrationFieldErrors.dateOfBirth}</p> : null}
            </label>
            <label>
              <span>Age</span>
              <input value={ageYears} onChange={(event) => setAgeYears(event.target.value)} inputMode="numeric" placeholder="If DOB is not available" aria-invalid={Boolean(registrationFieldErrors.ageYears)} />
              {registrationFieldErrors.ageYears ? <p className="patient-field-error">{registrationFieldErrors.ageYears}</p> : null}
            </label>
            <label>
              <span>Mobile number</span>
              <input value={registrationSession.phone} readOnly />
            </label>
            <label>
              <span>City</span>
              <input value={city} onChange={(event) => setCity(event.target.value)} required autoComplete="address-level2" aria-invalid={Boolean(registrationFieldErrors.city)} />
              {registrationFieldErrors.city ? <p className="patient-field-error">{registrationFieldErrors.city}</p> : null}
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
            <button className="primary-button" type="submit" disabled={submitPending || !registrationPreview.success}>
              {submitPending ? "Completing..." : "Complete registration"}
            </button>
            <button className="ghost-button" type="button" onClick={handleStartOver}>
              Cancel / Start over
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
      subtitle="See your next visit, recent prescription, bills, and care updates in one place."
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
                  {dashboard.data.nextAppointment.clinicName ?? dashboard.data.clinicName} ·{" "}
                  {formatDateTimeFromParts(dashboard.data.nextAppointment.appointmentDate, dashboard.data.nextAppointment.appointmentTime)}
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
              <div className="patient-inline-empty">No prescription summary is available yet.</div>
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
            <h2>AIVA</h2>
            <Link to="/patient/careai">Open AIVA</Link>
          </div>
          <div className="patient-highlight-card">
            <strong>Use AIVA for guided booking and visit prep</strong>
            <span>{dashboard.data?.clinicName ?? "Clinic"} patient view</span>
            <p>Ask AIVA to help book a clinic visit, summarize medicines, or explain billing totals.</p>
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
  const upcomingAppointments = useMemo(
    () => appointments.data.filter(isUpcomingAppointment),
    [appointments.data],
  );

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Appointments"
      subtitle="View your upcoming and past clinic visits."
    >
      <PatientPortalApiState
        loading={appointments.loading}
        error={appointments.error}
        empty={appointments.data.length === 0}
        emptyTitle="No upcoming appointments."
        emptyMessage="Book a new clinic visit when you are ready."
      >
        <div className="patient-action-strip">
          <Link className="primary-button" to="/patient/book-appointment">
            Book appointment
          </Link>
        </div>
        {upcomingAppointments.length ? (
          <div className="patient-highlight-card patient-guidance-card">
            <strong>Need to cancel or reschedule?</strong>
            <p>To cancel or reschedule, please use AIVA or contact receptionist.</p>
            <div className="patient-action-row">
              <Link className="secondary-button" to="/patient/careai">
                Open AIVA
              </Link>
            </div>
          </div>
        ) : null}
        <div className="patient-card-stack">
          {appointments.data.map((appointment) => (
            <article key={`${appointment.doctorName ?? "doctor"}-${appointment.appointmentDate}-${appointment.appointmentTime ?? "time"}`} className="patient-record-card">
              <div className="record-card-top">
                <div>
                  <strong>{appointment.doctorName ?? "Doctor to be confirmed"}</strong>
                  <span>
                    {appointment.clinicName ?? "Clinic"} · {formatDateTimeFromParts(appointment.appointmentDate, appointment.appointmentTime)}
                  </span>
                </div>
                <span className="status-pill">{formatStatusLabel(appointment.status)}</span>
              </div>
              <p>{appointment.reason ?? "Visit reason is not available for this appointment."}</p>
              <div className="record-card-meta">
                <span>Source: {appointment.source ?? "Not available"}</span>
              </div>
              {(appointment.status ?? "").toUpperCase() !== "CANCELLED" &&
              (appointment.status ?? "").toUpperCase() !== "NO_SHOW" &&
              (appointment.status ?? "").toUpperCase() !== "COMPLETED" ? (
                <div className="patient-inline-note">
                  To cancel or reschedule, please use AIVA or contact receptionist.
                </div>
              ) : null}
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
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const recentAppointments = usePatientPortalResource<PatientPortalAppointmentResponse[]>(portalSession, "/api/patient-portal/appointments", []);
  const publicBookingContext = useMemo(
    () => getPublicBookingContext(searchParams, location.state),
    [location.state, searchParams],
  );
  const [publicDoctorsState, setPublicDoctorsState] = useState<FetchState<PublicDoctorSummaryResponse[]>>({
    data: [],
    loading: false,
    error: null,
  });
  const [selectedClinicFilter, setSelectedClinicFilter] = useState("");
  const [selectedSpeciality, setSelectedSpeciality] = useState("All");
  const [selectedDoctorId, setSelectedDoctorId] = useState("");
  const [selectedDoctorSlug, setSelectedDoctorSlug] = useState("");
  const [selectedClinicId, setSelectedClinicId] = useState("");
  const [selectedClinicSlug, setSelectedClinicSlug] = useState("");
  const [selectedTenantId, setSelectedTenantId] = useState("");
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [reason, setReason] = useState("");
  const [slots, setSlots] = useState<PatientPortalDoctorSlotResponse[]>([]);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [slotsError, setSlotsError] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<PatientPortalDoctorSlotResponse | null>(null);
  const [submitPending, setSubmitPending] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<PatientPortalAppointmentConfirmationResponse | null>(null);
  const [clinicContextDetail, setClinicContextDetail] = useState<FetchState<PublicClinicDetailResponse | null>>({
    data: null,
    loading: false,
    error: null,
  });
  const [doctorContextDetail, setDoctorContextDetail] = useState<FetchState<PublicDoctorDetailResponse | null>>({
    data: null,
    loading: false,
    error: null,
  });
  const lastSlotRequestKeyRef = useRef<string | null>(null);
  const manualDoctorSelectionRef = useRef(false);
  const currentBookingPath = `${location.pathname}${location.search}`;
  const bookingClinicCode = publicBookingContext.clinicSlug || publicBookingContext.clinicCode || null;
  const bookingClinicId = normalizeUuidOrNull(publicBookingContext.clinicId);
  const bookingTenantId = normalizeUuidOrNull(publicBookingContext.tenantId);
  const bookingClinicName = publicBookingContext.clinicName?.trim() || searchParams.get("clinicName")?.trim() || null;
  const bookingDoctorName = formatDoctorDisplayName(publicBookingContext.doctorName || searchParams.get("doctorName"));
  const bookingDoctorSlug = publicBookingContext.doctorSlug || null;
  const bookingDoctorId = publicBookingContext.doctorId || null;
  const hasBookingContext = Boolean(bookingClinicCode || bookingClinicId || bookingTenantId || bookingDoctorSlug || bookingDoctorId);
  const bookingContextLine = bookingClinicName
    ? bookingDoctorName
      ? `Booking appointment with Dr ${bookingDoctorName} at ${bookingClinicName}`
      : `Booking with ${bookingClinicName}`
    : bookingDoctorName
      ? `Booking appointment with Dr ${bookingDoctorName}`
      : null;
  useEffect(() => {
    savePublicBookingContext({
      clinicId: bookingClinicId,
      clinicSlug: publicBookingContext.clinicSlug ?? publicBookingContext.clinicCode ?? null,
      tenantId: bookingTenantId,
      doctorId: publicBookingContext.doctorId ?? null,
      nextPath: currentBookingPath,
    });
  }, [bookingClinicId, bookingTenantId, currentBookingPath, publicBookingContext]);

  useEffect(() => {
    const abortController = new AbortController();
    setPublicDoctorsState((current) => ({
      data: current.data,
      loading: true,
      error: null,
    }));

    fetchPublicJson<{ items: PublicDoctorSummaryResponse[] }>("/api/public/doctors", { size: 100 }, abortController.signal)
      .then((result) => {
        setPublicDoctorsState({
          data: result.items,
          loading: false,
          error: null,
        });
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) {
          return;
        }
        setPublicDoctorsState({
          data: [],
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load doctors right now.",
        });
      });

    return () => abortController.abort();
  }, []);

  useEffect(() => {
    if (!bookingClinicCode) {
      setClinicContextDetail({
        data: null,
        loading: false,
        error: null,
      });
      return;
    }
    const hasExplicitClinicQuery =
      searchParams.has("clinicId")
      || searchParams.has("clinicCode")
      || searchParams.has("clinic")
      || searchParams.has("clinicSlug")
      || searchParams.has("tenantId")
      || searchParams.has("tenant")
      || searchParams.has("tenantSlug");
    if (bookingClinicCode === "demo-clinic" && !hasExplicitClinicQuery) {
      setClinicContextDetail({
        data: null,
        loading: false,
        error: null,
      });
      return;
    }

    const abortController = new AbortController();
    setClinicContextDetail({
      data: null,
      loading: true,
      error: null,
    });

    fetchPublicJson<PublicClinicDetailResponse>(`/api/public/clinics/${bookingClinicCode}`, {}, abortController.signal)
      .then((result) => {
        setClinicContextDetail({
          data: result,
          loading: false,
          error: null,
        });
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) {
          return;
        }
        setClinicContextDetail({
          data: null,
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load clinic doctors right now.",
        });
      });

    return () => abortController.abort();
  }, [bookingClinicCode, searchParams]);

  function syncBookingClinicContext(clinicSlug: string) {
    const nextClinicId = normalizeUuidOrNull(publicBookingContext.clinicId);
    const nextTenantId = normalizeUuidOrNull(publicBookingContext.tenantId);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("clinicSlug", clinicSlug);
    if (selectedDoctorId) {
      nextParams.set("doctorId", selectedDoctorId);
    }
    setSearchParams(nextParams, { replace: true });
    setSelectedClinicId(nextClinicId || "");
    setSelectedClinicSlug(clinicSlug);
    setSelectedTenantId(nextTenantId || "");
    setSelectedSlot(null);
    savePublicBookingContext({
      clinicId: nextClinicId,
      clinicSlug,
      tenantId: nextTenantId,
      doctorId: selectedDoctorId || null,
      nextPath: currentBookingPath,
    });
  }

  useEffect(() => {
    if (bookingClinicCode) {
      setSelectedClinicFilter(bookingClinicCode);
    }
  }, [bookingClinicCode]);

  const allDoctorOptions = useMemo<BookingDoctorChoice[]>(() => {
    if (clinicContextDetail.data?.doctors.length) {
      return clinicContextDetail.data.doctors.map(mapPublicDoctorSummaryToBookingChoice);
    }
    if (doctorContextDetail.data) {
      const clinic = doctorContextDetail.data.clinics[0] ?? null;
      return [
        mapPublicDoctorDetailToBookingChoice(
          doctorContextDetail.data,
          clinic?.clinicDisplayName ?? bookingClinicName,
          clinic?.clinicSlug ?? bookingClinicCode,
        ),
      ];
    }
    if (!hasBookingContext) {
      return publicDoctorsState.data.map(mapPublicDoctorSummaryToBookingChoice);
    }
    return [];
  }, [bookingClinicCode, bookingClinicName, clinicContextDetail.data, doctorContextDetail.data, hasBookingContext, publicDoctorsState.data]);

  const clinicOptions = useMemo(
    () =>
      Array.from(
        new Map(
          publicDoctorsState.data.map((doctor) => [
            doctor.clinicSlug,
            { clinicSlug: doctor.clinicSlug, clinicName: doctor.clinicDisplayName },
          ]),
        ).values(),
      ).sort((left, right) => left.clinicName.localeCompare(right.clinicName)),
    [publicDoctorsState.data],
  );

  const doctorOptions = useMemo(() => {
    return allDoctorOptions.filter((doctor) => {
      if (selectedClinicFilter && doctor.clinicSlug !== selectedClinicFilter) {
        return false;
      }
      if (selectedSpeciality !== "All" && doctor.specialization !== selectedSpeciality) {
        return false;
      }
      return true;
    });
  }, [allDoctorOptions, selectedClinicFilter, selectedSpeciality]);

  const specialities = useMemo(() => {
    const values = new Set<string>();
    allDoctorOptions.forEach((doctor) => {
      if (doctor.specialization) {
        values.add(doctor.specialization);
      }
    });
    return ["All", ...Array.from(values).sort((left, right) => left.localeCompare(right))];
  }, [allDoctorOptions]);

  useEffect(() => {
    if (selectedDoctorId) {
      return;
    }
    if (manualDoctorSelectionRef.current) {
      return;
    }

    const seededDoctor =
      (bookingDoctorId
        ? allDoctorOptions.find((doctor) => doctor.publicDoctorId === bookingDoctorId) ?? null
        : null)
      || (bookingDoctorSlug
        ? allDoctorOptions.find((doctor) => doctor.doctorSlug === bookingDoctorSlug) ?? null
        : null)
      || (bookingDoctorName
        ? allDoctorOptions.find((doctor) => formatDoctorDisplayName(doctor.doctorName) === bookingDoctorName) ?? null
        : null)
      || allDoctorOptions[0]
      || null;

    if (seededDoctor) {
      setSelectedDoctorId(seededDoctor.publicDoctorId);
      setSelectedDoctorSlug(seededDoctor.doctorSlug);
      if (bookingClinicCode) {
        setSelectedClinicId(bookingClinicId || "");
        setSelectedClinicSlug(bookingClinicCode);
        setSelectedTenantId(bookingTenantId || "");
      } else if (doctorContextDetail.data?.clinics.length === 1 && seededDoctor.clinicSlug) {
        setSelectedClinicId("");
        setSelectedClinicSlug(seededDoctor.clinicSlug);
        setSelectedTenantId("");
      }
    }
  }, [allDoctorOptions, bookingClinicCode, bookingClinicId, bookingTenantId, bookingDoctorId, bookingDoctorName, bookingDoctorSlug, doctorContextDetail.data, selectedDoctorId]);

  function handleStartOver() {
    clearPublicBookingContext();
    setSearchParams(new URLSearchParams(), { replace: true });
    setSelectedClinicFilter("");
    setSelectedSpeciality("All");
    setSelectedDoctorId("");
    setSelectedDoctorSlug("");
    setSelectedClinicId("");
    setSelectedClinicSlug("");
    setSelectedTenantId("");
    setSelectedSlot(null);
    setSlots([]);
    setSlotsError(null);
    setConfirmation(null);
    setSubmitError(null);
    lastSlotRequestKeyRef.current = null;
    manualDoctorSelectionRef.current = false;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const selectedSlotTime = selectedSlot?.slotTime ?? "";
    if (!portalSession || !selectedDoctorId || !selectedDate || !selectedSlotTime) {
      setSubmitError("Choose a doctor, date, and available time slot before confirming.");
      return;
    }
    const parsed = bookAppointmentSchema.safeParse({
      doctorId: selectedDoctorId,
      appointmentDate: selectedDate,
      slot: selectedSlotTime,
      reason: reason.trim() || undefined,
      appointmentType: "SCHEDULED",
    });
    if (!parsed.success) {
      setSubmitError(parsed.error.issues[0]?.message || "Unable to confirm the appointment.");
      return;
    }

    setSubmitPending(true);
    setSubmitError(null);
    setConfirmation(null);
    try {
      const resolvedClinicId = normalizeUuidOrNull(selectedClinicId) || bookingClinicId;
      const resolvedTenantId = normalizeUuidOrNull(selectedTenantId) || bookingTenantId;
      const payload: PatientPortalAppointmentBookingRequest = {
        publicDoctorId: selectedDoctorId,
        clinicSlug: resolvedSelectedClinicSlug,
        tenantId: resolvedTenantId,
        clinicId: resolvedClinicId,
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
      setSelectedSlot(null);
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "Unable to confirm the appointment.");
    } finally {
      setSubmitPending(false);
    }
  }

  const selectedDoctor = useMemo(
    () => allDoctorOptions.find((doctor) => doctor.publicDoctorId === selectedDoctorId) ?? null,
    [allDoctorOptions, selectedDoctorId],
  );
  const selectedDoctorClinics = doctorContextDetail.data?.clinics ?? [];
  const resolvedSelectedClinicSlug =
    selectedClinicSlug
    || bookingClinicCode
    || (selectedDoctorClinics.length === 1 ? selectedDoctor?.clinicSlug : null);
  const resolvedSelectedClinicId = normalizeUuidOrNull(selectedClinicId) || bookingClinicId;
  const resolvedSelectedTenantId = normalizeUuidOrNull(selectedTenantId) || bookingTenantId;
  const selectedSlotTime = selectedSlot?.slotTime ?? "";
  const requiresClinicSelection = Boolean(
    selectedDoctorId && !resolvedSelectedClinicSlug && selectedDoctorClinics.length > 1,
  );
  const availableSlots = slots.filter((slot) => slot.selectable);
  const bookingDoctorsLoading = clinicContextDetail.loading || doctorContextDetail.loading || publicDoctorsState.loading;
  const bookingDoctorsError = doctorContextDetail.error || clinicContextDetail.error || publicDoctorsState.error;
  const recentDoctorOptions = useMemo(
    () =>
      Array.from(
        new Map(
          recentAppointments.data
            .filter((appointment) => appointment.doctorName)
            .map((appointment) => [
              `${appointment.doctorName}-${appointment.clinicName ?? ""}`,
              {
                doctorName: appointment.doctorName as string,
                clinicName: appointment.clinicName,
              },
            ]),
        ).values(),
      ).slice(0, 4),
    [recentAppointments.data],
  );

  const activeDoctorSlug = selectedDoctorSlug || selectedDoctor?.doctorSlug || bookingDoctorSlug || "";

  useEffect(() => {
    if (!activeDoctorSlug) {
      setDoctorContextDetail({
        data: null,
        loading: false,
        error: null,
      });
      return;
    }

    const abortController = new AbortController();
    setDoctorContextDetail({
      data: null,
      loading: true,
      error: null,
    });

    fetchPublicJson<PublicDoctorDetailResponse>(`/api/public/doctors/${activeDoctorSlug}`, {}, abortController.signal)
      .then((result) => {
        if (isPatientPortalLocalDev()) {
          console.debug("[patient-portal] selected doctor detail", result);
        }
        setDoctorContextDetail({
          data: result,
          loading: false,
          error: null,
        });
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) {
          return;
        }
        setDoctorContextDetail({
          data: null,
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load doctor details right now.",
        });
      });

    return () => abortController.abort();
  }, [activeDoctorSlug]);

  useEffect(() => {
    if (bookingDoctorId && doctorOptions.some((doctor) => doctor.publicDoctorId === bookingDoctorId)) {
      const matchedDoctor = doctorOptions.find((doctor) => doctor.publicDoctorId === bookingDoctorId) ?? null;
      setSelectedDoctorId(bookingDoctorId);
      setSelectedDoctorSlug(matchedDoctor?.doctorSlug ?? bookingDoctorSlug ?? "");
      return;
    }

    if (bookingDoctorSlug) {
      const matchedDoctor = doctorOptions.find((doctor) => doctor.doctorSlug === bookingDoctorSlug) ?? null;
      if (matchedDoctor) {
        setSelectedDoctorId(matchedDoctor.publicDoctorId);
        setSelectedDoctorSlug(matchedDoctor.doctorSlug);
        return;
      }
    }

    if (selectedDoctorId && doctorOptions.some((doctor) => doctor.publicDoctorId === selectedDoctorId)) {
      return;
    }

    const firstDoctor = doctorOptions[0] ?? null;
    if (firstDoctor) {
      setSelectedDoctorId(firstDoctor.publicDoctorId);
      setSelectedDoctorSlug(firstDoctor.doctorSlug);
    } else {
      setSelectedDoctorId("");
      setSelectedDoctorSlug("");
      setSelectedClinicId("");
      setSelectedClinicSlug("");
    }
  }, [bookingDoctorId, bookingDoctorSlug, doctorOptions, selectedDoctorId]);

  useEffect(() => {
    if (!selectedDoctorId) {
      setSelectedClinicId("");
      setSelectedClinicSlug("");
      return;
    }

    if (bookingClinicCode) {
      setSelectedClinicId(bookingClinicId || "");
      setSelectedClinicSlug(bookingClinicCode);
      setSelectedTenantId(bookingTenantId || "");
      return;
    }

    const doctorDetailClinics = doctorContextDetail.data?.clinics ?? [];
    const resolvedClinics = doctorDetailClinics;

    if (resolvedClinics.length === 1) {
      const onlyClinic = resolvedClinics[0];
      if (selectedClinicSlug !== onlyClinic.clinicSlug) {
        setSelectedClinicId("");
        setSelectedClinicSlug(onlyClinic.clinicSlug);
      }
      return;
    }

    if (resolvedClinics.length > 1 && selectedClinicSlug && !resolvedClinics.some((clinic) => clinic.clinicSlug === selectedClinicSlug)) {
      setSelectedClinicId("");
      setSelectedClinicSlug("");
      return;
    }

    if (!selectedClinicSlug && selectedClinicId) {
      setSelectedClinicId("");
    }
  }, [bookingClinicCode, bookingClinicId, bookingTenantId, doctorContextDetail.data, doctorOptions, selectedClinicId, selectedClinicSlug, selectedDoctorId]);

  useEffect(() => {
    const slotRequestDoctorId = selectedDoctorId || "";
    const slotRequestClinicSlug = resolvedSelectedClinicSlug || "";
    const slotRequestClinicId = resolvedSelectedClinicId || "";
    const slotRequestTenantId = resolvedSelectedTenantId || "";
    const slotRequestKey = `${slotRequestDoctorId}|${slotRequestClinicId}|${slotRequestTenantId}|${slotRequestClinicSlug}|${selectedDate}`;

    if (!slotRequestDoctorId || !slotRequestClinicSlug || !selectedDate) {
      setSlots([]);
      setSlotsLoading(false);
      setSlotsError(null);
      lastSlotRequestKeyRef.current = null;
      return;
    }

    if (!portalSession) {
      setSlots([]);
      setSlotsLoading(false);
      setSlotsError(null);
      lastSlotRequestKeyRef.current = null;
      return;
    }

    if (lastSlotRequestKeyRef.current === slotRequestKey) {
      return;
    }
    lastSlotRequestKeyRef.current = slotRequestKey;

    const abortController = new AbortController();
    setSelectedSlot(null);
    setConfirmation(null);
    setSubmitError(null);
    setSlotsLoading(true);
    setSlotsError(null);

    if (isPatientPortalLocalDev()) {
      console.debug("[patient-portal] booking URL doctorId", bookingDoctorId);
      console.debug("[patient-portal] selectedDoctor", {
        publicDoctorId: selectedDoctor?.publicDoctorId ?? null,
        id: selectedDoctorId || null,
        slug: selectedDoctor?.doctorSlug || null,
        clinicId: resolvedSelectedClinicId,
        tenantId: resolvedSelectedTenantId,
      });
      console.debug("[patient-portal] slot request payload", {
        slotRequestKey,
        slotRequestDoctorId,
        clinicSlug: slotRequestClinicSlug,
        clinicId: slotRequestClinicId,
        tenantId: slotRequestTenantId,
        date: selectedDate,
      });
    }

    loadPatientPortalDoctorSlots(
      {
        doctorId: slotRequestDoctorId,
        clinicSlug: slotRequestClinicSlug,
        tenantId: slotRequestTenantId || null,
        clinicId: slotRequestClinicId || null,
        date: selectedDate,
      },
      portalSession,
      abortController.signal,
    )
      .then((result) => {
        if (abortController.signal.aborted) {
          return;
        }
        if (isPatientPortalLocalDev()) {
          console.debug("[patient-portal] slot response", result);
        }
        setSlots(result);
        setSlotsLoading(false);
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) {
          return;
        }
        if (isPatientPortalLocalDev()) {
          console.debug("[patient-portal] slot error", error);
        }
        setSlots([]);
        setSlotsLoading(false);
        const message = error instanceof Error ? error.message : "";
        if (message.toLowerCase().includes("clinic is not available for online booking")) {
          setSlotsError("We could not load slots for this doctor. Please try another date or choose another doctor.");
        } else {
          setSlotsError(message || "We could not load slots for this doctor. Please try another date or choose another doctor.");
        }
      });

    return () => abortController.abort();
  }, [
    portalSession,
    resolvedSelectedClinicId,
    resolvedSelectedClinicSlug,
    resolvedSelectedTenantId,
    selectedDate,
    selectedDoctorId,
  ]);

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Book an in-clinic appointment"
      subtitle="Choose a doctor, pick a convenient slot, and confirm your visit."
    >
      {bookingContextLine ? (
        <div className="portal-banner">
          <strong>{bookingContextLine}</strong>
          <p>Your selected doctor or clinic is ready for booking.</p>
        </div>
      ) : null}
      {requiresClinicSelection && doctorContextDetail.data ? (
        <DoctorClinicSelector
          doctorName={publicBookingContext.doctorName || doctorContextDetail.data.doctorDisplayName}
          clinics={doctorContextDetail.data.clinics}
          selectedClinicCode={selectedClinicSlug || bookingClinicCode || ""}
          nextAvailableSlot={doctorContextDetail.data.nextAvailableSlots[0] ?? null}
          onSelect={(clinic) => {
            setSelectedClinicId("");
            setSelectedClinicSlug(clinic.clinicSlug);
            setSelectedTenantId("");
            syncBookingClinicContext(clinic.clinicSlug);
          }}
        />
      ) : null}
      {(!requiresClinicSelection && bookingDoctorsLoading) ? (
        <div className="patient-empty-card">Loading available doctors...</div>
      ) : null}
      {!hasBookingContext ? (
        <div className="patient-empty-card">
          <strong>Choose how you want to book your visit</strong>
          <p>Select a doctor or clinic to see available appointments.</p>
          {recentDoctorOptions.length ? (
            <div className="patient-action-row">
              {recentDoctorOptions.map((doctor) => (
                <button
                  key={`${doctor.doctorName}-${doctor.clinicName ?? ""}`}
                  className="ghost-button"
                  type="button"
                  onClick={() => {
                    const matched = publicDoctorsState.data.find((candidate) => candidate.doctorDisplayName === doctor.doctorName);
                    if (!matched) {
                      return;
                    }
                    const nextParams = new URLSearchParams();
                    nextParams.set("doctorId", matched.publicDoctorId);
                    nextParams.set("clinicSlug", matched.clinicSlug);
                    setSearchParams(nextParams, { replace: true });
                  }}
                >
                  {doctor.doctorName}
                </button>
              ))}
            </div>
          ) : null}
          <div className="patient-action-row">
            <Link className="primary-button" to="/doctors">
              Search doctors
            </Link>
            <Link className="secondary-button" to="/clinics">
              Browse clinics
            </Link>
            <Link className="ghost-button" to="/">
              Go back to discovery
            </Link>
          </div>
        </div>
      ) : null}
      {bookingDoctorsError ? (
        <div className="patient-empty-card">
          <strong>Unable to load doctors right now</strong>
          <p>{bookingDoctorsError}</p>
          <div className="patient-action-row">
            <Link className="primary-button" to="/doctors">
              Browse available doctors
            </Link>
            <Link className="secondary-button" to="/clinics">
              Choose another clinic
            </Link>
            <button className="ghost-button" type="button" onClick={handleStartOver}>
              Start over
            </button>
          </div>
        </div>
      ) : null}
      {!bookingDoctorsError && !requiresClinicSelection && !bookingDoctorsLoading && allDoctorOptions.length === 0 ? (
        <div className="patient-empty-card">
          <strong>No doctors are available for online booking right now.</strong>
          <p>Try browsing doctors or clinics to continue.</p>
          <div className="patient-action-row">
            <Link className="primary-button" to="/doctors">
              Browse doctors
            </Link>
            <Link className="secondary-button" to="/clinics">
              Browse clinics
            </Link>
            <button className="ghost-button" type="button" onClick={handleStartOver}>
              Go back home
            </button>
          </div>
        </div>
      ) : null}
      {!bookingDoctorsError && !requiresClinicSelection && !bookingDoctorsLoading && allDoctorOptions.length > 0 ? (
        <>
          {!hasBookingContext ? (
            <section className="patient-panel">
              <div className="patient-panel-heading">
                <h2>Find your doctor</h2>
              </div>
              <div className="patient-form-grid">
                <label>
                  <span>Clinic</span>
                  <select value={selectedClinicFilter} onChange={(event) => setSelectedClinicFilter(event.target.value)}>
                    <option value="">All clinics</option>
                    {clinicOptions.map((clinic) => (
                      <option key={clinic.clinicSlug} value={clinic.clinicSlug}>
                        {clinic.clinicName}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>Specialty</span>
                  <select value={selectedSpeciality} onChange={(event) => setSelectedSpeciality(event.target.value)}>
                    {specialities.map((speciality) => (
                      <option key={speciality} value={speciality}>
                        {speciality}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </section>
          ) : null}
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
                  onClick={() => {
                    manualDoctorSelectionRef.current = true;
                    if (isPatientPortalLocalDev()) {
                      console.debug("[patient-portal] manualDoctorClick", {
                        publicDoctorId: doctor.publicDoctorId,
                        doctorSlug: doctor.doctorSlug,
                        clinicSlug: doctor.clinicSlug,
                      });
                    }
                    setSelectedDoctorId(doctor.publicDoctorId);
                    setSelectedDoctorSlug(doctor.doctorSlug);
                    if (bookingClinicCode) {
                      setSelectedClinicId(bookingClinicId || "");
                      setSelectedClinicSlug(bookingClinicCode);
                      setSelectedTenantId(bookingTenantId || "");
                    } else if (doctorContextDetail.data?.clinics.length === 1 && doctor.clinicSlug) {
                      setSelectedClinicId("");
                      setSelectedClinicSlug(doctor.clinicSlug);
                      setSelectedTenantId("");
                    } else {
                      setSelectedClinicId("");
                      setSelectedClinicSlug("");
                      setSelectedTenantId("");
                    }
                    setSelectedSlot(null);
                    setConfirmation(null);
                    setSubmitError(null);
                    const nextParams = new URLSearchParams(searchParams);
                    nextParams.set("doctorId", doctor.publicDoctorId);
                    if (bookingClinicCode || (doctorContextDetail.data?.clinics.length === 1 && doctor.clinicSlug)) {
                      const clinicSlug = bookingClinicCode || doctor.clinicSlug || "";
                      nextParams.set("clinicSlug", clinicSlug);
                    }
                    setSearchParams(nextParams, { replace: true });
                    savePublicBookingContext({
                      clinicId: bookingClinicId,
                      clinicSlug: bookingClinicCode || (doctorContextDetail.data?.clinics.length === 1 ? doctor.clinicSlug : null),
                      tenantId: bookingTenantId,
                      doctorId: doctor.publicDoctorId,
                      nextPath: currentBookingPath,
                    });
                  }}
                >
                  <strong>{doctor.doctorName}</strong>
                  <span>{doctor.specialization ?? "General consultation"}</span>
                  <small>
                    {doctor.qualification ?? doctor.clinicName ?? "Clinic doctor"}
                    {doctor.consultationRoom ? ` · ${doctor.consultationRoom}` : ""}
                    {doctor.yearsOfExperience ? ` · ${doctor.yearsOfExperience} yrs exp` : ""}
                    {doctor.nextAvailableSlotSummary ? ` · ${doctor.nextAvailableSlotSummary}` : ""}
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
                <strong>No slots available for this date.</strong>
                <p>Try the next day or choose another doctor to continue.</p>
              </div>
            ) : null}

            {availableSlots.length > 0 ? (
              <div className="booking-slot-grid">
                {availableSlots.map((slot) => (
                  <button
                    key={`${slot.appointmentDate}-${slot.slotTime}`}
                    className={`booking-slot-card${selectedSlotTime === slot.slotTime ? " is-active" : ""}`}
                    type="button"
                    onClick={() => setSelectedSlot(slot)}
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
                  {selectedDoctor?.clinicName ?? "Clinic"} {selectedDate ? `· ${selectedSlotTime ? formatDateTimeFromParts(selectedDate, selectedSlotTime) : formatDate(selectedDate)}` : ""}
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
                    {confirmation.doctorName ?? "Doctor"} · {formatDateTimeFromParts(confirmation.appointmentDate, confirmation.appointmentTime)}
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
        </>
      ) : null}
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
      subtitle="Review medicines, advice, and follow-up details shared by your clinic."
    >
      <PatientPortalApiState
        loading={prescriptions.loading}
        error={prescriptions.error}
        empty={prescriptions.data.length === 0}
        emptyTitle="No prescriptions available."
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
                  <span>{prescription.diagnosisSummary ?? "No diagnosis summary is available."}</span>
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
      subtitle="Review your bills, payments, and receipt summaries."
    >
      <PatientPortalApiState
        loading={bills.loading}
        error={bills.error}
        empty={bills.data.length === 0}
        emptyTitle="No bills available."
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
                  <span>
                    {bill.billType ?? "Bill"} · {formatDate(bill.billDate)}
                  </span>
                </div>
                <span className="status-pill">{formatStatusLabel(bill.status)}</span>
              </div>

              <div className="record-card-meta">
                <span>Type: {bill.billType ?? "Bill"}</span>
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

export function PatientNotificationsPage({ session, onSignOut }: { session: PatientPortalSession | null; onSignOut: () => void }) {
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const [refreshKey, setRefreshKey] = useState(0);
  const notifications = usePatientPortalResource<PatientPortalNotificationResponse[]>(
    portalSession,
    `/api/patient-portal/notifications?refresh=${refreshKey}`,
    [],
  );
  const [workingId, setWorkingId] = useState<string | null>(null);

  function actionPath(notification: PatientPortalNotificationResponse) {
    return notification.actionPath ?? switchActionPath(notification.sourceType);
  }

  function notificationDisplayTitle(notification: PatientPortalNotificationResponse) {
    const subject = notification.subject?.trim() || "";
    if (subject && !isUuid(subject)) {
      return subject;
    }
    const sourceType = notification.sourceType?.trim() || notification.eventType.trim();
    return sourceType
      .replaceAll("_", " ")
      .toLowerCase()
      .replace(/(^|\s)\w/g, (char) => char.toUpperCase());
  }

  function switchActionPath(sourceType: string | null) {
    switch ((sourceType ?? "").toUpperCase()) {
      case "APPOINTMENT":
        return "/patient/appointments";
      case "PRESCRIPTION":
        return "/patient/prescriptions";
      case "BILL":
      case "RECEIPT":
        return "/patient/bills";
      case "LAB_ORDER":
        return "/patient/lab";
      default:
        return null;
    }
  }

  async function handleMarkRead(id: string) {
    if (!portalSession) {
      return;
    }
    setWorkingId(id);
    try {
      await markPatientNotificationRead(portalSession, id);
      setRefreshKey((current) => current + 1);
    } finally {
      setWorkingId(null);
    }
  }

  const unreadCount = notifications.data.filter((notification) => !notification.readAt).length;

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title="Notifications"
      subtitle="Stay updated with appointment, prescription, bill, and lab notifications."
    >
      <PatientPortalApiState
        loading={notifications.loading}
        error={notifications.error}
        empty={notifications.data.length === 0}
        emptyTitle="You’re all caught up."
        emptyMessage="Your portal notifications will appear here once the clinic sends them."
      >
        <div className="portal-dashboard-grid">
          <article className="dashboard-card">
            <strong>Total</strong>
            <span>{notifications.data.length}</span>
          </article>
          <article className="dashboard-card">
            <strong>Unread</strong>
            <span>{unreadCount}</span>
          </article>
          <article className="dashboard-card">
            <strong>Read</strong>
            <span>{notifications.data.length - unreadCount}</span>
          </article>
        </div>

        <div className="portal-list">
          {notifications.data.map((notification) => (
            <article key={notification.id} className="portal-list-card">
              <div className="portal-list-card-header">
                <strong>{notificationDisplayTitle(notification)}</strong>
                <span className={`status-pill status-${notification.readAt ? "success" : "warning"}`}>
                  {notification.readAt ? "Read" : "Unread"}
                </span>
              </div>
              <div className="portal-list-meta">
                <span>{formatDateTime(notification.createdAt)}</span>
                <span>{notification.sourceType ?? "Notification"}</span>
                <span>{notification.status}</span>
              </div>
              <p className="portal-help-text">{notification.message}</p>
              <div className="cta-row">
                {actionPath(notification) ? (
                  <Link className="secondary-button" to={actionPath(notification) as string}>
                    Open related page
                  </Link>
                ) : null}
                {!notification.readAt ? (
                  <button className="ghost-button" type="button" disabled={workingId === notification.id} onClick={() => void handleMarkRead(notification.id)}>
                    {workingId === notification.id ? "Marking..." : "Mark as read"}
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
  const [voiceStatus, setVoiceStatus] = useState<PatientCareAiVoiceStatus>("idle");
  const [voiceError, setVoiceError] = useState<string | null>(null);
  const [voiceInfo, setVoiceInfo] = useState<string | null>(null);
  const [voiceTranscript, setVoiceTranscript] = useState<string>("");
  const [voiceAssistant, setVoiceAssistant] = useState<string>("");
  const [voiceMuted, setVoiceMuted] = useState(false);
  const [voiceAudioUrl, setVoiceAudioUrl] = useState<string | null>(null);
  const [voiceReplyReadyToPlay, setVoiceReplyReadyToPlay] = useState(false);
  const [voiceSessionId, setVoiceSessionId] = useState<string | null>(null);
  const [voiceProviderTrace, setVoiceProviderTrace] = useState<PatientCareAiVoiceProviderTrace | null>(null);
  const [voiceConfig, setVoiceConfig] = useState<PatientCareAiVoiceConfig>(DEFAULT_PATIENT_VOICE_CONFIG);
  const [voiceTurnMetrics, setVoiceTurnMetrics] = useState<PatientCareAiVoiceTurnMetrics | null>(null);
  const [voiceEvents, setVoiceEvents] = useState<string[]>([]);
  const [voiceConnectionTargetUrl, setVoiceConnectionTargetUrl] = useState<string | null>(null);
  const [voiceConnectionCloseCode, setVoiceConnectionCloseCode] = useState<number | null>(null);
  const [voiceConnectionCloseReason, setVoiceConnectionCloseReason] = useState<string | null>(null);
  const [voiceMicLevel, setVoiceMicLevel] = useState(0);
  const [voiceMicPeak, setVoiceMicPeak] = useState(0);
  const [voiceSpeechDetected, setVoiceSpeechDetected] = useState(false);
  const [voiceSilenceDetected, setVoiceSilenceDetected] = useState(false);
  const [showVoiceTechnicalDetails, setShowVoiceTechnicalDetails] = useState(false);
  const [voiceInactivityWarning, setVoiceInactivityWarning] = useState<string | null>(null);
  const chatEndRef = useRef<HTMLDivElement | null>(null);
  const voiceSocketRef = useRef<WebSocket | null>(null);
  const voiceRecorderRef = useRef<MediaRecorder | null>(null);
  const voiceStreamRef = useRef<MediaStream | null>(null);
  const voiceChunksRef = useRef<Blob[]>([]);
  const voicePendingAudioRef = useRef<Map<number, string>>(new Map());
  const voiceExpectedAudioChunksRef = useRef(0);
  const voiceAudioElementRef = useRef<HTMLAudioElement | null>(null);
  const voiceHeartbeatTimerRef = useRef<number | null>(null);
  const voiceResumeTimerRef = useRef<number | null>(null);
  const voiceStatusRef = useRef<PatientCareAiVoiceStatus>("idle");
  const voiceConfigRef = useRef<PatientCareAiVoiceConfig>(DEFAULT_PATIENT_VOICE_CONFIG);
  const voiceEndedByUserRef = useRef(false);
  const voiceStartMicPendingRef = useRef(false);
  const voiceDiscardRecordingRef = useRef(false);
  const voiceStartingMicRef = useRef(false);
  const voiceAutoResumeRef = useRef(false);
  const voiceAutoStopTriggeredRef = useRef(false);
  const voiceSpeechDetectedRef = useRef(false);
  const voiceSpeechFrameActiveRef = useRef(false);
  const voiceSpeechStartedAtRef = useRef<number | null>(null);
  const voiceLastSpeechAtRef = useRef<number | null>(null);
  const voiceSessionStartedAtRef = useRef<number | null>(null);
  const voiceLastTranscriptOrResponseAtRef = useRef<number | null>(null);
  const voiceAudioContextRef = useRef<AudioContext | null>(null);
  const voiceAnalyserRef = useRef<AnalyserNode | null>(null);
  const voiceSourceNodeRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const voiceLevelDataRef = useRef<Uint8Array | null>(null);
  const voiceMonitoringIntervalRef = useRef<number | null>(null);
  const voiceTurnUploadStartedAtRef = useRef<number | null>(null);
  const voiceTurnSubmittedAtRef = useRef<number | null>(null);
  const voicePlaybackStartedAtRef = useRef<number | null>(null);
  const voiceAutoplayUnlockAttemptedRef = useRef(false);
  const voiceAssistantAudioPendingRef = useRef(false);
  const voiceAssistantAudioReadyRef = useRef(false);
  const voiceAssistantAudioPlayingRef = useRef(false);
  const voicePendingAutoPlayRef = useRef(false);
  const patientVoiceResumeStorageKey = portalSession
    ? `patient-careai-voice-resume:${portalSession.tenantId}:${portalSession.patientSessionToken}`
    : null;

  function updateVoiceStatus(nextStatus: PatientCareAiVoiceStatus) {
    voiceStatusRef.current = nextStatus;
    setVoiceStatus(nextStatus);
  }

  function appendVoiceEvent(message: string) {
    setVoiceEvents((current) => [...current.slice(-11), `${formatDisplayDateTime(new Date())} • ${message}`]);
  }

  function storedVoiceResumeSessionId() {
    if (!patientVoiceResumeStorageKey) {
      return null;
    }
    try {
      return window.sessionStorage.getItem(patientVoiceResumeStorageKey);
    } catch {
      return null;
    }
  }

  function persistVoiceResumeSessionId(nextSessionId: string | null) {
    if (!patientVoiceResumeStorageKey) {
      return;
    }
    try {
      if (nextSessionId) {
        window.sessionStorage.setItem(patientVoiceResumeStorageKey, nextSessionId);
      } else {
        window.sessionStorage.removeItem(patientVoiceResumeStorageKey);
      }
    } catch {
      // Ignore browser storage errors.
    }
  }

  function clearVoiceHeartbeat() {
    if (voiceHeartbeatTimerRef.current != null) {
      window.clearInterval(voiceHeartbeatTimerRef.current);
      voiceHeartbeatTimerRef.current = null;
    }
  }

  function clearVoiceResumeTimer() {
    if (voiceResumeTimerRef.current != null) {
      window.clearTimeout(voiceResumeTimerRef.current);
      voiceResumeTimerRef.current = null;
    }
  }

  function stopVoiceAudioMonitoring() {
    if (voiceMonitoringIntervalRef.current != null) {
      window.clearInterval(voiceMonitoringIntervalRef.current);
      voiceMonitoringIntervalRef.current = null;
    }
    voiceSourceNodeRef.current?.disconnect();
    voiceAnalyserRef.current?.disconnect();
    voiceSourceNodeRef.current = null;
    voiceAnalyserRef.current = null;
    voiceLevelDataRef.current = null;
    if (voiceAudioContextRef.current) {
      void voiceAudioContextRef.current.close().catch(() => undefined);
      voiceAudioContextRef.current = null;
    }
    setVoiceMicLevel(0);
    setVoiceMicPeak(0);
    setVoiceSpeechDetected(false);
    setVoiceSilenceDetected(false);
  }

  function startVoiceAudioMonitoring(stream: MediaStream) {
    stopVoiceAudioMonitoring();
    const AudioContextCtor = window.AudioContext
      || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextCtor) {
      return;
    }
    const contextInstance = new AudioContextCtor();
    const source = contextInstance.createMediaStreamSource(stream);
    const analyser = contextInstance.createAnalyser();
    analyser.fftSize = 2048;
    const data = new Uint8Array(analyser.fftSize);
    source.connect(analyser);
    voiceAudioContextRef.current = contextInstance;
    voiceSourceNodeRef.current = source;
    voiceAnalyserRef.current = analyser;
    voiceLevelDataRef.current = data;
    voiceMonitoringIntervalRef.current = window.setInterval(() => {
      if (!voiceAnalyserRef.current || !voiceLevelDataRef.current) {
        return;
      }
      voiceAnalyserRef.current.getByteTimeDomainData(voiceLevelDataRef.current as Uint8Array<ArrayBuffer>);
      let sumSquares = 0;
      let peak = 0;
      for (const sample of voiceLevelDataRef.current) {
        const normalized = (sample - 128) / 128;
        const absolute = Math.abs(normalized);
        sumSquares += normalized * normalized;
        if (absolute > peak) {
          peak = absolute;
        }
      }
      const rms = Math.sqrt(sumSquares / voiceLevelDataRef.current.length);
      setVoiceMicLevel(rms);
      setVoiceMicPeak(peak);
      const currentVoiceConfig = voiceConfigRef.current;

      const now = Date.now();
      const isSpeechFrame = rms >= currentVoiceConfig.speechStartThreshold;
      if (isSpeechFrame) {
        if (!voiceSpeechFrameActiveRef.current && voiceSpeechDetectedRef.current) {
          appendVoiceEvent("VAD_SPEECH_RESUMED");
        }
        voiceSpeechFrameActiveRef.current = true;
        if (!voiceSpeechDetectedRef.current) {
          voiceSpeechDetectedRef.current = true;
          voiceSpeechStartedAtRef.current = now;
          appendVoiceEvent("VAD_SPEECH_STARTED");
        }
        voiceLastSpeechAtRef.current = now;
        voiceAutoStopTriggeredRef.current = false;
        setVoiceSpeechDetected(true);
        setVoiceSilenceDetected(false);
        if (voiceStatusRef.current === "listening" || voiceStatusRef.current === "session_started") {
          updateVoiceStatus("speech_detected");
        }
      } else {
        if (voiceSpeechFrameActiveRef.current && voiceSpeechDetectedRef.current) {
          appendVoiceEvent("VAD_SPEECH_ENDED");
        }
        voiceSpeechFrameActiveRef.current = false;
        setVoiceSpeechDetected(false);
        setVoiceSilenceDetected(true);
        if (voiceStatusRef.current === "speech_detected") {
          updateVoiceStatus("listening");
        }
      }

      if (voiceSpeechDetectedRef.current && voiceLastSpeechAtRef.current && voiceSpeechStartedAtRef.current) {
        const speechDuration = now - voiceSpeechStartedAtRef.current;
        const silenceDuration = now - voiceLastSpeechAtRef.current;
        if (
          rms <= currentVoiceConfig.speechEndThreshold
          && speechDuration >= currentVoiceConfig.minSpeechMs
          && silenceDuration >= currentVoiceConfig.silenceTimeoutMs
          && !voiceAutoStopTriggeredRef.current
        ) {
          voiceAutoStopTriggeredRef.current = true;
          appendVoiceEvent("SILENCE_TIMEOUT");
          handleVoiceStopTurn();
          return;
        }
      }

      if (
        voiceSessionStartedAtRef.current
        && now - voiceSessionStartedAtRef.current >= currentVoiceConfig.maxUtteranceMs
        && !voiceAutoStopTriggeredRef.current
      ) {
        voiceAutoStopTriggeredRef.current = true;
        appendVoiceEvent("MAX_UTTERANCE_REACHED");
        handleVoiceStopTurn();
      }
    }, 100);
  }

  function scheduleVoiceListeningResume(reason: string, delayMs = voiceConfigRef.current.autoResumeDelayMs) {
    clearVoiceResumeTimer();
    if (
      !voiceAutoResumeRef.current
      || voiceEndedByUserRef.current
      || !voiceSocketRef.current
      || voiceSocketRef.current.readyState !== WebSocket.OPEN
    ) {
      appendVoiceEvent(`AUTO_RESUME_SKIPPED ${reason}`);
      return;
    }
    voiceResumeTimerRef.current = window.setTimeout(() => {
      voiceResumeTimerRef.current = null;
      if (
        !voiceAutoResumeRef.current
        || voiceEndedByUserRef.current
        || !voiceSocketRef.current
        || voiceSocketRef.current.readyState !== WebSocket.OPEN
      ) {
        appendVoiceEvent(`AUTO_RESUME_SKIPPED ${reason}`);
        return;
      }
      if (voiceStatusRef.current !== "session_started" && voiceStatusRef.current !== "idle" && voiceStatusRef.current !== "ended") {
        appendVoiceEvent(`AUTO_RESUME_SKIPPED status=${voiceStatusRef.current}`);
        return;
      }
      appendVoiceEvent(`AUTO_RESUME ${reason}`);
      void startVoiceMic({ automatic: true, reason });
    }, delayMs);
  }

  function stopVoiceStream() {
    voiceRecorderRef.current = null;
    if (voiceStreamRef.current) {
      voiceStreamRef.current.getTracks().forEach((track) => track.stop());
      voiceStreamRef.current = null;
    }
    stopVoiceAudioMonitoring();
  }

  function resetPendingVoiceAudio() {
    voicePendingAudioRef.current.clear();
    voiceExpectedAudioChunksRef.current = 0;
  }

  function clearAssistantAudioFlags() {
    voiceAssistantAudioPendingRef.current = false;
    voiceAssistantAudioReadyRef.current = false;
    voiceAssistantAudioPlayingRef.current = false;
  }

  function cleanupVoiceSessionResources() {
    clearVoiceHeartbeat();
    clearVoiceResumeTimer();
    voiceStartMicPendingRef.current = false;
    voiceStartingMicRef.current = false;
    voiceAutoStopTriggeredRef.current = false;
    voiceSpeechDetectedRef.current = false;
    voiceSpeechFrameActiveRef.current = false;
    voiceSpeechStartedAtRef.current = null;
    voiceLastSpeechAtRef.current = null;
    voiceSessionStartedAtRef.current = null;
    voiceTurnUploadStartedAtRef.current = null;
    voiceTurnSubmittedAtRef.current = null;
    voicePlaybackStartedAtRef.current = null;
    voicePendingAutoPlayRef.current = false;
    stopVoiceStream();
    resetPendingVoiceAudio();
    clearAssistantAudioFlags();
    voiceChunksRef.current = [];
  }

  function closeVoiceSocket() {
    cleanupVoiceSessionResources();
    if (voiceSocketRef.current) {
      const socket = voiceSocketRef.current;
      voiceSocketRef.current = null;
      if (socket.readyState === WebSocket.OPEN) {
        try {
          socket.send(JSON.stringify({ type: "session.close" }));
        } catch {
          // noop
        }
      }
      socket.close();
    }
  }

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
    updateVoiceStatus("idle");
    setVoiceError(null);
    setVoiceInfo(null);
    setVoiceTranscript("");
    setVoiceAssistant("");
    setVoiceReplyReadyToPlay(false);
    setVoiceSessionId(null);
    persistVoiceResumeSessionId(null);
    setVoiceProviderTrace(null);
    setVoiceConfig(DEFAULT_PATIENT_VOICE_CONFIG);
    setVoiceTurnMetrics(null);
    setVoiceEvents([]);
    setVoiceConnectionTargetUrl(null);
    setVoiceConnectionCloseCode(null);
    setVoiceConnectionCloseReason(null);
    setVoiceMicLevel(0);
    setVoiceMicPeak(0);
    setVoiceSpeechDetected(false);
    setVoiceSilenceDetected(false);
    setShowVoiceTechnicalDetails(false);
    setVoiceInactivityWarning(null);
    if (voiceAudioUrl) {
      URL.revokeObjectURL(voiceAudioUrl);
      setVoiceAudioUrl(null);
    }
    voiceEndedByUserRef.current = false;
    voiceAutoResumeRef.current = false;
    voiceLastTranscriptOrResponseAtRef.current = null;
    voiceTurnUploadStartedAtRef.current = null;
    voiceTurnSubmittedAtRef.current = null;
    voicePlaybackStartedAtRef.current = null;
    voiceAutoplayUnlockAttemptedRef.current = false;
    clearAssistantAudioFlags();
    closeVoiceSocket();
  }, [portalSession?.patientSessionToken]);

  useEffect(() => () => {
    closeVoiceSocket();
  }, []);

  useEffect(() => {
    voiceStatusRef.current = voiceStatus;
  }, [voiceStatus]);

  useEffect(() => {
    voiceConfigRef.current = voiceConfig;
  }, [voiceConfig]);

  useEffect(() => {
    if (voiceAudioElementRef.current) {
      voiceAudioElementRef.current.muted = voiceMuted;
    }
  }, [voiceMuted, voiceAudioUrl]);

  useEffect(() => {
    if (!voiceAudioUrl || !voicePendingAutoPlayRef.current) {
      return;
    }
    voicePendingAutoPlayRef.current = false;
    void handleVoiceReplyPlayback(voiceAudioUrl, true);
  }, [voiceAudioUrl]);

  function replaceVoiceAudioUrl(nextUrl: string | null) {
    setVoiceAudioUrl((current) => {
      if (current && current !== nextUrl) {
        URL.revokeObjectURL(current);
      }
      return nextUrl;
    });
  }

  async function unlockVoicePlaybackElement() {
    const audioElement = voiceAudioElementRef.current;
    if (!audioElement || voiceAutoplayUnlockAttemptedRef.current) {
      return;
    }
    voiceAutoplayUnlockAttemptedRef.current = true;
    const previousMuted = audioElement.muted;
    const previousSrc = audioElement.src;
    try {
      audioElement.muted = true;
      audioElement.src = PATIENT_VOICE_AUDIO_UNLOCK_SRC;
      audioElement.load();
      await audioElement.play();
      audioElement.pause();
      audioElement.currentTime = 0;
      appendVoiceEvent("AUDIO_UNLOCK_READY");
    } catch (error) {
      const reason = error instanceof Error ? error.message : "unknown";
      appendVoiceEvent(`AUDIO_UNLOCK_FAILED ${reason}`);
    } finally {
      audioElement.pause();
      audioElement.currentTime = 0;
      audioElement.src = previousSrc;
      if (previousSrc) {
        audioElement.load();
      } else {
        audioElement.removeAttribute("src");
        audioElement.load();
      }
      audioElement.muted = previousMuted || voiceMuted;
    }
  }

  useEffect(() => {
    if (voiceStatus === "idle" || voiceStatus === "ended" || voiceStatus === "error") {
      setVoiceInactivityWarning(null);
      return;
    }
    const warningTimer = window.setInterval(() => {
      const lastActivityAt = voiceLastTranscriptOrResponseAtRef.current ?? voiceSessionStartedAtRef.current;
      if (lastActivityAt && Date.now() - lastActivityAt >= 120000) {
        // TODO: replace this lightweight warning with an explicit idle-timeout policy later.
        setVoiceInactivityWarning("No voice activity detected. This call may end soon.");
      } else {
        setVoiceInactivityWarning(null);
      }
    }, 5000);
    return () => window.clearInterval(warningTimer);
  }, [voiceStatus]);

  function startVoiceHeartbeat(socket: WebSocket) {
    clearVoiceHeartbeat();
    voiceHeartbeatTimerRef.current = window.setInterval(() => {
      if (socket.readyState !== WebSocket.OPEN) {
        clearVoiceHeartbeat();
        return;
      }
      socket.send(JSON.stringify({ type: "heartbeat" }));
    }, voiceConfigRef.current.heartbeatIntervalMs);
  }

  function appendVoicePatientMessage(text: string) {
    setMessages((current) => [
      ...current,
      {
        id: `patient-voice-${Date.now()}`,
        role: "patient",
        text,
      },
    ]);
  }

  function appendVoiceAssistantMessage(text: string) {
    setMessages((current) => [
      ...current,
      {
        id: `assistant-voice-${Date.now()}`,
        role: "assistant",
        text,
      },
    ]);
  }

  async function handleVoiceReplyPlayback(nextAudioUrl?: string, autoTriggered = false) {
    const audioUrl = nextAudioUrl || voiceAudioUrl;
    if (!voiceAudioElementRef.current || !audioUrl) {
      return;
    }
    try {
      appendVoiceEvent(`AUDIO_PLAY_ATTEMPT ${autoTriggered ? "auto" : "manual"}`);
      if (voiceStatusRef.current === "listening" || voiceStatusRef.current === "speech_detected") {
        stopVoiceStream();
      }
      if (voiceAudioElementRef.current.src !== audioUrl) {
        voiceAudioElementRef.current.src = audioUrl;
      }
      voiceAudioElementRef.current.load();
      voiceAudioElementRef.current.currentTime = 0;
      updateVoiceStatus("playing_response");
      setVoiceReplyReadyToPlay(false);
      setVoiceError(null);
      setVoiceInfo("Playing AIVA response…");
      await voiceAudioElementRef.current.play();
      voiceAssistantAudioPendingRef.current = false;
      voiceAssistantAudioReadyRef.current = false;
      voiceAssistantAudioPlayingRef.current = true;
      voicePlaybackStartedAtRef.current = performance.now();
      const playbackStartLatencyMs = voiceTurnSubmittedAtRef.current == null
        ? 0
        : Math.round(voicePlaybackStartedAtRef.current - voiceTurnSubmittedAtRef.current);
      appendVoiceEvent(`AUDIO_PLAY_SUCCESS ${autoTriggered ? "auto" : "manual"}`);
      appendVoiceEvent(`ASSISTANT_AUDIO_PLAYING ${playbackStartLatencyMs}ms`);
    } catch (error) {
      const reason = error instanceof Error ? error.message : "Autoplay blocked";
      voiceAssistantAudioPendingRef.current = false;
      voiceAssistantAudioReadyRef.current = true;
      voiceAssistantAudioPlayingRef.current = false;
      voicePendingAutoPlayRef.current = false;
      updateVoiceStatus("session_started");
      setVoiceReplyReadyToPlay(true);
      setVoiceInfo("AIVA reply is ready. Tap play to hear it.");
      appendVoiceEvent(`AUDIO_PLAY_FAILED ${reason}`);
      appendVoiceEvent(`ASSISTANT_AUDIO_PLAY_BLOCKED ${reason}`);
    }
  }

  function bindVoiceSocket(socket: WebSocket) {
    socket.onopen = () => {
      updateVoiceStatus("connecting");
      setVoiceInfo("Connecting to AIVA voice…");
      appendVoiceEvent("CONNECTED");
      socket.send(JSON.stringify({
        type: "session.start",
        language: "auto",
        resumeSessionId: storedVoiceResumeSessionId() || voiceSessionId,
      }));
    };
    socket.onmessage = (rawMessage) => {
    let payload: Record<string, unknown>;
    try {
      payload = JSON.parse(rawMessage.data) as Record<string, unknown>;
    } catch {
      updateVoiceStatus("error");
      setVoiceError("Voice response could not be read.");
      setVoiceInfo(null);
      return;
    }

    const type = String(payload.type || "");
    if (type === "session.connected") {
      appendVoiceEvent("SESSION_CONNECTED");
      return;
    }
    if (type === "session.started") {
      const resumed = Boolean(payload.resumed);
      const resumedSessionId = String(payload.sessionId || "");
      setVoiceSessionId(resumedSessionId);
      persistVoiceResumeSessionId(resumedSessionId || null);
      const nextVoiceConfig = parsePatientVoiceConfig(payload.voiceConfig);
      voiceConfigRef.current = nextVoiceConfig;
      setVoiceConfig(nextVoiceConfig);
      startVoiceHeartbeat(socket);
      updateVoiceStatus("session_started");
      setVoiceInfo(resumed ? "Reconnected. Continuing your previous conversation." : "Listening… speak now.");
      appendVoiceEvent(resumed ? "SESSION_RESUMED" : "SESSION_STARTED");
      if (voiceAutoResumeRef.current) {
        scheduleVoiceListeningResume("session_started", 0);
      }
      return;
    }
    if (type === "stt.started" || type === "turn.started") {
      updateVoiceStatus("processing");
      setVoiceError(null);
      setVoiceInfo("Processing your request…");
      appendVoiceEvent(type === "stt.started" ? "STT_STARTED" : "TURN_STARTED");
      return;
    }
    if (type === "heartbeat") {
      return;
    }
    if (type === "audio.chunk.received") {
      const sequence = Number(payload.sequence || 0);
      const totalChunks = Number(payload.totalChunks || 0);
      appendVoiceEvent(`AUDIO_CHUNK_RECEIVED ${sequence}/${totalChunks || "?"}`);
      return;
    }
    if (type === "turn.audio.received") {
      const sequence = Number(payload.sequence || 0);
      const totalChunks = Number(payload.totalChunks || 0);
      appendVoiceEvent(`TURN_AUDIO_RECEIVED ${sequence}/${totalChunks || "?"}`);
      return;
    }
    if (type === "transcript.final") {
      const transcript = String(payload.text || "").trim();
      voiceLastTranscriptOrResponseAtRef.current = Date.now();
      setVoiceInactivityWarning(null);
      setVoiceTranscript(transcript);
      if (transcript) {
        appendVoicePatientMessage(transcript);
      }
      setVoiceInfo("Transcript captured.");
      appendVoiceEvent("TRANSCRIPT_FINAL");
      return;
    }
    if (type === "stt.complete" || type === "turn.stt.complete") {
      const durationMs = Number(payload.durationMs || 0);
      const provider = String(payload.provider || "");
      appendVoiceEvent(`STT_COMPLETE ${durationMs}ms${provider ? ` ${provider}` : ""}`);
      return;
    }
    if (type === "assistant.text") {
      const assistantText = String(payload.text || "").trim();
      const nextState = payload.state as PatientPortalCareAiStateResponse | null;
      const trace = (payload.providerTrace as PatientCareAiVoiceProviderTrace | null) ?? null;
      voiceLastTranscriptOrResponseAtRef.current = Date.now();
      setVoiceInactivityWarning(null);
      setVoiceAssistant(assistantText);
      if (assistantText) {
        appendVoiceAssistantMessage(assistantText);
      }
      if (nextState) {
        setState(nextState);
      }
      setVoiceProviderTrace(trace);
      setVoiceInfo("AIVA responded.");
      appendVoiceEvent("ASSISTANT_TEXT");
      return;
    }
    if (type === "turn.careai.complete") {
      const durationMs = Number(payload.durationMs || 0);
      const provider = String(payload.provider || "");
      appendVoiceEvent(`CAREAI_COMPLETE ${durationMs}ms${provider ? ` ${provider}` : ""}`);
      return;
    }
    if (type === "assistant.audio.chunk") {
      const sequence = Number(payload.sequence || 0);
      const totalChunks = Number(payload.totalChunks || 0);
      const chunk = String(payload.audioBase64Chunk || "");
      if (sequence > 0 && chunk) {
        voicePendingAudioRef.current.set(sequence, chunk);
        voiceExpectedAudioChunksRef.current = totalChunks;
        appendVoiceEvent(`ASSISTANT_AUDIO_CHUNK ${sequence}/${totalChunks || "?"}`);
      }
      return;
    }
    if (type === "assistant.audio.end") {
      const contentType = String(payload.contentType || "audio/wav");
      const totalChunks = voiceExpectedAudioChunksRef.current;
      const chunks: string[] = [];
      for (let index = 1; index <= totalChunks; index += 1) {
        const chunk = voicePendingAudioRef.current.get(index);
        if (!chunk) {
          updateVoiceStatus("error");
          setVoiceError("Voice playback data was incomplete.");
          resetPendingVoiceAudio();
          return;
        }
        chunks.push(chunk);
      }
      resetPendingVoiceAudio();
      const binary = atob(chunks.join(""));
      const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
      const url = URL.createObjectURL(new Blob([bytes], { type: contentType }));
      voiceAssistantAudioPendingRef.current = true;
      voiceAssistantAudioReadyRef.current = false;
      voiceAssistantAudioPlayingRef.current = false;
      voicePendingAutoPlayRef.current = true;
      updateVoiceStatus("playing_response");
      setVoiceReplyReadyToPlay(false);
      replaceVoiceAudioUrl(url);
      setVoiceInfo("Playing AIVA response…");
      appendVoiceEvent(`ASSISTANT_AUDIO_READY ${Number(payload.durationMs || 0)}ms`);
      return;
    }
    if (type === "turn.tts.complete") {
      const durationMs = Number(payload.durationMs || 0);
      const provider = String(payload.provider || "");
      appendVoiceEvent(`TTS_COMPLETE ${durationMs}ms${provider ? ` ${provider}` : ""}`);
      return;
    }
    if (type === "turn.complete") {
      const metrics = parsePatientVoiceTurnMetrics(payload.metrics);
      setVoiceTurnMetrics(metrics);
      if (metrics) {
        appendVoiceEvent(
          `TURN_COMPLETE total=${metrics.totalDurationMs}ms stt=${metrics.sttDurationMs}ms careai=${metrics.careAiDurationMs}ms tts=${metrics.ttsDurationMs}ms`,
        );
      } else {
        appendVoiceEvent("TURN_COMPLETE");
      }
      if (
        voiceAssistantAudioPendingRef.current
        || voiceAssistantAudioReadyRef.current
        || voiceAssistantAudioPlayingRef.current
      ) {
        appendVoiceEvent("AUTO_RESUME_SKIPPED assistant_audio_pending");
      } else if (!voiceExpectedAudioChunksRef.current) {
        updateVoiceStatus("session_started");
        if (voiceAutoResumeRef.current) {
          scheduleVoiceListeningResume("turn_complete_no_audio");
        }
      }
      setVoiceInfo("Turn completed.");
      return;
    }
    if (type === "session.closed") {
      updateVoiceStatus("ended");
      setVoiceInfo("Voice session closed.");
      appendVoiceEvent("SESSION_CLOSED");
      return;
    }
    if (type === "session.timeout") {
      updateVoiceStatus("error");
      setVoiceError("Voice connection timed out. Please retry.");
      setVoiceInfo(null);
      appendVoiceEvent(`SESSION_TIMEOUT ${String(payload.reason || "timeout")}`);
      return;
    }
    if (type === "error") {
      updateVoiceStatus("error");
      setVoiceError(String(payload.message || "AIVA voice could not process that request."));
      setVoiceInfo(null);
      appendVoiceEvent("SERVER_ERROR");
    }
    };
    socket.onerror = () => {
      updateVoiceStatus("error");
      setVoiceError("Voice connection failed. Open technical details for the websocket target and close status.");
      setVoiceInfo(null);
      appendVoiceEvent("CONNECT_FAILED");
    };
    socket.onclose = (event) => {
      const closedByUser = voiceEndedByUserRef.current;
      setVoiceConnectionCloseCode(event.code);
      setVoiceConnectionCloseReason(event.reason || null);
      cleanupVoiceSessionResources();
      if (voiceSocketRef.current === socket) {
        voiceSocketRef.current = null;
      }
      if (closedByUser) {
        updateVoiceStatus("idle");
        setVoiceInfo(null);
        appendVoiceEvent("DISCONNECTED");
        return;
      }
      if (voiceStatusRef.current !== "error") {
        updateVoiceStatus("idle");
      }
      if (event.code !== 1000) {
        setVoiceError("Voice connection failed. Open technical details for the websocket target and close status.");
      }
      setVoiceInfo(null);
      appendVoiceEvent(`DISCONNECTED ${event.code}`);
    };
  }

  async function ensureVoiceSocket() {
    if (!portalSession) {
      throw new Error("Patient session is required for AIVA voice.");
    }
    if (voiceSocketRef.current && voiceSocketRef.current.readyState === WebSocket.OPEN) {
      return voiceSocketRef.current;
    }
    if (voiceSocketRef.current) {
      closeVoiceSocket();
    }
    updateVoiceStatus("connecting");
    setVoiceError(null);
    setVoiceInfo("Connecting to AIVA voice…");
    const socketUrl = buildPatientPortalVoiceWebSocketUrl(portalSession);
    setVoiceConnectionTargetUrl(socketUrl);
    setVoiceConnectionCloseCode(null);
    setVoiceConnectionCloseReason(null);
    appendVoiceEvent("CONNECTING");
    const socket = new WebSocket(socketUrl);
    voiceSocketRef.current = socket;
    bindVoiceSocket(socket);

    await new Promise<WebSocket>((resolve, reject) => {
      let settled = false;
      const previousOnOpen = socket.onopen;
      const previousOnError = socket.onerror;
      const previousOnClose = socket.onclose;
      socket.onopen = (event) => {
        previousOnOpen?.call(socket, event);
        if (!settled) {
          settled = true;
          startVoiceHeartbeat(socket);
          resolve(socket);
        }
      };
      socket.onerror = (event) => {
        previousOnError?.call(socket, event);
        if (!settled) {
          settled = true;
          reject(new Error("Voice connection failed. See technical details for the websocket target."));
        }
      };
      socket.onclose = (event) => {
        previousOnClose?.call(socket, event);
        if (!settled) {
          settled = true;
          reject(new Error("Voice connection failed. See technical details for the websocket target."));
        }
      };
    });

    return socket;
  }

  async function startVoiceMic(options?: { automatic?: boolean; reason?: string }) {
    if (
      !portalSession ||
      voiceStartingMicRef.current ||
      voiceStatusRef.current === "listening" ||
      voiceStatusRef.current === "speech_detected" ||
      voiceStatusRef.current === "processing" ||
      voiceStatusRef.current === "finalizing_audio" ||
      voiceStatusRef.current === "playing_response" ||
      voiceStatusRef.current === "ending" ||
      voiceAssistantAudioPendingRef.current ||
      voiceAssistantAudioReadyRef.current ||
      voiceAssistantAudioPlayingRef.current
    ) {
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      updateVoiceStatus("error");
      setVoiceError("Microphone access is not available in this browser. You can keep using chat.");
      return;
    }
    setVoiceError(null);
    setVoiceTranscript("");
    setVoiceAssistant("");
    setVoiceTurnMetrics(null);
    voiceDiscardRecordingRef.current = false;
    try {
      voiceStartingMicRef.current = true;
      clearVoiceResumeTimer();
      const socket = await ensureVoiceSocket();
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = selectPatientVoiceMimeType();
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      const recordingType = recorder.mimeType || mimeType || "audio/webm";
      const filename = `patient-careai-${Date.now()}.${resolvePatientVoiceAudioExtension(recordingType)}`;
      voiceChunksRef.current = [];
      voiceSpeechDetectedRef.current = false;
      voiceSpeechFrameActiveRef.current = false;
      voiceSpeechStartedAtRef.current = null;
      voiceLastSpeechAtRef.current = null;
      voiceAutoStopTriggeredRef.current = false;
      voiceSessionStartedAtRef.current = Date.now();
      startVoiceAudioMonitoring(stream);
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          voiceChunksRef.current.push(event.data);
        }
      };
      recorder.onstop = async () => {
        stopVoiceStream();
        if (voiceDiscardRecordingRef.current || socket.readyState !== WebSocket.OPEN) {
          voiceDiscardRecordingRef.current = false;
          return;
        }
        const contentType = recorder.mimeType || recordingType || "audio/webm";
        const blob = new Blob(voiceChunksRef.current, { type: contentType });
        if (blob.size === 0) {
          updateVoiceStatus("session_started");
          setVoiceInfo("No recorded audio was captured. Please speak and try again.");
          appendVoiceEvent("NO_SPEECH_DETECTED");
          if (voiceAutoResumeRef.current) {
            scheduleVoiceListeningResume("empty_turn");
          }
          return;
        }
        if (!voiceSpeechDetectedRef.current) {
          updateVoiceStatus("session_started");
          setVoiceInfo("No speech detected. Please speak more clearly and try again.");
          appendVoiceEvent("NO_SPEECH_DETECTED");
          if (voiceAutoResumeRef.current) {
            scheduleVoiceListeningResume("silent_turn");
          }
          return;
        }
        updateVoiceStatus("finalizing_audio");
        setVoiceInfo("Finalizing microphone audio…");
        voiceTurnUploadStartedAtRef.current = performance.now();
        const audioBase64 = await blobToBase64(blob);
        const chunks = splitVoiceBase64Chunks(audioBase64);
        updateVoiceStatus("processing");
        setVoiceInfo("Uploading audio to AIVA…");
        chunks.forEach((audioBase64Chunk, index) => {
          socket.send(JSON.stringify({
            type: "audio.chunk",
            sequence: index + 1,
            totalChunks: chunks.length,
            contentType,
            filename,
            audioBase64Chunk,
          }));
        });
        socket.send(JSON.stringify({
          type: "audio.end",
          totalChunks: chunks.length,
          contentType,
          filename,
        }));
        voiceTurnSubmittedAtRef.current = performance.now();
        const uploadPrepMs = voiceTurnUploadStartedAtRef.current == null
          ? 0
          : Math.round(voiceTurnSubmittedAtRef.current - voiceTurnUploadStartedAtRef.current);
        appendVoiceEvent(`TURN_UPLOAD_SENT ${chunks.length} chunks ${uploadPrepMs}ms`);
        appendVoiceEvent(`TURN_SENT ${chunks.length} chunks`);
      };
      voiceStreamRef.current = stream;
      voiceRecorderRef.current = recorder;
      recorder.start();
      updateVoiceStatus("listening");
      setVoiceInfo("Listening… speak now.");
      appendVoiceEvent(`RECORDER_STARTED ${options?.reason || (options?.automatic ? "automatic" : "manual")}`);
    } catch (voiceStartError: unknown) {
      stopVoiceStream();
      updateVoiceStatus("error");
      setVoiceError("Voice connection failed. Open technical details for the websocket target and close status.");
      setVoiceInfo(null);
    } finally {
      voiceStartingMicRef.current = false;
    }
  }

  async function handleVoiceStart() {
    if (!portalSession) {
      return;
    }
    await unlockVoicePlaybackElement();
    voiceEndedByUserRef.current = false;
    voiceAutoResumeRef.current = true;
    voiceLastTranscriptOrResponseAtRef.current = Date.now();
    setVoiceInactivityWarning(null);
    setVoiceReplyReadyToPlay(false);
    replaceVoiceAudioUrl(null);
    voicePendingAutoPlayRef.current = false;
    clearAssistantAudioFlags();
    setVoiceProviderTrace(null);
    setVoiceEvents([]);
    setVoiceError(null);
    setVoiceInfo("Connecting to AIVA voice…");
    setVoiceTranscript("");
    setVoiceAssistant("");
    setVoiceSessionId(null);
    try {
      const socket = await ensureVoiceSocket();
      if (socket.readyState === WebSocket.OPEN) {
        appendVoiceEvent("SOCKET_OPEN");
      }
    } catch {
      updateVoiceStatus("error");
      setVoiceError("Voice connection failed. Open technical details for the websocket target and close status.");
      setVoiceInfo(null);
    }
  }

  async function handleVoiceStartTurn() {
    if (!portalSession) {
      return;
    }
    voiceEndedByUserRef.current = false;
    try {
      await startVoiceMic();
    } catch {
      updateVoiceStatus("error");
      setVoiceError("Voice connection failed. Open technical details for the websocket target and close status.");
      setVoiceInfo(null);
    }
  }

  function handleVoiceStopTurn() {
    if (voiceRecorderRef.current && voiceRecorderRef.current.state !== "inactive") {
      appendVoiceEvent("RECORDER_STOPPING");
      updateVoiceStatus("finalizing_audio");
      setVoiceInfo("Finalizing this turn…");
      voiceRecorderRef.current.stop();
      return;
    }
    stopVoiceStream();
    updateVoiceStatus("session_started");
    setVoiceInfo("Session is ready. Start a turn when you want to speak.");
  }

  function handleVoiceEndSession() {
    voiceEndedByUserRef.current = true;
    voiceAutoResumeRef.current = false;
    voiceStartMicPendingRef.current = false;
    voiceDiscardRecordingRef.current = true;
    if (voiceRecorderRef.current && voiceRecorderRef.current.state !== "inactive") {
      voiceRecorderRef.current.stop();
    }
    if (voiceAudioElementRef.current) {
      voiceAudioElementRef.current.pause();
      voiceAudioElementRef.current.currentTime = 0;
    }
    voicePendingAutoPlayRef.current = false;
    clearAssistantAudioFlags();
    closeVoiceSocket();
    updateVoiceStatus("idle");
    setVoiceError(null);
    setVoiceInfo(null);
    persistVoiceResumeSessionId(null);
    setVoiceInactivityWarning(null);
    setVoiceReplyReadyToPlay(false);
    appendVoiceEvent("SESSION_ENDED_BY_USER");
  }

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
      setError(submitError instanceof Error ? submitError.message : "AIVA could not process that request.");
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
      if (voiceAudioUrl) {
        URL.revokeObjectURL(voiceAudioUrl);
        setVoiceAudioUrl(null);
      }
      setVoiceTranscript("");
      setVoiceAssistant("");
      setVoiceReplyReadyToPlay(false);
      setVoiceError(null);
      resetPendingVoiceAudio();
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
      setError(resetError instanceof Error ? resetError.message : "AIVA could not reset the booking context.");
    } finally {
      setResetting(false);
    }
  }

  return (
    <PatientAccessBoundary
      session={session}
      onSignOut={onSignOut}
      title={`${branding.productName} Patient Portal`}
      subtitle="Chat-based booking uses your verified patient session, asks for explicit confirmation, and stays scoped to your tenant."
    >
      <div className="patient-content-grid">
        <article className="patient-panel patient-panel-wide">
          <div className="patient-panel-heading">
            <h2>AIVA booking chat</h2>
            <button className="ghost-button" type="button" disabled={!portalSession || resetting} onClick={handleReset}>
              {resetting ? "Resetting..." : "Reset"}
            </button>
          </div>
          <div className="patient-careai-voice-panel">
            <div className="patient-careai-voice-actions">
              <button
                className="primary-button"
                type="button"
                disabled={!portalSession || submitting || (voiceStatus !== "idle" && voiceStatus !== "ended" && voiceStatus !== "error")}
                onClick={handleVoiceStart}
              >
                Talk to AIVA
              </button>
              <button
                className="secondary-button"
                type="button"
                disabled={voiceStatus === "idle" || voiceStatus === "ended"}
                onClick={handleVoiceEndSession}
              >
                End Call
              </button>
              <button
                className="ghost-button"
                type="button"
                disabled={voiceStatus === "idle" || voiceStatus === "ended"}
                onClick={() => setVoiceMuted((current) => !current)}
              >
                {voiceMuted ? "Unmute" : "Mute"}
              </button>
              <span className={`patient-voice-status patient-voice-status-${voiceStatus}`}>
                {voiceMuted && voiceStatus !== "idle" && voiceStatus !== "ended" && voiceStatus !== "error"
                  ? "Muted"
                  : patientVoiceStatusLabel(voiceStatus)}
              </span>
            </div>
            <div className="patient-careai-voice-toolbar">
              <span className="patient-inline-note">
                {voiceInfo || "Start a voice session and AIVA will listen automatically after it connects."}
              </span>
              <button
                className="ghost-button patient-technical-toggle"
                type="button"
                onClick={() => setShowVoiceTechnicalDetails((current) => !current)}
              >
                {showVoiceTechnicalDetails ? "Hide technical details" : "Show technical details"}
              </button>
            </div>
            {voiceInactivityWarning ? <div className="patient-inline-empty patient-careai-voice-warning">{voiceInactivityWarning}</div> : null}
            <audio
              ref={voiceAudioElementRef}
              className="patient-careai-audio"
              muted={voiceMuted}
              onEnded={() => {
                clearAssistantAudioFlags();
                voicePendingAutoPlayRef.current = false;
                const playbackDurationMs = voicePlaybackStartedAtRef.current == null ? 0 : Math.round(performance.now() - voicePlaybackStartedAtRef.current);
                appendVoiceEvent(`ASSISTANT_AUDIO_ENDED ${playbackDurationMs}ms`);
                voicePlaybackStartedAtRef.current = null;
                updateVoiceStatus("session_started");
                setVoiceInfo("Listening again…");
                setVoiceReplyReadyToPlay(false);
                if (voiceAutoResumeRef.current) {
                  scheduleVoiceListeningResume("playback_ended");
                }
              }}
            />
            {voiceReplyReadyToPlay ? (
              <button className="ghost-button patient-careai-reply-play" type="button" onClick={() => void handleVoiceReplyPlayback()}>
                Play AIVA reply
              </button>
            ) : null}
            {voiceError ? <div className="patient-inline-empty patient-inline-error">{voiceError}</div> : null}
            {showVoiceTechnicalDetails ? (
              <div className="patient-careai-technical-panel">
                <div className="patient-careai-voice-actions patient-careai-voice-debug-actions">
                  <button
                    className="secondary-button"
                    type="button"
                    disabled={!portalSession || voiceStatus !== "session_started"}
                    onClick={() => void handleVoiceStartTurn()}
                  >
                    Start Turn
                  </button>
                  <button
                    className="secondary-button"
                    type="button"
                    disabled={voiceStatus !== "listening" && voiceStatus !== "speech_detected"}
                    onClick={handleVoiceStopTurn}
                  >
                    Stop Turn / Send
                  </button>
                </div>
                <div className="patient-careai-voice-meta">
                  <div className="patient-subcard">
                    <strong>Transcript</strong>
                    <span>{voiceTranscript || "No transcript yet."}</span>
                  </div>
                  <div className="patient-subcard">
                    <strong>Voice reply</strong>
                    <span>{voiceAssistant || "No assistant reply yet."}</span>
                  </div>
                  <div className="patient-subcard">
                    <strong>Session</strong>
                    <span>{voiceSessionId || "Starts after websocket handshake."}</span>
                  </div>
                  <div className="patient-subcard">
                    <strong>WebSocket target</strong>
                    <span>{voiceConnectionTargetUrl || "Not connected yet."}</span>
                  </div>
                  <div className="patient-subcard">
                    <strong>Last close</strong>
                    <span>
                      {voiceConnectionCloseCode == null
                        ? "No close event yet."
                        : `${voiceConnectionCloseCode}${voiceConnectionCloseReason ? ` · ${voiceConnectionCloseReason}` : ""}`}
                    </span>
                  </div>
                  <div className="patient-subcard">
                    <strong>Providers</strong>
                    <span>
                      STT {formatPatientVoiceProvider(voiceProviderTrace?.sttProvider)} · LLM {formatPatientVoiceProvider(voiceProviderTrace?.llmProvider)} · TTS {formatPatientVoiceProvider(voiceProviderTrace?.ttsProvider)}
                    </span>
                  </div>
                  <div className="patient-subcard">
                    <strong>Voice activity</strong>
                    <span>
                      RMS {voiceMicLevel.toFixed(3)} · Peak {voiceMicPeak.toFixed(3)} · {voiceSpeechDetected ? "Speech detected" : voiceSilenceDetected ? "Silence detected" : "Waiting"}
                    </span>
                  </div>
                  <div className="patient-subcard">
                    <strong>Turn timings</strong>
                    <span>
                      {voiceTurnMetrics
                        ? `STT ${voiceTurnMetrics.sttDurationMs}ms · AIVA ${voiceTurnMetrics.careAiDurationMs}ms · TTS ${voiceTurnMetrics.ttsDurationMs}ms · Total ${voiceTurnMetrics.totalDurationMs}ms`
                        : `VAD start ${voiceConfig.speechStartThreshold.toFixed(3)} · silence ${voiceConfig.silenceTimeoutMs}ms · auto resume ${voiceConfig.autoResumeDelayMs}ms`}
                    </span>
                  </div>
                </div>
                {voiceAudioUrl ? (
                  <audio className="patient-careai-audio-technical" controls src={voiceAudioUrl} muted={voiceMuted} />
                ) : null}
                {voiceEvents.length > 0 ? (
                  <div className="patient-careai-voice-events">
                    <strong>Live events</strong>
                    <div className="patient-careai-voice-event-list">
                      {voiceEvents.map((item, index) => (
                        <span key={`${item}-${index}`}>{item}</span>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>
          <div className="patient-careai-chat">
            <div className="patient-chat-stream" aria-live="polite">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`patient-chat-bubble ${message.role === "patient" ? "patient-chat-bubble-self" : "patient-chat-bubble-ai"}`}
                >
                  <strong>{message.role === "patient" ? "You" : "AIVA"}</strong>
                  <p>{message.text}</p>
                </div>
              ))}
              {submitting ? (
                <div className="patient-chat-bubble patient-chat-bubble-ai">
                  <strong>AIVA</strong>
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
                  {submitting ? "Sending..." : "Send to AIVA"}
                </button>
                <span className="patient-inline-note">AIVA never books, reschedules, or cancels until you explicitly confirm.</span>
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
                  <p>AIVA could not safely finish this request. The clinic team should help complete the booking.</p>
                </div>
              ) : null}
            </div>
          ) : (
            <div className="patient-empty-card">
              <strong>AIVA is ready</strong>
              <p>Ask to book, reschedule, cancel, or check an appointment. You can reply in English or Hindi.</p>
            </div>
          )}
        </article>

        <article className="patient-panel">
          <div className="patient-panel-heading">
            <h2>What AIVA can do now</h2>
          </div>
          <div className="patient-subcard-list">
            <div className="patient-subcard">
              <strong>Book and confirm appointments</strong>
              <span>AIVA can collect doctor, date, slot, and explicit confirmation before creating the appointment.</span>
            </div>
            <div className="patient-subcard">
              <strong>Reschedule and cancel safely</strong>
              <span>AIVA can list your upcoming appointments, ask you to choose one, and only change it after confirmation.</span>
            </div>
            <div className="patient-subcard">
              <strong>Confirmed patient session only</strong>
              <span>AIVA uses your verified portal session and never accepts another patient identity from chat.</span>
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
  const profileValidation = useMemo(() => {
    if (!formState) {
      return { success: false, errors: {} as Record<string, string> };
    }
    const parsed = patientProfileSchema.safeParse(formState);
    const fieldErrors = parsed.success ? {} : mapZodErrors(parsed.error);
    if (!formState.firstName.trim()) {
      fieldErrors.firstName = "First name is required.";
    }
    return {
      success: parsed.success && !fieldErrors.firstName,
      errors: fieldErrors,
    };
  }, [formState]);
  const profileFieldErrors = profileValidation.errors;

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
    if (!profileValidation.success) {
      setSaveError(Object.values(profileFieldErrors)[0] || "Unable to save your profile.");
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
      subtitle="Review your details and update the information you want the clinic to keep on file."
    >
      <PatientPortalApiState
        loading={profile.loading}
        error={profile.error}
        empty={!profileData}
        emptyTitle="Your profile is not available yet"
        emptyMessage="Your profile details will appear here once the clinic has linked them."
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
              <h2>Edit profile details</h2>
              <span className="panel-caption">Your mobile number stays locked after verification</span>
            </div>
            <form className="patient-login-form patient-registration-form" onSubmit={handleProfileSave}>
              <div className="patient-form-grid">
                <label>
                  <span>First name</span>
                  <input
                    value={formState.firstName}
                    onChange={(event) => setFormState((current) => current ? { ...current, firstName: event.target.value } : current)}
                    required
                    aria-invalid={Boolean(profileFieldErrors.firstName)}
                  />
                  {profileFieldErrors.firstName ? <p className="patient-field-error">{profileFieldErrors.firstName}</p> : null}
                </label>
                <label>
                  <span>Last name</span>
                  <input value={formState.lastName} onChange={(event) => setFormState((current) => current ? { ...current, lastName: event.target.value } : current)} />
                  {profileFieldErrors.lastName ? <p className="patient-field-error">{profileFieldErrors.lastName}</p> : null}
                </label>
                <label>
                  <span>Gender</span>
                  <select value={formState.gender} onChange={(event) => setFormState((current) => current ? { ...current, gender: event.target.value } : current)} aria-invalid={Boolean(profileFieldErrors.gender)}>
                    <option value="UNKNOWN">Prefer not to say</option>
                    <option value="MALE">Male</option>
                    <option value="FEMALE">Female</option>
                    <option value="OTHER">Other</option>
                  </select>
                  {profileFieldErrors.gender ? <p className="patient-field-error">{profileFieldErrors.gender}</p> : null}
                </label>
                <label>
                  <span>Date of birth</span>
                  <input
                    type="date"
                    max={new Date().toISOString().slice(0, 10)}
                    value={formState.dateOfBirth ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, dateOfBirth: event.target.value || null } : current)}
                    aria-invalid={Boolean(profileFieldErrors.dateOfBirth)}
                  />
                  {profileFieldErrors.dateOfBirth ? <p className="patient-field-error">{profileFieldErrors.dateOfBirth}</p> : null}
                </label>
                <label>
                  <span>Age</span>
                  <input
                    value={formState.ageYears ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, ageYears: event.target.value ? Number(event.target.value) : null } : current)}
                  />
                  {profileFieldErrors.ageYears ? <p className="patient-field-error">{profileFieldErrors.ageYears}</p> : null}
                </label>
                <label>
                  <span>Mobile</span>
                  <input value={profileData?.mobile ?? ""} readOnly />
                </label>
                <label>
                  <span>Email</span>
                  <input
                    value={formState.email ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, email: event.target.value || null } : current)}
                    aria-invalid={Boolean(profileFieldErrors.email)}
                  />
                  {profileFieldErrors.email ? <p className="patient-field-error">{profileFieldErrors.email}</p> : null}
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
                  <input
                    value={formState.city ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, city: event.target.value || null } : current)}
                    aria-invalid={Boolean(profileFieldErrors.city)}
                  />
                  {profileFieldErrors.city ? <p className="patient-field-error">{profileFieldErrors.city}</p> : null}
                </label>
                <label>
                  <span>State</span>
                  <input
                    value={formState.state ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, state: event.target.value || null } : current)}
                    aria-invalid={Boolean(profileFieldErrors.state)}
                  />
                  {profileFieldErrors.state ? <p className="patient-field-error">{profileFieldErrors.state}</p> : null}
                </label>
                <label>
                  <span>Country</span>
                  <input
                    value={formState.country ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, country: event.target.value || null } : current)}
                    aria-invalid={Boolean(profileFieldErrors.country)}
                  />
                  {profileFieldErrors.country ? <p className="patient-field-error">{profileFieldErrors.country}</p> : null}
                </label>
                <label>
                  <span>Postal code</span>
                  <input
                    value={formState.postalCode ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, postalCode: event.target.value || null } : current)}
                    aria-invalid={Boolean(profileFieldErrors.postalCode)}
                  />
                  {profileFieldErrors.postalCode ? <p className="patient-field-error">{profileFieldErrors.postalCode}</p> : null}
                </label>
                <label>
                  <span>Emergency contact</span>
                  <input
                    value={formState.emergencyContactName ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, emergencyContactName: event.target.value || null } : current)}
                    aria-invalid={Boolean(profileFieldErrors.emergencyContactName)}
                  />
                  {profileFieldErrors.emergencyContactName ? <p className="patient-field-error">{profileFieldErrors.emergencyContactName}</p> : null}
                </label>
                <label>
                  <span>Emergency mobile</span>
                  <input
                    value={formState.emergencyContactMobile ?? ""}
                    onChange={(event) => setFormState((current) => current ? { ...current, emergencyContactMobile: event.target.value || null } : current)}
                    aria-invalid={Boolean(profileFieldErrors.emergencyContactMobile)}
                  />
                  {profileFieldErrors.emergencyContactMobile ? <p className="patient-field-error">{profileFieldErrors.emergencyContactMobile}</p> : null}
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
                <button className="primary-button" type="submit" disabled={saving || !profileValidation.success}>
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
