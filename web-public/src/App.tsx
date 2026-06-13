import { type ReactNode, useState } from "react";
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
  patientPortalHomePath,
} from "./api/patientPortal";

const clinicLoginUrl = "http://localhost:5174";
const patientSessionStorageKey = "clinic-web-public-patient-session";

const navItems = [
  { to: "/", label: "Home" },
  { to: "/doctors", label: "Doctors" },
  { to: "/clinics", label: "Clinics" },
  { to: "/specialities", label: "Specialities" },
  { to: "/careai", label: "CareAI" },
];

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
    if (
      parsed?.mode === "otp"
      && parsed.patientSessionToken
      && parsed.tenantId
      && parsed.tenantCode
      && (parsed.sessionRole === "patient" || parsed.sessionRole === "registration")
    ) {
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

function AppShell({ children, session }: { children: ReactNode; session: PatientPortalSession | null }) {
  const location = useLocation();

  return (
    <div className="site-shell">
      <header className="site-header">
        <Link to="/" className="brand">
          <span className="brand-badge">CP</span>
          <span className="brand-meta">
            <strong>CuraPilot</strong>
            <small>The AI Co-Pilot for Healthcare</small>
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
            Open CuraPilot Admin Console
          </a>
          <Link className="primary-button" to={patientPortalHomePath(session)}>
            {session?.sessionRole === "registration" ? "Continue registration" : session ? "Open CuraPilot Patient Portal" : "CuraPilot Patient Portal"}
          </Link>
        </div>
      </header>
      <main>{children}</main>
      <footer className="site-footer">
        <div className="footer-grid">
          <section className="footer-brand-block">
            <span className="eyebrow">By DeepThoughtNet</span>
            <strong>CuraPilot</strong>
            <p className="footer-tagline">The AI Co-Pilot for Healthcare</p>
            <p>Smarter Care. Powered by AI.</p>
            <div className="footer-meaning">
              <p>Cura means care, healing, and wellbeing.</p>
              <p>क्यूरा = देखभाल, उपचार और स्वास्थ्य का भरोसा</p>
            </div>
          </section>

          <section className="footer-column">
            <strong>CuraPilot</strong>
            <div className="footer-link-list">
              <Link to="/">About</Link>
              <Link to="/clinics">For Clinics</Link>
              <span>CuraPilot Doctor Portal</span>
              <Link to={patientPortalHomePath(session)}>For Patients</Link>
            </div>
          </section>

          <section className="footer-column">
            <strong>Platform</strong>
            <div className="footer-link-list">
              <Link to="/careai">CareAI</Link>
              <span>CarePilot</span>
              <Link to={patientPortalHomePath(session)}>Patient Portal</Link>
              <span>Doctor Portal</span>
              <span>Pharmacy</span>
              <span>Billing</span>
              <span>Analytics</span>
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

          <section className="footer-column">
            <strong>DeepThoughtNet</strong>
            <div className="footer-link-list">
              <span>AI-powered healthcare operations platform</span>
              <span>Smarter Care. Powered by AI.</span>
            </div>
          </section>
        </div>
        <div className="footer-bottom">
          <p>© 2026 DeepThoughtNet. CuraPilot — The AI Co-Pilot for Healthcare.</p>
          <Link to={patientPortalHomePath(session)}>
            {session?.sessionRole === "registration" ? "Continue registration" : session ? "Open CuraPilot Patient Portal" : "Open CuraPilot Patient Portal"}
          </Link>
        </div>
      </footer>
    </div>
  );
}

export function App() {
  const { session, saveSession, clearSession } = usePatientPortalSession();

  return (
    <AppShell session={session}>
      <Routes>
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
              onClearSession={clearSession}
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
              onClearSession={clearSession}
            />
          }
        />
        <Route path="/patient/dashboard" element={<PatientDashboardPage session={session} onSignOut={clearSession} />} />
        <Route path="/patient/book-appointment" element={<PatientBookAppointmentPage session={session} onSignOut={clearSession} />} />
        <Route path="/patient/appointments" element={<PatientAppointmentsPage session={session} onSignOut={clearSession} />} />
        <Route path="/patient/prescriptions" element={<PatientPrescriptionsPage session={session} onSignOut={clearSession} />} />
        <Route path="/patient/bills" element={<PatientBillsPage session={session} onSignOut={clearSession} />} />
        <Route path="/patient/notifications" element={<PatientNotificationsPage session={session} onSignOut={clearSession} />} />
        <Route path="/patient/lab" element={<PatientLabPage session={session} onSignOut={clearSession} />} />
        <Route path="/patient/careai" element={<PatientCareAiPage session={session} onSignOut={clearSession} />} />
        <Route path="/patient/profile" element={<PatientProfilePage session={session} onSignOut={clearSession} />} />
      </Routes>
    </AppShell>
  );
}
