import { useEffect, useMemo, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { patientPortalHomePath, type PatientPortalSession } from "../api/patientPortal";
import { branding, productAndTagline } from "../branding";
import { careConfig } from "../config";

const unauthenticatedNavItems = [
  { to: "/patient/login", label: "Patient Login" },
  { to: "/patient/register", label: "Register" },
  { to: "/help-centre", label: "Help" },
];

const authenticatedNavItems = [
  { to: "/patient/dashboard", label: "Dashboard" },
  { to: "/patient/appointments", label: "Appointments" },
  { to: "/patient/prescriptions", label: "Prescriptions" },
  { to: "/patient/lab", label: "Reports" },
  { to: "/patient/bills", label: "Bills" },
  { to: "/patient/notifications", label: "Notifications" },
  { to: "/patient/careai", label: "AIVA" },
  { to: "/patient/profile", label: "Profile" },
];

export function GlobalPatientHeader({ session }: { session: PatientPortalSession | null }) {
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const patientPortalUrl = patientPortalHomePath(session);
  const discoverUrl = useMemo(() => careConfig.discoverAppUrl, []);
  const healthcareUrl = useMemo(() => careConfig.healthcareAppUrl, []);
  const isPatientRoute = location.pathname.startsWith("/patient");
  const navItems = session?.sessionRole === "patient" ? authenticatedNavItems : unauthenticatedNavItems;

  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  return (
    <header className={`global-header${isPatientRoute ? " global-header-portal" : ""}`}>
      <div className="global-header-brand-row">
        <Link to="/" className="brand global-brand">
          <span className="brand-badge">JC</span>
          <span className="brand-meta">
            <strong>{branding.productName}</strong>
            <small>{productAndTagline()}</small>
          </span>
        </Link>

        <div className="global-header-mobile-actions">
          <a className="global-header-icon-button" href={discoverUrl} aria-label="Find doctors and clinics on Jeevanam Discover">
            <span aria-hidden="true">⌕</span>
          </a>
          <button
            type="button"
            className="global-header-icon-button"
            aria-label="Toggle navigation"
            aria-expanded={mobileMenuOpen}
            onClick={() => setMobileMenuOpen((current) => !current)}
          >
            <span aria-hidden="true">☰</span>
          </button>
        </div>
      </div>

      <nav className={`main-nav global-header-nav${mobileMenuOpen ? " is-open" : ""}`} aria-label="Care navigation">
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

      <div className="header-actions global-header-actions">
        <a className="secondary-button" href={discoverUrl}>
          Find Care
        </a>
        <a className="ghost-button" href={healthcareUrl}>
          Clinic / Hospital Login
        </a>
      </div>

      {mobileMenuOpen ? (
        <div className="global-header-drawer">
          <nav className="global-header-drawer-nav" aria-label="Mobile Care navigation">
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
          <div className="global-header-drawer-actions">
            <a className="ghost-button" href={discoverUrl}>
              Find Care
            </a>
            <a className="ghost-button" href={healthcareUrl}>
              Clinic / Hospital Login
            </a>
            <Link className="ghost-button" to={patientPortalUrl}>
              Open {branding.productName}
            </Link>
          </div>
        </div>
      ) : null}
    </header>
  );
}
