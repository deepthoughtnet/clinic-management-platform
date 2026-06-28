import { useEffect, useMemo, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { branding, productAndTagline } from "../branding";
import { patientPortalHomePath, type PatientPortalSession } from "../api/patientPortal";
import {
  PUBLIC_DEFAULT_LOCATION,
  PUBLIC_LOCATION_OPTIONS,
  normalizePublicLocation,
  usePublicLocation,
} from "../context/publicLocation";

const navItems = [
  { to: "/", label: "Home" },
  { to: "/doctors", label: "Doctors" },
  { to: "/clinics", label: "Clinics" },
  { to: "/specialities", label: "Specialities" },
  { to: "/aiva", label: "AIVA" },
  { to: "/patient/login", label: "Patient Login" },
];

function deriveClinicLoginUrl() {
  const url = new URL(window.location.origin);
  if (url.hostname === "portal.deepthoughtnet.com") {
    url.hostname = "arogia.deepthoughtnet.com";
  } else if (url.port === "5175") {
    url.port = "5174";
  }
  return url.toString().replace(/\/$/, "");
}

function formatLocationLabel(location: string) {
  return normalizePublicLocation(location) || PUBLIC_DEFAULT_LOCATION;
}

export function GlobalPatientHeader({ session }: { session: PatientPortalSession | null }) {
  const location = useLocation();
  const { locationState, setSelectedLocation } = usePublicLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [demoMenuOpen, setDemoMenuOpen] = useState(false);
  const [locationMenuOpen, setLocationMenuOpen] = useState(false);
  const [locationDraft, setLocationDraft] = useState(locationState.location);
  const clinicLoginUrl = useMemo(() => deriveClinicLoginUrl(), []);
  const patientPortalUrl = patientPortalHomePath(session);
  const displayLocation = locationState.location || PUBLIC_DEFAULT_LOCATION;
  const isPatientRoute = location.pathname.startsWith("/patient");

  useEffect(() => {
    setLocationDraft(displayLocation);
  }, [displayLocation]);

  useEffect(() => {
    setMobileMenuOpen(false);
    setDemoMenuOpen(false);
    setLocationMenuOpen(false);
  }, [location.pathname]);

  function commitLocation(nextLocation: string) {
    setSelectedLocation(formatLocationLabel(nextLocation));
    setLocationDraft(formatLocationLabel(nextLocation));
    setLocationMenuOpen(false);
  }

  return (
    <header className={`global-header${isPatientRoute ? " global-header-portal" : ""}`}>
      <div className="global-header-brand-row">
        <Link to="/" className="brand global-brand">
          <span className="brand-badge">JH</span>
          <span className="brand-meta">
            <strong>{branding.productName}</strong>
            <small>{productAndTagline()}</small>
          </span>
        </Link>

        <div className="global-header-mobile-actions">
          <Link className="global-header-icon-button" to="/" aria-label="Search doctors and clinics">
            <span aria-hidden="true">🔍</span>
          </Link>
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

      <nav className={`main-nav global-header-nav${mobileMenuOpen ? " is-open" : ""}`} aria-label="Main navigation">
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
        <Link className="global-header-search-link" to="/" aria-label="Search doctors and clinics">
          <span aria-hidden="true">🔍</span>
        </Link>

        <div className="global-location-shell">
          <button
            type="button"
            className="smart-location-pill global-location-pill"
            aria-label={`Selected location ${displayLocation}`}
            aria-expanded={locationMenuOpen}
            onClick={() => setLocationMenuOpen((current) => !current)}
          >
            <span>📍 {displayLocation}</span>
            <span className="global-location-caret" aria-hidden="true">▼</span>
          </button>
          {locationMenuOpen ? (
            <div className="global-location-panel" role="dialog" aria-label="Select location">
              <label className="smart-location-field">
                <span>City or locality</span>
                <input
                  value={locationDraft}
                  onChange={(event) => setLocationDraft(normalizePublicLocation(event.target.value))}
                  placeholder={PUBLIC_DEFAULT_LOCATION}
                />
              </label>
              <div className="smart-location-options" role="list" aria-label="Popular locations">
                {PUBLIC_LOCATION_OPTIONS.map((option) => (
                  <button
                    key={option}
                    className={`smart-location-option${displayLocation === option ? " is-active" : ""}`}
                    type="button"
                    onClick={() => commitLocation(option)}
                  >
                    {option}
                  </button>
                ))}
              </div>
              <div className="smart-location-actions">
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => commitLocation(locationDraft)}
                  disabled={!normalizePublicLocation(locationDraft)}
                >
                  Save location
                </button>
                <button className="ghost-button" type="button" onClick={() => setLocationDraft(displayLocation)}>
                  Reset
                </button>
                <button className="ghost-button" type="button" onClick={() => setLocationMenuOpen(false)}>
                  Close
                </button>
              </div>
            </div>
          ) : null}
        </div>

        <div className="global-demo-shell">
          <button
            type="button"
            className="secondary-button global-demo-button"
            aria-expanded={demoMenuOpen}
            onClick={() => setDemoMenuOpen((current) => !current)}
          >
            Demo Links <span aria-hidden="true">▼</span>
          </button>
          {demoMenuOpen ? (
            <div className="global-demo-menu" role="menu" aria-label="Demo links">
              <a href={clinicLoginUrl} className="global-demo-menu-link" role="menuitem">
                Open {branding.productName} Admin Console
              </a>
              <Link to={patientPortalUrl} className="global-demo-menu-link" role="menuitem">
                Open {branding.productName} Patient Portal
              </Link>
            </div>
          ) : null}
        </div>
      </div>

      {mobileMenuOpen ? (
        <div className="global-header-drawer">
          <nav className="global-header-drawer-nav" aria-label="Mobile navigation">
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
            <Link className="ghost-button" to="/">
              Search
            </Link>
            <button className="secondary-button" type="button" onClick={() => setLocationMenuOpen((current) => !current)}>
              📍 {displayLocation}
            </button>
            <a className="ghost-button" href={clinicLoginUrl}>
              Open {branding.productName} Admin Console
            </a>
            <Link className="ghost-button" to={patientPortalUrl}>
              Open {branding.productName} Patient Portal
            </Link>
          </div>
        </div>
      ) : null}
    </header>
  );
}
