import { type ReactNode, useEffect, useState } from "react";
import { Link, NavLink, Route, Routes, useLocation } from "react-router-dom";
import {
  PublicCareAiPage,
  PublicClinicDetailPage,
  PublicClinicsPage,
  PublicDoctorDetailPage,
  PublicDoctorsPage,
  PublicHomePage,
  PublicSpecialitiesPage,
  PublicSpecialityDetailPage,
} from "./pages/public/PublicDiscoveryPages";
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
  isPatientPortalRegistrationSession,
  patientPortalHomePath,
} from "./api/patientPortal";
import { branding, footerBrandingLine, productAndTagline, productTitle } from "./branding";
import {
  PATIENT_PORTAL_SESSION_STORAGE_KEY,
  clearPatientAuthSession,
  clearPatientRegistrationSession,
  isPatientRegistrationSessionActive,
  isStoredPatientSessionActive,
} from "./pages/patient/patientPortalSessionState";

function deriveClinicLoginUrl() {
  const url = new URL(window.location.origin);
  if (url.hostname === "portal.deepthoughtnet.com") {
    url.hostname = "arogia.deepthoughtnet.com";
  } else if (url.port === "5175") {
    url.port = "5174";
  }
  return url.toString().replace(/\/$/, "");
}

const clinicLoginUrl = import.meta.env.VITE_CLINIC_LOGIN_URL?.trim() || deriveClinicLoginUrl();
const aivaAppUrl = import.meta.env.VITE_AIVA_APP_URL?.trim() || new URL("/careai", window.location.origin).toString();

const navItems = [
  { to: "/", label: "Home" },
  { to: "/doctors", label: "Doctors" },
  { to: "/clinics", label: "Clinics" },
  { to: "/specialities", label: "Specialities" },
  { to: "/careai", label: "AI Assistant" },
  { to: "/aiva", label: "AIVA" },
];

const aivaNavItems = [
  { to: "/aiva", label: "Overview" },
  { to: "/aiva/demo", label: "Demo" },
  { to: "/aiva/architecture", label: "Architecture" },
  { to: "/aiva/roadmap", label: "Roadmap" },
];

function pageTitleForPath(pathname: string) {
  if (pathname === "/") return productTitle();
  if (pathname === "/doctors") return `Doctors | ${branding.productName}`;
  if (pathname === "/clinics") return `Clinics | ${branding.productName}`;
  if (pathname === "/specialities") return `Specialities | ${branding.productName}`;
  if (pathname.startsWith("/patient")) return `Patient Portal | ${branding.productName}`;
  if (pathname === "/careai") return `AIVA | ${branding.productName}`;
  if (pathname.startsWith("/aiva")) return `AIVA | ${branding.productName}`;
  return productTitle();
}

function descriptionForPath(pathname: string) {
  if (pathname === "/") return `${branding.productName} is the ${branding.tagline} for clinics and hospitals.`;
  if (pathname === "/careai") return `${branding.productName} connects patients to guided care navigation powered by ${branding.aiPlatformName}.`;
  if (pathname.startsWith("/patient")) return `${branding.productName} patient portal for verified appointments, prescriptions, bills, and reports.`;
  if (pathname.startsWith("/aiva")) return `${branding.productName} AI voice and assistant platform powered by ${branding.aiPlatformName}.`;
  return `${branding.productName} by ${branding.companyName}.`;
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
  const activeRegistrationSession = isPatientPortalRegistrationSession(session) && isPatientRegistrationSessionActive(session)
    ? session
    : null;
  const portalNavSession = isPatientPortalRegistrationSession(session) && !activeRegistrationSession
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
      {isAivaRoute ? (
        <header className="site-header aiva-header">
          <Link to="/aiva" className="brand aiva-brand">
            <span className="brand-badge aiva-brand-badge">AI</span>
            <span className="brand-meta">
              <strong>AIVA</strong>
              <small>{productAndTagline()}</small>
            </span>
          </Link>
          <nav className="main-nav" aria-label="AIVA navigation">
            {aivaNavItems.map((item) => (
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
            <Link className="secondary-button" to="/aiva/architecture">
              View architecture
            </Link>
            <Link className="primary-button" to="/aiva/demo">
              Talk to AIVA
            </Link>
          </div>
        </header>
      ) : (
        <header className="site-header">
          <Link to="/" className="brand">
            <span className="brand-badge">JH</span>
            <span className="brand-meta">
              <strong>{branding.productName}</strong>
              <small>{branding.tagline}</small>
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
              Open {branding.productName} Admin Console
            </a>
            <Link className="primary-button" to={patientPortalHomePath(activeRegistrationSession || portalNavSession)}>
              {activeRegistrationSession ? "Continue registration" : portalNavSession ? `Open ${branding.productName} Patient Portal` : `${branding.productName} Patient Portal`}
            </Link>
            {activeRegistrationSession ? (
              <button className="ghost-button" type="button" onClick={onCancelRegistration}>
                Start over
              </button>
            ) : null}
          </div>
        </header>
      )}
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
              <p>Intelligent Healthcare Platform for clinics, patients, and teams.</p>
            </section>

            <section className="footer-column">
              <strong>{branding.productName}</strong>
              <div className="footer-link-list">
                <Link to="/">About</Link>
                <Link to="/clinics">For Clinics</Link>
                <Link to={patientPortalHomePath(portalNavSession)}>Patient Portal</Link>
                <span>Doctor Portal</span>
              </div>
            </section>

            <section className="footer-column">
              <strong>Platform</strong>
              <div className="footer-link-list">
                <Link to="/careai">AIVA</Link>
                <span>Appointments</span>
                <span>Consultation</span>
                <span>Lab Reports</span>
                <span>Billing</span>
                <span>Pharmacy</span>
              </div>
            </section>

            <section className="footer-column">
              <strong>Support</strong>
              <div className="footer-link-list">
                <span>Contact</span>
                <span>Help Center</span>
                <span>Privacy Policy</span>
                <span>Terms</span>
              </div>
            </section>
          </div>
          <div className="footer-brand-line">{footerBrandingLine()}</div>
          <div className="footer-environment-line">Demo / UAT Environment</div>
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
    <AppShell session={session} onCancelRegistration={clearRegistrationOnly}>
      <Routes>
        <Route path="/aiva/*" element={<AivaRedirectPage />} />
        <Route path="/" element={<PublicHomePage session={session} />} />
        <Route path="/doctors" element={<PublicDoctorsPage session={session} />} />
        <Route path="/doctors/:doctorSlug" element={<PublicDoctorDetailPage session={session} />} />
        <Route path="/clinics" element={<PublicClinicsPage session={session} />} />
        <Route path="/clinics/:clinicSlug" element={<PublicClinicDetailPage session={session} />} />
        <Route path="/specialities" element={<PublicSpecialitiesPage />} />
        <Route path="/specialities/:specialitySlug" element={<PublicSpecialityDetailPage session={session} />} />
        <Route path="/careai" element={<PublicCareAiPage session={session} />} />
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
      </Routes>
    </AppShell>
  );
}
