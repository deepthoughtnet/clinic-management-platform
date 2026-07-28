import { Component, type ErrorInfo, type ReactNode, useEffect, useState } from "react";
import { Link, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { discoverBrand } from "./branding";
import { DiscoverEmptyState } from "./components/DiscoveryComponents";
import { discoverConfig } from "./config";
import { PublicLocationProvider } from "./context/PublicLocationContext";
import {
  PublicClinicDetailPage,
  PublicClinicsPage,
  PublicDoctorDetailPage,
  PublicDoctorsPage,
  PublicHomePage,
  PublicSpecialitiesPage,
  PublicSpecialityDetailPage,
} from "./pages/discovery/PublicDiscoveryPages";
import { ProviderDashboardPage } from "./pages/provider/ProviderDashboardPage";
import { ProviderOnboardingPage } from "./pages/provider/ProviderOnboardingPage";
import { DISCOVER_ROUTES, primaryNavigationRoutes } from "./routes";

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
  [DISCOVER_ROUTES.providerDashboard.path]: {
    title: "Provider Dashboard | Jeevanam Discover",
    description: "Continue your provider onboarding, review status, and submit your public profile.",
  },
};

function updateDocumentMetadata(pathname: string) {
  const meta = routeMeta[pathname] ?? {
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

function Shell({ children }: { children: ReactNode }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();

  useEffect(() => {
    setMenuOpen(false);
    updateDocumentMetadata(location.pathname);
  }, [location.pathname]);

  return (
    <div className="discover-shell">
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
              <NavLink key={route.path} to={route.path} className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
                {route.label}
              </NavLink>
            ))}
          </nav>
          <div className="header-actions">
            <a className="ghost-button" href={discoverConfig.careAppUrl}>
              Patient Login
            </a>
            <Link className="primary-button" to={DISCOVER_ROUTES.listPractice.path}>
              For Providers
            </Link>
          </div>
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
            <Link to={DISCOVER_ROUTES.specialities.path}>Browse specialities</Link>
            <a href={discoverConfig.careAppUrl}>Patient login</a>
          </nav>
          <nav className="footer-column" aria-label="Providers">
            <strong>Providers</strong>
            <Link to={DISCOVER_ROUTES.listPractice.path}>Register practice</Link>
            <Link to={DISCOVER_ROUTES.healthcare.path}>Provider information</Link>
            <Link to={DISCOVER_ROUTES.pricing.path}>Pricing</Link>
            <a href={discoverConfig.healthcareAppUrl}>Clinic / Hospital login</a>
          </nav>
          <nav className="footer-column" aria-label="Support">
            <strong>Support</strong>
            <Link to={DISCOVER_ROUTES.about.path}>About</Link>
            <Link to={DISCOVER_ROUTES.contact.path}>Contact</Link>
            <Link to={DISCOVER_ROUTES.privacy.path}>Privacy</Link>
            <Link to={DISCOVER_ROUTES.terms.path}>Terms</Link>
          </nav>
          <section className="footer-bottom">
            <span>© {new Date().getFullYear()} Jeevanam. Public healthcare discovery for patients and providers.</span>
            <div>
              <Link to={DISCOVER_ROUTES.privacy.path}>Privacy</Link>
              <Link to={DISCOVER_ROUTES.terms.path}>Terms</Link>
            </div>
          </section>
        </div>
      </footer>
    </div>
  );
}

function ShellPage({ eyebrow, title, body, stateIcon, stateTitle, stateBody, ctaLabel, ctaTo, secondaryLabel, secondaryTo }: ShellPageProps) {
  return (
    <section className="page-section compact-page">
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

function ProviderEntryPage() {
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
      to: DISCOVER_ROUTES.registerDoctor.path,
    },
    {
      title: "Clinic",
      icon: "CL",
      body: "Present your clinic, doctor team, services, locations and appointment options.",
      details: "Best for outpatient practices and care centres.",
      to: DISCOVER_ROUTES.registerClinic.path,
    },
    {
      title: "Hospital",
      icon: "H",
      body: "Prepare a hospital presence with departments, facilities and doctor information.",
      details: "Best for hospitals and multi-speciality organisations.",
      to: DISCOVER_ROUTES.registerHospital.path,
    },
  ];

  return (
    <section className="page-section">
      <div className="section-heading compact-page-hero">
        <span className="eyebrow">Provider registration</span>
        <h1>List your practice on Jeevanam Discover</h1>
        <p>Create a public presence for your practice and help patients understand the care you offer.</p>
      </div>
      <div className="provider-card-grid">
        {cards.map((card) => (
          <article className="provider-card onboarding-option-card" key={card.title}>
            <span className="onboarding-icon" aria-hidden="true">{card.icon}</span>
            <h2>{card.title}</h2>
            <p>{card.body}</p>
            <span>{card.details}</span>
            <Link className="primary-button" to={card.to}>Start {card.title.toLowerCase()} registration</Link>
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
          <a className="primary-button" href={discoverConfig.careAppUrl}>Open Jeevanam Care</a>
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
        <Shell>
          <Routes>
          <Route path={DISCOVER_ROUTES.home.path} element={<PublicHomePage />} />
          <Route path={DISCOVER_ROUTES.doctors.path} element={<PublicDoctorsPage />} />
          <Route path="/doctors/:doctorSlug" element={<PublicDoctorDetailPage />} />
          <Route path={DISCOVER_ROUTES.clinics.path} element={<PublicClinicsPage />} />
          <Route path="/clinics/:clinicSlug" element={<PublicClinicDetailPage />} />
          <Route path={DISCOVER_ROUTES.hospitals.path} element={<ShellPage eyebrow="Hospitals" title="Hospital discovery is coming to Jeevanam Discover" body="Hospitals will be able to publish departments, facilities, doctors and appointment information in a future release." stateIcon="H" ctaLabel="Explore clinics" ctaTo={DISCOVER_ROUTES.clinics.path} secondaryLabel="Register a hospital" secondaryTo={DISCOVER_ROUTES.registerHospital.path} />} />
          <Route path={DISCOVER_ROUTES.specialities.path} element={<PublicSpecialitiesPage />} />
          <Route path="/specialities/:specialitySlug" element={<PublicSpecialityDetailPage />} />
          <Route path={DISCOVER_ROUTES.services.path} element={<ShellPage eyebrow="Services" title="Explore healthcare services" body="Start with doctor consultations, clinic appointments and speciality search while the service directory expands." stateIcon="＋" ctaLabel="Find doctors" ctaTo={DISCOVER_ROUTES.doctors.path} secondaryLabel="Browse specialities" secondaryTo={DISCOVER_ROUTES.specialities.path} />} />
          <Route path={DISCOVER_ROUTES.healthcare.path} element={<ShellPage eyebrow="Jeevanam Healthcare" title="Operations platform for clinics and hospitals." body="Jeevanam Healthcare supports reception, queue, EMR, care documentation, lab, pharmacy, revenue workflows, vaccination, and administration." ctaLabel="Clinic / Hospital Login" ctaTo={DISCOVER_ROUTES.login.path} />} />
          <Route path={DISCOVER_ROUTES.pricing.path} element={<ShellPage eyebrow="Plans and pricing" title="Plan information for healthcare providers" body="Choose the right path for your practice and connect with Jeevanam for current plan guidance." stateIcon="₹" ctaLabel="Contact Jeevanam" ctaTo={DISCOVER_ROUTES.contact.path} secondaryLabel="List your practice" secondaryTo={DISCOVER_ROUTES.listPractice.path} />} />
          <Route path={DISCOVER_ROUTES.listPractice.path} element={<ProviderEntryPage />} />
          <Route path={DISCOVER_ROUTES.registerDoctor.path} element={<ProviderOnboardingPage type="doctor" />} />
          <Route path={DISCOVER_ROUTES.registerClinic.path} element={<ProviderOnboardingPage type="clinic" />} />
          <Route path={DISCOVER_ROUTES.registerHospital.path} element={<ProviderOnboardingPage type="hospital" />} />
          <Route path={DISCOVER_ROUTES.providerDashboard.path} element={<ProviderDashboardPage />} />
          <Route path="/provider/onboarding/:applicationId/:step" element={<ProviderOnboardingPage />} />
          <Route path={DISCOVER_ROUTES.login.path} element={<LoginChooserPage />} />
          <Route path={DISCOVER_ROUTES.about.path} element={<ShellPage eyebrow="About" title="About Jeevanam." body="Jeevanam connects public discovery, patient care access, and healthcare operations through focused applications." ctaLabel="Find Care" ctaTo={`${DISCOVER_ROUTES.home.path}#find-care`} />} />
          <Route path={DISCOVER_ROUTES.contact.path} element={<ShellPage eyebrow="Contact" title="Contact Jeevanam" body="Reach Jeevanam for provider enquiries, product demos and public support." stateIcon="@" ctaLabel="List your practice" ctaTo={DISCOVER_ROUTES.listPractice.path} />} />
          <Route path={DISCOVER_ROUTES.privacy.path} element={<ShellPage eyebrow="Privacy" title="Privacy" body="Jeevanam is preparing privacy information for patients, providers and public discovery visitors." stateIcon="◇" ctaLabel="Return home" ctaTo={DISCOVER_ROUTES.home.path} />} />
          <Route path={DISCOVER_ROUTES.terms.path} element={<ShellPage eyebrow="Terms" title="Terms" body="Jeevanam is preparing terms for public discovery, provider registration and appointment discovery." stateIcon="§" ctaLabel="Return home" ctaTo={DISCOVER_ROUTES.home.path} />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        </Shell>
      </PublicLocationProvider>
    </DiscoverErrorBoundary>
  );
}

export default App;
