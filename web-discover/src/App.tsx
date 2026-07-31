import { Component, type ErrorInfo, type ReactNode, useEffect, useState } from "react";
import { CheckCircleOutlineOutlined, LocationOnOutlined } from "@mui/icons-material";
import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate, useParams } from "react-router-dom";
import { discoverBrand } from "./branding";
import { DiscoverEmptyState } from "./components/DiscoveryComponents";
import { discoverConfig } from "./config";
import {
  PUBLIC_CURRENT_LOCATION_LABEL,
  PUBLIC_DEFAULT_LOCATION,
  PUBLIC_LOCATION_OPTIONS,
  PublicLocationProvider,
  normalizePublicLocation,
  type PublicLocationCoordinates,
  usePublicLocation,
} from "./context/PublicLocationContext";
import { ProviderSessionProvider, useProviderSession } from "./context/ProviderSessionContext";
import { ProviderAuthError, startProviderApplication } from "./api/providerAuth";
import {
  PublicClinicDetailPage,
  PublicClinicsPage,
  PublicDoctorDetailPage,
  PublicDoctorsPage,
  PublicHomePage,
  PublicHospitalDetailPage,
  PublicHospitalsPage,
  PublicSpecialitiesPage,
  PublicSpecialityDetailPage,
} from "./pages/discovery/PublicDiscoveryPages";
import { LandingPagePage } from "./pages/public/LandingPagePage";
import type { ProviderType } from "./api/providerOnboarding";
import { ProviderDashboardPage } from "./pages/provider/ProviderDashboardPage";
import { ProviderLoginPage } from "./pages/provider/ProviderLoginPage";
import { ProviderLandingPagePage } from "./pages/provider/ProviderLandingPagePage";
import { ProviderOnboardingPage } from "./pages/provider/ProviderOnboardingPage";
import { ProviderWorkspacePage } from "./pages/provider/ProviderWorkspacePage";
import { DISCOVER_ROUTES, primaryNavigationRoutes } from "./routes";
import { providerOnboardingStepRoute } from "./features/provider/providerOnboardingRoutes";

type ShellPageProps = {
  eyebrow: string;
  title: string;
  body: string;
  stateIcon?: string;
  stateTitle?: string;
  stateBody?: string;
  ctaLabel?: string;
  ctaTo?: string;
  secondaryLabel?: string;
  secondaryTo?: string;
};

const routeMeta: Record<string, { title: string; description: string }> = {
  [DISCOVER_ROUTES.home.path]: {
    title: "Jeevanam Discover",
    description: "Find doctors, clinics, hospitals, specialities, and healthcare services with Jeevanam Discover.",
  },
  [DISCOVER_ROUTES.doctors.path]: {
    title: "Doctors | Jeevanam Discover",
    description: "Find public doctor profiles by speciality, service, and location.",
  },
  [DISCOVER_ROUTES.clinics.path]: {
    title: "Clinics | Jeevanam Discover",
    description: "Explore public clinic pages, services, doctors, and locations.",
  },
  [DISCOVER_ROUTES.hospitals.path]: {
    title: "Hospitals | Jeevanam Discover",
    description: "Discover hospital profiles, departments, facilities, and public care information.",
  },
  [DISCOVER_ROUTES.specialities.path]: {
    title: "Specialities | Jeevanam Discover",
    description: "Start care discovery by speciality and connect to trusted providers.",
  },
  [DISCOVER_ROUTES.services.path]: {
    title: "Services | Jeevanam Discover",
    description: "Discover healthcare services across consultations, diagnostics, vaccination, and more.",
  },
  [DISCOVER_ROUTES.healthcare.path]: {
    title: "Jeevanam Healthcare | Jeevanam Discover",
    description: "Learn about the Jeevanam Healthcare operations platform for clinics and hospitals.",
  },
  [DISCOVER_ROUTES.pricing.path]: {
    title: "Pricing | Jeevanam Discover",
    description: "Explore Jeevanam Healthcare plan information for clinics and hospitals.",
  },
  [DISCOVER_ROUTES.listPractice.path]: {
    title: "List Your Practice | Jeevanam Discover",
    description: "Start provider registration for doctors, clinics, and hospitals.",
  },
  [DISCOVER_ROUTES.login.path]: {
    title: "Login | Jeevanam Discover",
    description: "Choose Jeevanam Care for patients or Jeevanam Healthcare for clinic and hospital teams.",
  },
  [DISCOVER_ROUTES.providerApplications.path]: {
    title: "Provider Applications | Jeevanam Discover",
    description: "Review your provider applications and continue onboarding where needed.",
  },
  [DISCOVER_ROUTES.providerLogin.path]: {
    title: "Provider Login | Jeevanam Discover",
    description: "Sign in with a verification code to manage your provider workspace.",
  },
  [DISCOVER_ROUTES.providerWorkspace.path]: {
    title: "Provider Workspace | Jeevanam Discover",
    description: "View your provider applications and public profiles.",
  },
  [DISCOVER_ROUTES.providerLandingPage.path]: {
    title: "Provider Profiles | Jeevanam Discover",
    description: "Configure your profile, landing page, and publication setup.",
  },
  [DISCOVER_ROUTES.providerAccount.path]: {
    title: "Account & Security | Jeevanam Discover",
    description: "Review your provider session and account details.",
  },
};

function updateDocumentMetadata(pathname: string) {
  const landingMeta = pathname.startsWith("/discover/doctors/") && pathname.endsWith("/home")
    ? { title: "Doctor Landing Page | Jeevanam Discover", description: "View the structured public landing page for this doctor." }
    : pathname.startsWith("/discover/clinics/") && pathname.endsWith("/home")
      ? { title: "Clinic Landing Page | Jeevanam Discover", description: "View the structured public landing page for this clinic." }
      : pathname.startsWith("/discover/hospitals/") && pathname.endsWith("/home")
        ? { title: "Hospital Landing Page | Jeevanam Discover", description: "View the structured public landing page for this hospital." }
        : null;
  const detailMeta = pathname.startsWith("/discover/doctors/")
    ? { title: "Doctor Profile | Jeevanam Discover", description: "View public doctor profiles, booking options, and practice information." }
    : pathname.startsWith("/discover/clinics/")
      ? { title: "Clinic Profile | Jeevanam Discover", description: "View public clinic pages, services, doctors, and locations." }
      : pathname.startsWith("/discover/hospitals/")
        ? { title: "Hospital Profile | Jeevanam Discover", description: "View public hospital pages, departments, facilities, and locations." }
        : pathname.startsWith("/discover/specialities/")
          ? { title: "Speciality | Jeevanam Discover", description: "Browse providers for this medical speciality." }
          : null;
  const meta = landingMeta ?? detailMeta ?? routeMeta[pathname] ?? {
    title: "Jeevanam Discover",
    description: "Jeevanam Discover helps people find trusted healthcare providers and services.",
  };
  document.title = meta.title;
  let description = document.querySelector('meta[name="description"]') as HTMLMetaElement | null;
  if (!description) {
    description = document.createElement("meta");
    description.name = "description";
    document.head.appendChild(description);
  }
  description.content = meta.description;
}

class DiscoverErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean }> {
  state = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("[web-discover] application error", { message: error.message, componentStack: info.componentStack });
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="page-section narrow-page" role="main">
          <span className="eyebrow">Jeevanam Discover</span>
          <h1>We could not load this page.</h1>
          <p>Please refresh the page or return to the Discover home page.</p>
          <Link className="primary-button" to={DISCOVER_ROUTES.home.path}>
            Return home
          </Link>
        </main>
      );
    }
    return this.props.children;
  }
}

function BrandLockup() {
  return (
    <Link className="brand-lockup" to={DISCOVER_ROUTES.home.path} aria-label="Jeevanam Discover home">
      <span className="brand-mark" aria-hidden="true">
        <img src="/favicon.svg" alt="" />
      </span>
      <span>
        <strong>{discoverBrand.productName}</strong>
        <small>Find trusted healthcare</small>
      </span>
    </Link>
  );
}

function isProviderSessionRoute(pathname: string) {
  return pathname === "/provider" || pathname.startsWith("/provider/") || pathname.startsWith("/register/");
}

function maskProviderIdentity(value: string | null) {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  if (trimmed.includes("@")) {
    const [localPart, domain = ""] = trimmed.split("@");
    const prefix = localPart.slice(0, 1) || "";
    return `${prefix}${"*".repeat(Math.max(0, Math.min(8, localPart.length - 1)))}@${domain}`;
  }
  const digits = trimmed.replace(/[^0-9]/g, "");
  if (!digits) {
    return null;
  }
  return `${"*".repeat(Math.max(0, digits.length - 4))}${digits.slice(-4)}`;
}

function HeaderLocationSelector() {
  const { locationState, setSelectedLocation } = usePublicLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [draftLocation, setDraftLocation] = useState(locationState.location);
  const [message, setMessage] = useState<string | null>(null);
  const [detecting, setDetecting] = useState(false);

  useEffect(() => {
    if (menuOpen) {
      setDraftLocation(locationState.location);
      setMessage(null);
    }
  }, [locationState.location, menuOpen]);

  function commitSelectedLocation(nextLocation: string, nextCoordinates: PublicLocationCoordinates | null = null) {
    const normalizedLocation = normalizePublicLocation(nextLocation) || PUBLIC_DEFAULT_LOCATION;
    setSelectedLocation(normalizedLocation, nextCoordinates);
    setDraftLocation(normalizedLocation);
    setMessage(null);
    setMenuOpen(false);
  }

  function handleCurrentLocation() {
    setMessage(null);
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setMessage("Location services are not available in this browser.");
      return;
    }
    setDetecting(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        commitSelectedLocation(PUBLIC_CURRENT_LOCATION_LABEL, {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
        setDetecting(false);
      },
      () => {
        setDetecting(false);
        setMessage("Location permission was not allowed.");
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }

  return (
    <div className="header-location-selector">
      <button
        className="ghost-button header-location-selector-summary"
        type="button"
        aria-haspopup="dialog"
        aria-expanded={menuOpen}
        aria-label={`Change location, currently ${locationState.location}`}
        onClick={() => setMenuOpen((current) => !current)}
      >
        <LocationOnOutlined fontSize="small" aria-hidden="true" />
        <span>{locationState.location}</span>
      </button>
      {menuOpen ? (
        <div className="header-location-selector-panel" role="dialog" aria-label="Select location">
          <label className="header-location-selector-field">
            <span className="header-location-selector-label">City or locality</span>
            <input value={draftLocation} onChange={(event) => setDraftLocation(normalizePublicLocation(event.target.value))} placeholder="Pune" />
          </label>
          <div className="chip-row" role="list" aria-label="Popular locations">
            {PUBLIC_LOCATION_OPTIONS.map((location) => (
              <button key={location} className="chip-button" type="button" onClick={() => commitSelectedLocation(location)}>
                {location}
              </button>
            ))}
          </div>
          <div className="cta-row">
            <button className="secondary-button" type="button" onClick={() => commitSelectedLocation(draftLocation)} disabled={!normalizePublicLocation(draftLocation)}>
              Save location
            </button>
            <button className="text-button" type="button" onClick={handleCurrentLocation} disabled={detecting}>
              {detecting ? "Detecting..." : "Use my current location"}
            </button>
            {locationState.location !== PUBLIC_DEFAULT_LOCATION ? (
              <button className="text-button" type="button" onClick={() => commitSelectedLocation(PUBLIC_DEFAULT_LOCATION, null)}>
                Clear
              </button>
            ) : null}
          </div>
          {message ? <p className="form-note" role="status">{message}</p> : null}
        </div>
      ) : null}
    </div>
  );
}

function ProviderHeaderActions() {
  const navigate = useNavigate();
  const location = useLocation();
  const { status, workspace, logout } = useProviderSession();
  const [loggingOut, setLoggingOut] = useState(false);
  const isHydratingSession = (status === "idle" || status === "loading") && isProviderSessionRoute(location.pathname);
  const maskedIdentity = maskProviderIdentity(workspace?.contactEmail ?? workspace?.contactPhone ?? null);

  async function endSession(targetPath: string) {
    setLoggingOut(true);
    try {
      await logout();
      navigate(targetPath, { replace: true });
    } finally {
      setLoggingOut(false);
    }
  }

  if (isHydratingSession) {
    return (
      <div className="header-actions header-actions--loading" aria-live="polite">
        <span className="header-session-pill">Restoring provider session</span>
      </div>
    );
  }

  if (status === "authenticated") {
    return (
      <div className="header-actions header-actions--authenticated">
        <HeaderLocationSelector />
        <a className="ghost-button" href={discoverConfig.careAppUrl} target="_blank" rel="noopener noreferrer">
          Patient Login
        </a>
        <details className="provider-account-menu">
          <summary className="ghost-button provider-account-menu-summary" aria-label="Provider account menu">
            <span className="provider-account-menu-label">
              <span className="provider-account-menu-title">Provider Account</span>
              <span className="provider-account-menu-subtitle">{maskedIdentity ? `Signed in as ${maskedIdentity}` : "Active session"}</span>
            </span>
          </summary>
          <div className="provider-account-menu-panel" role="menu" aria-label="Provider account actions">
            <Link className="provider-account-menu-item" to={DISCOVER_ROUTES.providerWorkspace.path}>
              Dashboard
            </Link>
            <button className="provider-account-menu-item" type="button" onClick={() => void endSession(DISCOVER_ROUTES.providerLogin.path)} disabled={loggingOut}>
              Switch account
            </button>
            <button className="provider-account-menu-item provider-account-menu-item--danger" type="button" onClick={() => void endSession(DISCOVER_ROUTES.providerLogin.path)} disabled={loggingOut}>
              Logout
            </button>
          </div>
        </details>
      </div>
    );
  }

  return (
    <div className="header-actions">
      <HeaderLocationSelector />
      <a className="ghost-button" href={discoverConfig.careAppUrl} target="_blank" rel="noopener noreferrer">
        Patient Login
      </a>
      <Link className="ghost-button" to={DISCOVER_ROUTES.providerLogin.path}>
        Provider Login
      </Link>
      <Link className="primary-button" to={DISCOVER_ROUTES.listPractice.path}>
        For Providers
      </Link>
    </div>
  );
}

function Shell({ children }: { children: ReactNode }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const isDoctorsDirectoryRoute = location.pathname === DISCOVER_ROUTES.doctors.path;

  useEffect(() => {
    setMenuOpen(false);
    updateDocumentMetadata(location.pathname);
  }, [location.pathname]);

  return (
    <div className={`discover-shell${isDoctorsDirectoryRoute ? " discover-shell--doctors-directory-wide" : ""}`}>
      <header className="site-header">
        <div className="header-inner">
          <BrandLockup />
          <button
            className="mobile-menu-button"
            type="button"
            aria-label="Toggle Discover navigation"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((current) => !current)}
          >
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
          </button>
          <nav className={`primary-nav${menuOpen ? " is-open" : ""}`} aria-label="Discover navigation">
            {primaryNavigationRoutes.map((route) => (
              <NavLink key={route.path} to={route.path} end={route.path === DISCOVER_ROUTES.home.path} className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
                {route.label}
              </NavLink>
            ))}
          </nav>
          <ProviderHeaderActions />
        </div>
      </header>

      <main>{children}</main>

      <footer className="site-footer">
        <div className="footer-inner">
          <section className="footer-brand" aria-label="Jeevanam Discover">
            <div className="footer-brand-row">
              <span className="brand-mark" aria-hidden="true">
                <img src="/favicon.svg" alt="" />
              </span>
              <div>
                <strong>{discoverBrand.productName}</strong>
                <p>{discoverBrand.tagline}</p>
              </div>
            </div>
          </section>
          <nav className="footer-column" aria-label="Patients">
            <strong>Patients</strong>
            <Link to={DISCOVER_ROUTES.doctors.path}>Find doctors</Link>
            <Link to={DISCOVER_ROUTES.clinics.path}>Find clinics</Link>
            <Link to={DISCOVER_ROUTES.hospitals.path}>Find hospitals</Link>
            <Link to={DISCOVER_ROUTES.specialities.path}>Specialities</Link>
            <a href={discoverConfig.careAppUrl} target="_blank" rel="noopener noreferrer">
              Patient login
            </a>
          </nav>
          <nav className="footer-column" aria-label="Providers">
            <strong>Providers</strong>
            <Link to={DISCOVER_ROUTES.listPractice.path}>Register practice</Link>
            <Link to={DISCOVER_ROUTES.providerLogin.path}>Provider login</Link>
            <Link to={DISCOVER_ROUTES.healthcare.path}>Provider information</Link>
          </nav>
          <nav className="footer-column" aria-label="Support">
            <strong>Support</strong>
            <Link to={DISCOVER_ROUTES.about.path}>About</Link>
            <Link to={DISCOVER_ROUTES.contact.path}>Contact</Link>
            <Link to={DISCOVER_ROUTES.contact.path}>Help</Link>
            <a className="footer-placeholder-link" href="#" onClick={(event) => event.preventDefault()}>
              Accessibility
            </a>
            <a className="footer-placeholder-link" href="#" onClick={(event) => event.preventDefault()}>
              Sitemap
            </a>
          </nav>
          <nav className="footer-column" aria-label="Legal">
            <strong>Legal</strong>
            <Link to={DISCOVER_ROUTES.privacy.path}>Privacy</Link>
            <Link to={DISCOVER_ROUTES.terms.path}>Terms</Link>
            <a className="footer-placeholder-link" href="#" onClick={(event) => event.preventDefault()}>
              Status
            </a>
            <a className="footer-placeholder-link" href="#" onClick={(event) => event.preventDefault()}>
              Security
            </a>
            <a className="footer-placeholder-link" href="#" onClick={(event) => event.preventDefault()}>
              Cookies
            </a>
          </nav>
          <section className="footer-bottom">
            <span>© {new Date().getFullYear()} Jeevanam Discover. Trusted public healthcare discovery for patients and providers.</span>
            <div>
              <span className="footer-trust-note">
                <CheckCircleOutlineOutlined fontSize="small" aria-hidden="true" />
                Verified public information and clear discovery routes.
              </span>
            </div>
          </section>
        </div>
      </footer>
    </div>
  );
}

function ShellPage({ eyebrow, title, body, stateIcon, stateTitle, stateBody, ctaLabel, ctaTo, secondaryLabel, secondaryTo }: ShellPageProps) {
  return (
    <section className="page-section compact-page shell-page">
      <div className="compact-page-hero">
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{body}</p>
      </div>
      <DiscoverEmptyState
        icon={stateIcon}
        title={stateTitle ?? title}
        description={stateBody ?? body}
        primaryAction={ctaLabel}
        primaryTo={ctaTo}
        secondaryAction={secondaryLabel}
        secondaryTo={secondaryTo}
      />
    </section>
  );
}

function LegacyRedirect({ to }: { to: string }) {
  const location = useLocation();
  return <Navigate replace to={`${to}${location.search}`} />;
}

function LegacyDoctorRedirect() {
  const { doctorSlug = "" } = useParams();
  const location = useLocation();
  return <Navigate replace to={`${DISCOVER_ROUTES.doctors.path}/${doctorSlug}${location.search}`} />;
}

function LegacyClinicRedirect() {
  const { clinicSlug = "" } = useParams();
  const location = useLocation();
  return <Navigate replace to={`${DISCOVER_ROUTES.clinics.path}/${clinicSlug}${location.search}`} />;
}

function LegacyHospitalRedirect() {
  const { hospitalSlug = "" } = useParams();
  const location = useLocation();
  return <Navigate replace to={`${DISCOVER_ROUTES.hospitals.path}/${hospitalSlug}${location.search}`} />;
}

function LegacySpecialityRedirect() {
  const { specialitySlug = "" } = useParams();
  const location = useLocation();
  return <Navigate replace to={`${DISCOVER_ROUTES.specialities.path}/${specialitySlug}${location.search}`} />;
}

function ProviderProtectedRoute() {
  const location = useLocation();
  const { status, error, refreshSession } = useProviderSession();

  if (status === "idle" || status === "loading") {
    return (
      <section className="page-section provider-dashboard-page">
        <div className="provider-dashboard-skeleton" role="status" aria-label="Restoring provider session">
          <span />
          <span />
          <span />
        </div>
      </section>
    );
  }

  if (status === "anonymous") {
    const returnTo = `${location.pathname}${location.search}`;
    return <Navigate replace to={`${DISCOVER_ROUTES.providerLogin.path}?returnTo=${encodeURIComponent(returnTo)}`} />;
  }

  if (status === "error") {
    return (
      <section className="page-section provider-dashboard-page">
        <DiscoverEmptyState
          icon="!"
          title="We could not restore your provider session"
          description={error ?? "Please try again."}
          primaryAction="Try again"
          primaryHref={window.location.href}
          secondaryAction="Provider login"
          secondaryTo={DISCOVER_ROUTES.providerLogin.path}
        />
      </section>
    );
  }

  return <Outlet />;
}

function ProviderEntryPage() {
  const location = useLocation();
  const { workspace } = useProviderSession();
  const addAnotherProfile = new URLSearchParams(location.search).get("mode") === "add";
  const supportedProviderTypes = workspace?.supportedProviderTypes?.length
    ? workspace.supportedProviderTypes
    : ["INDIVIDUAL_DOCTOR", "CLINIC", "HOSPITAL"];

  const stages = [
    ["Create account", "Start with basic contact and practice information."],
    ["Complete profile", "Add doctors, specialities, services and locations."],
    ["Submit for review", "Share your profile for Jeevanam review."],
    ["Publish", "Once approved, your profile can be published in Discover."],
  ] as const;
  const cards = [
    {
      title: "Individual Doctor",
      icon: "DR",
      body: "Create a professional profile for patients looking for your speciality.",
      details: "Best for independent doctors and consultants.",
      providerType: "INDIVIDUAL_DOCTOR",
      to: `${DISCOVER_ROUTES.registerDoctor.path}?intent=INDIVIDUAL_DOCTOR${addAnotherProfile ? "&mode=add" : ""}`,
    },
    {
      title: "Clinic",
      icon: "CL",
      body: "Present your clinic, doctor team, services, locations and appointment options.",
      details: "Best for outpatient practices and care centres.",
      providerType: "CLINIC",
      to: `${DISCOVER_ROUTES.registerClinic.path}?intent=CLINIC${addAnotherProfile ? "&mode=add" : ""}`,
    },
    {
      title: "Hospital",
      icon: "H",
      body: "Prepare a hospital presence with departments, facilities and doctor information.",
      details: "Best for hospitals and multi-speciality organisations.",
      providerType: "HOSPITAL",
      to: `${DISCOVER_ROUTES.registerHospital.path}?intent=HOSPITAL${addAnotherProfile ? "&mode=add" : ""}`,
    },
  ].filter((card) => supportedProviderTypes.includes(card.providerType as ProviderType));

  return (
    <section className="page-section">
      <div className="section-heading compact-page-hero">
        <span className="eyebrow">Provider registration</span>
        <h1>{addAnotherProfile ? "Add another profile" : "List your practice on Jeevanam Discover"}</h1>
        <p>{addAnotherProfile ? "Choose the provider type for the new profile you want to add." : "Create a public presence for your practice and help patients understand the care you offer."}</p>
      </div>
      <div className="provider-card-grid">
        {cards.map((card) => (
          <article className="provider-card onboarding-option-card" key={card.title}>
            <span className="onboarding-icon" aria-hidden="true">{card.icon}</span>
            <h2>{card.title}</h2>
            <p>{card.body}</p>
            <span>{card.details}</span>
            <Link className="primary-button" to={card.to}>
              {addAnotherProfile ? `Add ${card.title} profile` : `Start ${card.title.toLowerCase()} registration`}
            </Link>
          </article>
        ))}
      </div>
      <div className="onboarding-process" aria-label="Provider registration lifecycle">
        {stages.map(([stage, detail], index) => (
          <article key={stage}>
            <span>{index + 1}</span>
            <strong>{stage}</strong>
            <p>{detail}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function providerTypeForRegistrationPath(pathname: string): ProviderType | null {
  if (pathname === DISCOVER_ROUTES.registerDoctor.path) return "INDIVIDUAL_DOCTOR";
  if (pathname === DISCOVER_ROUTES.registerClinic.path) return "CLINIC";
  if (pathname === DISCOVER_ROUTES.registerHospital.path) return "HOSPITAL";
  return null;
}

function ProviderRegistrationStartPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { status } = useProviderSession();
  const providerType = providerTypeForRegistrationPath(location.pathname);
  const addAnotherProfile = new URLSearchParams(location.search).get("mode") === "add";
  const [bootstrapError, setBootstrapError] = useState<string | null>(null);
  const [bootstrapAttempt, setBootstrapAttempt] = useState(0);

  useEffect(() => {
    if (!providerType) {
      navigate(DISCOVER_ROUTES.listPractice.path, { replace: true });
      return;
    }
    if (status === "idle" || status === "loading") {
      return;
    }
    if (status === "anonymous") {
      const returnTo = `${location.pathname}${location.search}`;
      navigate(`${DISCOVER_ROUTES.providerLogin.path}?returnTo=${encodeURIComponent(returnTo)}`, { replace: true });
      return;
    }
    if (status !== "authenticated") {
      return;
    }
    let active = true;
    setBootstrapError(null);
    void startProviderApplication(providerType, addAnotherProfile)
      .then((result) => {
        if (!active) {
          return;
        }
        if (result.onboardingToken) {
          const tokenKey = "jeevanam.discover.providerOnboardingToken";
          localStorage.removeItem(`${tokenKey}.INDIVIDUAL_DOCTOR`);
          localStorage.removeItem(`${tokenKey}.CLINIC`);
          localStorage.removeItem(`${tokenKey}.HOSPITAL`);
          localStorage.setItem(tokenKey, result.onboardingToken);
          navigate(`/provider/onboarding/${result.applicationId}/${providerOnboardingStepRoute(result.currentStep)}`, { replace: true });
          return;
        }
        if (result.status === "PUBLISHED" && result.publicProfilePath) {
          navigate(DISCOVER_ROUTES.providerWorkspace.path, { replace: true });
          return;
        }
        navigate(DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(result.referenceNumber)), { replace: true });
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        const message = error instanceof ProviderAuthError
          ? error.message
          : error instanceof Error
            ? error.message
            : "Could not start your provider onboarding right now.";
        setBootstrapError(message || "Could not start your provider onboarding right now.");
      });
    return () => {
      active = false;
    };
  }, [addAnotherProfile, bootstrapAttempt, location.pathname, location.search, navigate, providerType, status]);

  if (bootstrapError) {
    return (
      <section className="page-section provider-dashboard-page">
        <div className="discover-empty-state is-compact provider-bootstrap-error" role="alert" aria-live="polite">
          <span className="empty-state-icon" aria-hidden="true">!</span>
          <strong>We could not start this provider application.</strong>
          <p>{bootstrapError}</p>
          <div className="cta-row">
            <button className="primary-button" type="button" onClick={() => setBootstrapAttempt((value) => value + 1)}>
              Try again
            </button>
            <Link className="secondary-button" to={DISCOVER_ROUTES.providerWorkspace.path}>
              Open provider workspace
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="page-section provider-dashboard-page">
      <div className="provider-dashboard-skeleton" role="status" aria-label="Preparing provider application">
        <span />
        <span />
        <span />
      </div>
    </section>
  );
}

function LoginChooserPage() {
  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Login</span>
        <h1>Choose your Jeevanam workspace.</h1>
        <p>Discover stays anonymous-first. Care access and healthcare team access open in their dedicated applications.</p>
      </div>
      <div className="login-grid">
        <article className="login-card">
          <h2>Patient Login</h2>
          <p>Personal care access, care history, documents, and family profile support.</p>
          <a className="primary-button" href={discoverConfig.careAppUrl} target="_blank" rel="noopener noreferrer">
            Open Jeevanam Care
          </a>
        </article>
        <article className="login-card">
          <h2>Clinic / Hospital Staff Login</h2>
          <p>Clinical and operational workspace for healthcare teams.</p>
          <a className="secondary-button" href={discoverConfig.healthcareAppUrl}>Open Jeevanam Healthcare</a>
        </article>
      </div>
    </section>
  );
}

function NotFoundPage() {
  return (
    <section className="page-section narrow-page">
      <span className="eyebrow">404</span>
      <h1>Page not found.</h1>
      <p>The page may have moved, or the address may be incorrect.</p>
      <Link className="primary-button" to={DISCOVER_ROUTES.home.path}>Return to Discover</Link>
    </section>
  );
}

function App() {
  return (
    <DiscoverErrorBoundary>
      <PublicLocationProvider>
        <ProviderSessionProvider>
          <Shell>
        <Routes>
          <Route path={DISCOVER_ROUTES.home.path} element={<PublicHomePage />} />
          <Route path={DISCOVER_ROUTES.doctors.path} element={<PublicDoctorsPage />} />
          <Route path="/doctors" element={<LegacyRedirect to={DISCOVER_ROUTES.doctors.path} />} />
          <Route path="/doctors/:doctorSlug" element={<LegacyDoctorRedirect />} />
          <Route path={`${DISCOVER_ROUTES.doctors.path}/:doctorSlug`} element={<PublicDoctorDetailPage />} />
          <Route path={DISCOVER_ROUTES.clinics.path} element={<PublicClinicsPage />} />
          <Route path="/clinics" element={<LegacyRedirect to={DISCOVER_ROUTES.clinics.path} />} />
          <Route path="/clinics/:clinicSlug" element={<LegacyClinicRedirect />} />
          <Route path={`${DISCOVER_ROUTES.clinics.path}/:clinicSlug`} element={<PublicClinicDetailPage />} />
          <Route path="/discover/clinics/:clinicSlug/home" element={<LandingPagePage param="clinicSlug" />} />
          <Route path={DISCOVER_ROUTES.hospitals.path} element={<PublicHospitalsPage />} />
          <Route path="/hospitals" element={<LegacyRedirect to={DISCOVER_ROUTES.hospitals.path} />} />
          <Route path="/hospitals/:hospitalSlug" element={<LegacyHospitalRedirect />} />
          <Route path={`${DISCOVER_ROUTES.hospitals.path}/:hospitalSlug`} element={<PublicHospitalDetailPage />} />
          <Route path="/discover/hospitals/:hospitalSlug/home" element={<LandingPagePage param="hospitalSlug" />} />
          <Route path="/discover/doctors/:doctorSlug/home" element={<LandingPagePage param="doctorSlug" />} />
          <Route path={DISCOVER_ROUTES.specialities.path} element={<PublicSpecialitiesPage />} />
          <Route path="/specialities" element={<LegacyRedirect to={DISCOVER_ROUTES.specialities.path} />} />
          <Route path="/specialities/:specialitySlug" element={<LegacySpecialityRedirect />} />
          <Route path={`${DISCOVER_ROUTES.specialities.path}/:specialitySlug`} element={<PublicSpecialityDetailPage />} />
          <Route path={DISCOVER_ROUTES.services.path} element={<ShellPage eyebrow="Services" title="Explore healthcare services" body="Start with doctor consultations, clinic appointments and speciality search while the service directory expands." stateIcon="＋" ctaLabel="Find doctors" ctaTo={DISCOVER_ROUTES.doctors.path} secondaryLabel="Browse specialities" secondaryTo={DISCOVER_ROUTES.specialities.path} />} />
          <Route path={DISCOVER_ROUTES.healthcare.path} element={<ShellPage eyebrow="Jeevanam Healthcare" title="Operations platform for clinics and hospitals." body="Jeevanam Healthcare supports reception, queue, EMR, care documentation, lab, pharmacy, revenue workflows, vaccination, and administration." ctaLabel="Clinic / Hospital Login" ctaTo={DISCOVER_ROUTES.login.path} />} />
          <Route path={DISCOVER_ROUTES.pricing.path} element={<ShellPage eyebrow="Plans and pricing" title="Plan information for healthcare providers" body="Choose the right path for your practice and connect with Jeevanam for current plan guidance." stateIcon="₹" ctaLabel="Contact Jeevanam" ctaTo={DISCOVER_ROUTES.contact.path} secondaryLabel="List your practice" secondaryTo={DISCOVER_ROUTES.listPractice.path} />} />
          <Route path={DISCOVER_ROUTES.listPractice.path} element={<ProviderEntryPage />} />
          <Route path={DISCOVER_ROUTES.registerDoctor.path} element={<ProviderRegistrationStartPage />} />
          <Route path={DISCOVER_ROUTES.registerClinic.path} element={<ProviderRegistrationStartPage />} />
          <Route path={DISCOVER_ROUTES.registerHospital.path} element={<ProviderRegistrationStartPage />} />
        <Route path={DISCOVER_ROUTES.providerLogin.path} element={<ProviderLoginPage />} />
        <Route element={<ProviderProtectedRoute />}>
          <Route path={DISCOVER_ROUTES.providerWorkspace.path} element={<ProviderWorkspacePage />} />
          <Route path={DISCOVER_ROUTES.providerApplications.path} element={<Navigate to={DISCOVER_ROUTES.providerWorkspace.path} replace />} />
          <Route path={DISCOVER_ROUTES.providerApplicationDashboard.path} element={<ProviderDashboardPage />} />
          <Route path={DISCOVER_ROUTES.providerLandingPage.path} element={<ProviderLandingPagePage />} />
          <Route path={DISCOVER_ROUTES.providerAccount.path} element={<ProviderWorkspacePage />} />
        </Route>
        <Route path="/provider/workspace" element={<Navigate to={DISCOVER_ROUTES.providerWorkspace.path} replace />} />
        <Route path="/provider/dashboard" element={<Navigate to={DISCOVER_ROUTES.providerWorkspace.path} replace />} />
        <Route path="/provider/landing-page" element={<Navigate to={DISCOVER_ROUTES.providerLandingPage.path} replace />} />
          <Route path="/provider/onboarding/:applicationId/:step" element={<ProviderOnboardingPage />} />
          <Route path={DISCOVER_ROUTES.login.path} element={<LoginChooserPage />} />
          <Route path={DISCOVER_ROUTES.about.path} element={<ShellPage eyebrow="About" title="About Jeevanam." body="Jeevanam connects public discovery, patient care access, and healthcare operations through focused applications." ctaLabel="Find Care" ctaTo={`${DISCOVER_ROUTES.home.path}#find-care`} />} />
          <Route path={DISCOVER_ROUTES.contact.path} element={<ShellPage eyebrow="Contact" title="Contact Jeevanam" body="Reach Jeevanam for provider enquiries, product demos and public support." stateIcon="@" ctaLabel="List your practice" ctaTo={DISCOVER_ROUTES.listPractice.path} />} />
          <Route path={DISCOVER_ROUTES.privacy.path} element={<ShellPage eyebrow="Privacy" title="Privacy" body="Jeevanam is preparing privacy information for patients, providers and public discovery visitors." stateIcon="◇" ctaLabel="Return home" ctaTo={DISCOVER_ROUTES.home.path} />} />
          <Route path={DISCOVER_ROUTES.terms.path} element={<ShellPage eyebrow="Terms" title="Terms" body="Jeevanam is preparing terms for public discovery, provider registration and appointment discovery." stateIcon="§" ctaLabel="Return home" ctaTo={DISCOVER_ROUTES.home.path} />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        </Shell>
        </ProviderSessionProvider>
      </PublicLocationProvider>
    </DiscoverErrorBoundary>
  );
}

export default App;
