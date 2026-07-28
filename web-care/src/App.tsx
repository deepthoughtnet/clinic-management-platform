import { type ReactNode, useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation } from "react-router-dom";
import {
  PatientAppointmentsPage,
  PatientBillsPage,
  PatientBookAppointmentPage,
  PatientCareAiPage,
  PatientDashboardPage,
  PatientLoginPage,
  PatientNotificationsPage,
  PatientProfilePage,
  PatientRegistrationPage,
  PatientPrescriptionsPage,
} from "./pages/patient/PatientPortalPages";
import PatientLabPage from "./pages/patient/PatientLabPage";
import {
  type PatientPortalSession,
  isPatientPortalPatientSession,
  isPatientPortalRegistrationSession,
  patientPortalHomePath,
} from "./api/patientPortal";
import { branding, footerBrandingLine, productAndTagline, productTitle } from "./branding";
import { GlobalPatientHeader } from "./components/GlobalPatientHeader";
import { careConfig, externalAppUrl } from "./config";
import { PublicLocationProvider } from "./context/publicLocation";
import {
  PATIENT_PORTAL_SESSION_STORAGE_KEY,
  clearPatientAuthSession,
  clearPatientRegistrationSession,
  isPatientRegistrationSessionActive,
  isStoredPatientSessionActive,
} from "./pages/patient/patientPortalSessionState";

const clinicLoginUrl = careConfig.healthcareAppUrl;
const aivaAppUrl = careConfig.aivaAppUrl || new URL("/patient/careai", window.location.origin).toString();

function pageTitleForPath(pathname: string) {
  if (pathname === "/") return productTitle();
  if (pathname === "/patient/login") return `Login | ${branding.productName}`;
  if (pathname === "/patient/register") return `Register | ${branding.productName}`;
  if (pathname === "/patient/dashboard") return `Dashboard | ${branding.productName}`;
  if (pathname === "/patient/appointments") return `Appointments | ${branding.productName}`;
  if (pathname === "/patient/prescriptions") return `Prescriptions | ${branding.productName}`;
  if (pathname === "/patient/lab") return `Reports | ${branding.productName}`;
  if (pathname === "/patient/bills") return `Bills | ${branding.productName}`;
  if (pathname === "/patient/notifications") return `Notifications | ${branding.productName}`;
  if (pathname === "/patient/careai") return `AIVA | ${branding.productName}`;
  if (pathname === "/patient/profile") return `Profile | ${branding.productName}`;
  if (pathname.startsWith("/patient")) return `${branding.productName}`;
  if (pathname === "/careai") return `AIVA | ${branding.productName}`;
  if (pathname.startsWith("/aiva")) return `AIVA | ${branding.productName}`;
  if (pathname === "/contact") return `Contact | ${branding.productName}`;
  if (pathname === "/help-centre") return `Help Centre | ${branding.productName}`;
  if (pathname === "/privacy-policy") return `Privacy Policy | ${branding.productName}`;
  if (pathname === "/terms") return `Terms | ${branding.productName}`;
  return productTitle();
}

function descriptionForPath(pathname: string) {
  if (pathname === "/") return `${branding.productName}: ${branding.tagline}`;
  if (pathname === "/careai") return `${branding.productName} routes patients to care-specific AIVA access.`;
  if (pathname.startsWith("/patient")) return `${branding.productName} for verified appointments, prescriptions, bills, lab reports, notifications, profile, and AIVA.`;
  if (pathname.startsWith("/aiva")) return `${branding.productName} AI voice and assistant platform powered by ${branding.aiPlatformName}.`;
  if (pathname === "/contact") return `${branding.productName} contact page.`;
  if (pathname === "/help-centre") return `${branding.productName} help centre.`;
  if (pathname === "/privacy-policy") return `${branding.productName} privacy policy.`;
  if (pathname === "/terms") return `${branding.productName} terms and conditions.`;
  return `${branding.productName} by ${branding.companyName}.`;
}

const safeRedirectParams = new Set(["q", "city", "area", "speciality", "clinic", "clinicSlug", "page", "size"]);

function buildDiscoverRedirect(pathname: string, search: string) {
  const safeSearch = new URLSearchParams();
  const incoming = new URLSearchParams(search);
  incoming.forEach((value, key) => {
    if (safeRedirectParams.has(key) && value.trim()) {
      safeSearch.set(key, value.trim());
    }
  });
  return externalAppUrl(careConfig.discoverAppUrl, pathname, safeSearch.toString() ? `?${safeSearch.toString()}` : "");
}

function ExternalRedirectPage({ to }: { to: string }) {
  useEffect(() => {
    window.location.replace(to);
  }, [to]);
  return (
    <section className="page-section narrow-page">
      <div className="login-placeholder">
        <span className="eyebrow">Jeevanam Discover</span>
        <h1>Opening public discovery.</h1>
        <p>This public discovery page now lives in Jeevanam Discover.</p>
        <a className="primary-button" href={to}>
          Open Jeevanam Discover
        </a>
      </div>
    </section>
  );
}

function LegacyDiscoverRedirectPage() {
  const location = useLocation();
  return <ExternalRedirectPage to={buildDiscoverRedirect(location.pathname, location.search)} />;
}

function CareHomePage({ session }: { session: PatientPortalSession | null }) {
  if (isPatientPortalPatientSession(session)) {
    return <Navigate to="/patient/dashboard" replace />;
  }
  if (isPatientPortalRegistrationSession(session)) {
    return <Navigate to="/patient/register" replace />;
  }
  return (
    <section className="page-section care-home-page">
      <div className="care-home-hero">
        <div>
          <span className="eyebrow">Jeevanam Care</span>
          <h1>Your appointments, prescriptions, reports, and care journey in one place.</h1>
          <p>
            Sign in with phone OTP to access your private care workspace, continue bookings, view clinic-shared records,
            and use AIVA for your care.
          </p>
          <div className="cta-row">
            <Link className="primary-button" to="/patient/login">Patient Login</Link>
            <Link className="secondary-button" to="/patient/register">Complete Registration</Link>
            <a className="ghost-button" href={careConfig.discoverAppUrl}>Find a doctor or clinic</a>
          </div>
        </div>
        <article className="care-home-card" aria-label="Jeevanam Care capabilities">
          <strong>Care workspace</strong>
          <ul className="plain-list">
            <li>Book and manage appointments</li>
            <li>View prescriptions and lab reports shared by clinics</li>
            <li>Review bills, receipts, notifications, and reminders</li>
            <li>Use AIVA with your patient context after login</li>
          </ul>
        </article>
      </div>
    </section>
  );
}

function StaticSupportPage({
  title,
  eyebrow,
  subtitle,
  body,
}: {
  title: string;
  eyebrow: string;
  subtitle: string;
  body: string;
}) {
  return (
    <section className="page-section narrow-page">
      <div className="section-heading">
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      <div className="login-placeholder">
        <p>{body}</p>
        <div className="cta-row">
          <Link className="primary-button" to="/">
            Back to home
          </Link>
        </div>
      </div>
    </section>
  );
}

function readStoredPatientSession() {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(PATIENT_PORTAL_SESSION_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as PatientPortalSession;
    if (
      parsed?.mode === "otp"
      && parsed.patientSessionToken
      && parsed.tenantId
      && parsed.tenantCode
      && (parsed.sessionRole === "patient" || parsed.sessionRole === "registration")
      && isStoredPatientSessionActive(parsed)
    ) {
      return parsed;
    }
  } catch {
    window.localStorage.removeItem(PATIENT_PORTAL_SESSION_STORAGE_KEY);
  }
  clearPatientRegistrationSession({ clearBookingContext: false });
  return null;
}

function usePatientPortalSession() {
  const [session, setSession] = useState<PatientPortalSession | null>(() => readStoredPatientSession());

  function saveSession(nextSession: PatientPortalSession) {
    window.localStorage.setItem(PATIENT_PORTAL_SESSION_STORAGE_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
  }

  function clearSession() {
    window.localStorage.removeItem(PATIENT_PORTAL_SESSION_STORAGE_KEY);
    setSession(null);
  }

  return { session, saveSession, clearSession };
}

function AppShell({
  children,
  session,
  onCancelRegistration,
}: {
  children: ReactNode;
  session: PatientPortalSession | null;
  onCancelRegistration: () => void;
}) {
  const location = useLocation();
  const isAivaRoute = location.pathname.startsWith("/aiva");
  const isPatientRoute = location.pathname.startsWith("/patient");
  const portalNavSession = isPatientPortalRegistrationSession(session) && !isPatientRegistrationSessionActive(session)
    ? null
    : session;

  useEffect(() => {
    document.title = pageTitleForPath(location.pathname);
    const description = descriptionForPath(location.pathname);
    let meta = document.querySelector('meta[name="description"]') as HTMLMetaElement | null;
    if (!meta) {
      meta = document.createElement("meta");
      meta.name = "description";
      document.head.appendChild(meta);
    }
    meta.content = description;
  }, [location.pathname]);

  return (
    <div className="site-shell">
      {!isPatientRoute ? <GlobalPatientHeader session={session} /> : null}
      <main>{children}</main>
      {isAivaRoute ? (
        <footer className="site-footer aiva-footer">
          <div className="footer-grid">
            <section className="footer-brand-block">
              <span className="eyebrow">{branding.aiPlatformName} by {branding.companyName}</span>
              <strong>{branding.aiPlatformName}</strong>
              <p className="footer-tagline">{productAndTagline()}</p>
              <p>Talk. Understand. Act.</p>
              <div className="footer-meaning">
                <p>AIVA v1 is the product layer on top of the existing AIVA runtime.</p>
                <p>Live actions require the verified patient runtime when needed.</p>
              </div>
            </section>
            <section className="footer-column">
              <strong>Product</strong>
              <div className="footer-link-list">
                <Link to="/aiva">Overview</Link>
                <Link to="/aiva/demo">Demo</Link>
                <Link to="/aiva/architecture">Architecture</Link>
                <Link to="/aiva/roadmap">Roadmap</Link>
              </div>
            </section>
            <section className="footer-column">
              <strong>Runtime</strong>
              <div className="footer-link-list">
                <Link to="/careai">Public AIVA</Link>
                <Link to={patientPortalHomePath(portalNavSession)}>Patient Portal</Link>
                <span>STT / LLM / TTS / Workflow engine</span>
              </div>
            </section>
            <section className="footer-column">
              <strong>Safety</strong>
              <div className="footer-link-list">
                <span>No real patient data on AIVA pages</span>
                <span>Demo-safe prompts only</span>
                <span>Existing runtime reused, not rebuilt</span>
              </div>
            </section>
          </div>
          <div className="footer-bottom">
            <p>© 2026 DeepThoughtNet. AIVA — AI Voice Intelligence &amp; Agentic Workflow Platform.</p>
            <Link to="/aiva/demo">Talk to AIVA</Link>
          </div>
        </footer>
      ) : (
        <footer className="site-footer">
          <div className="footer-grid">
            <section className="footer-brand-block">
              <strong>{branding.productName}</strong>
              <p>{branding.tagline}</p>
            </section>

            <section className="footer-column">
              <strong>Jeevanam Care</strong>
              <div className="footer-link-list">
                <Link to="/patient/login">Patient Login</Link>
                <Link to={patientPortalHomePath(portalNavSession)}>Dashboard</Link>
                <Link to="/patient/book-appointment">Book Appointment</Link>
                <Link to="/patient/appointments">Appointments</Link>
                <Link to="/patient/prescriptions">Prescriptions</Link>
                <Link to="/patient/lab">Reports</Link>
                <Link to="/patient/bills">Bills</Link>
              </div>
            </section>

            <section className="footer-column">
              <strong>Jeevanam Platform</strong>
              <div className="footer-link-list">
                <a href={careConfig.discoverAppUrl}>Find Care</a>
                <a href={careConfig.healthcareAppUrl}>Clinic / Hospital Login</a>
                <Link to="/patient/careai">AIVA for your care</Link>
              </div>
            </section>

            <section className="footer-column">
              <strong>Support</strong>
              <div className="footer-link-list">
                <Link to="/contact">Contact</Link>
                <Link to="/help-centre">Help Centre</Link>
                <Link to="/privacy-policy">Privacy Policy</Link>
                <Link to="/terms">Terms</Link>
              </div>
            </section>
          </div>
          <div className="footer-brand-line">{footerBrandingLine()}</div>
          <div className="footer-bottom">
            <p>© 2026 DeepThoughtNet.</p>
          </div>
        </footer>
      )}
    </div>
  );
}

function AivaRedirectPage() {
  useEffect(() => {
    window.location.replace(aivaAppUrl);
  }, []);
  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">AIVA</span>
        <h1>AI Voice Intelligence &amp; Agentic Workflow Platform</h1>
        <p>
          AIVA has moved to a standalone frontend application. Open the microsite at{" "}
          <a href={aivaAppUrl}>{aivaAppUrl}</a>.
        </p>
      </div>
    </section>
  );
}

export function App() {
  const { session, saveSession, clearSession } = usePatientPortalSession();

  function clearRegistrationOnly() {
    clearPatientRegistrationSession();
    clearSession();
  }

  function clearPatientSessionAndContext() {
    clearPatientAuthSession();
    clearSession();
  }

  useEffect(() => {
    if (!isPatientPortalRegistrationSession(session) || isPatientRegistrationSessionActive(session)) {
      return;
    }
    clearRegistrationOnly();
  }, [session]);

  return (
    <PublicLocationProvider>
      <AppShell session={session} onCancelRegistration={clearRegistrationOnly}>
        <Routes>
          <Route path="/ai-assistant/*" element={<Navigate to="/aiva" replace />} />
          <Route path="/aiva/*" element={<AivaRedirectPage />} />
          <Route path="/" element={<CareHomePage session={session} />} />
          <Route path="/doctors" element={<LegacyDiscoverRedirectPage />} />
          <Route path="/doctors/:doctorSlug" element={<LegacyDiscoverRedirectPage />} />
          <Route path="/clinics" element={<LegacyDiscoverRedirectPage />} />
          <Route path="/clinics/:clinicSlug" element={<LegacyDiscoverRedirectPage />} />
          <Route path="/specialities" element={<LegacyDiscoverRedirectPage />} />
          <Route path="/specialities/:specialitySlug" element={<LegacyDiscoverRedirectPage />} />
          <Route path="/careai" element={<Navigate to="/patient/careai" replace />} />
          <Route
            path="/patient/login"
            element={
              <PatientLoginPage
                session={session}
                onSaveSession={saveSession}
                onClearSession={clearPatientSessionAndContext}
                clinicLoginUrl={clinicLoginUrl}
              />
            }
          />
          <Route
            path="/patient/register"
            element={
              <PatientRegistrationPage
                session={session}
                onSaveSession={saveSession}
                onClearSession={clearPatientSessionAndContext}
              />
            }
          />
          <Route path="/patient/dashboard" element={<PatientDashboardPage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route path="/patient/book-appointment" element={<PatientBookAppointmentPage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route path="/patient/appointments" element={<PatientAppointmentsPage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route path="/patient/prescriptions" element={<PatientPrescriptionsPage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route path="/patient/bills" element={<PatientBillsPage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route path="/patient/notifications" element={<PatientNotificationsPage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route path="/patient/lab" element={<PatientLabPage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route path="/patient/careai" element={<PatientCareAiPage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route path="/patient/profile" element={<PatientProfilePage session={session} onSignOut={clearPatientSessionAndContext} />} />
          <Route
            path="/contact"
            element={
              <StaticSupportPage
                eyebrow="Support"
                title="Contact"
                subtitle="Reach the platform team for deployment-specific support."
                body="Use the clinic or platform support channel configured for your deployment. No patient data is shared on this page."
              />
            }
          />
          <Route
            path="/help-centre"
            element={
              <StaticSupportPage
                eyebrow="Support"
                title="Help Centre"
                subtitle="Find quick answers for Jeevanam Care usage."
                body="This page can be replaced with your deployment-specific help content when ready."
              />
            }
          />
          <Route
            path="/privacy-policy"
            element={
              <StaticSupportPage
                eyebrow="Support"
                title="Privacy Policy"
                subtitle="Review the current privacy placeholder for this deployment."
                body="Replace this page with the approved privacy policy text for your environment."
              />
            }
          />
          <Route
            path="/terms"
            element={
              <StaticSupportPage
                eyebrow="Support"
                title="Terms"
                subtitle="Review the current terms placeholder for this deployment."
                body="Replace this page with the approved terms and conditions text for your environment."
              />
            }
          />
        </Routes>
      </AppShell>
    </PublicLocationProvider>
  );
}
