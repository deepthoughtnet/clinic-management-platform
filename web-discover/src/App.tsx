import { Component, type ErrorInfo, type ReactNode, useEffect, useState } from "react";
import { Link, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { discoverBrand } from "./branding";
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
import { DISCOVER_ROUTES, footerRoutes, primaryNavigationRoutes } from "./routes";

type ShellPageProps = {
  eyebrow: string;
  title: string;
  body: string;
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
    description: "Review public-safe Jeevanam Healthcare plan information when published.",
  },
  [DISCOVER_ROUTES.listPractice.path]: {
    title: "List Your Practice | Jeevanam Discover",
    description: "Start provider registration for doctors, clinics, and hospitals.",
  },
  [DISCOVER_ROUTES.login.path]: {
    title: "Login | Jeevanam Discover",
    description: "Choose Jeevanam Care for patients or Jeevanam Healthcare for clinic and hospital teams.",
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
      <span className="brand-mark" aria-hidden="true">JD</span>
      <span>
        <strong>{discoverBrand.productName}</strong>
        <small>{discoverBrand.shortTagline}</small>
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
            <Link className="secondary-button" to={`${DISCOVER_ROUTES.home.path}#find-care`}>
              Find Care
            </Link>
            <Link className="primary-button" to={DISCOVER_ROUTES.listPractice.path}>
              List Your Practice
            </Link>
          </div>
        </div>
      </header>

      <main>{children}</main>

      <footer className="site-footer">
        <div className="footer-inner">
          <section className="footer-brand">
            <strong>{discoverBrand.productName}</strong>
            <p>{discoverBrand.tagline}</p>
          </section>
          <nav className="footer-links" aria-label="Discover footer navigation">
            {footerRoutes.map((route) => (
              <Link key={route.path} to={route.path}>
                {route.label}
              </Link>
            ))}
          </nav>
          <section className="footer-login-links" aria-label="Related Jeevanam applications">
            <a href={discoverConfig.careAppUrl}>Patient Login</a>
            <a href={discoverConfig.healthcareAppUrl}>Clinic / Hospital Login</a>
            {discoverConfig.aivaAppUrl ? <a href={discoverConfig.aivaAppUrl}>AIVA</a> : null}
          </section>
        </div>
      </footer>
    </div>
  );
}

function ShellPage({ eyebrow, title, body, ctaLabel, ctaTo, secondaryLabel, secondaryTo }: ShellPageProps) {
  return (
    <section className="page-section narrow-page">
      <span className="eyebrow">{eyebrow}</span>
      <h1>{title}</h1>
      <p>{body}</p>
      <div className="empty-state">
        <strong>Planned for the next implementation phase</strong>
        <span>This route is established now so navigation, deployment, and ownership can be validated before feature migration.</span>
      </div>
      <div className="cta-row">
        {ctaLabel && ctaTo ? <Link className="primary-button" to={ctaTo}>{ctaLabel}</Link> : null}
        {secondaryLabel && secondaryTo ? <Link className="secondary-button" to={secondaryTo}>{secondaryLabel}</Link> : null}
      </div>
    </section>
  );
}

function ProviderEntryPage() {
  const stages = ["Register", "Complete Profile", "Create Public Page", "Submit for Review", "Publish"];
  const cards = [
    {
      title: "Individual Doctor",
      body: "For independent doctors building a verified professional profile and public page.",
      details: "Profile, registration details, qualifications, specialities, fees, locations, timings, and biography.",
      to: DISCOVER_ROUTES.registerDoctor.path,
    },
    {
      title: "Clinic",
      body: "For outpatient practices that need a public organisation page and doctor directory.",
      details: "Business details, locations, doctors, services, timings, facilities, branding, gallery, and review submission.",
      to: DISCOVER_ROUTES.registerClinic.path,
    },
    {
      title: "Hospital",
      body: "For hospitals preparing public departments, facilities, doctors, and location information.",
      details: "Organisation details, accreditations, departments, facilities, emergency availability, branding, and gallery.",
      to: DISCOVER_ROUTES.registerHospital.path,
    },
  ];

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Provider registration</span>
        <h1>List your practice on Jeevanam Discover.</h1>
        <p>Start a governed provider application and prepare a public page for verification and publication.</p>
      </div>
      <div className="provider-card-grid">
        {cards.map((card) => (
          <article className="provider-card" key={card.title}>
            <h2>{card.title}</h2>
            <p>{card.body}</p>
            <span>{card.details}</span>
            <Link className="primary-button" to={card.to}>Start {card.title}</Link>
          </article>
        ))}
      </div>
      <div className="lifecycle-strip" aria-label="Provider registration lifecycle">
        {stages.map((stage, index) => (
          <span key={stage}>{index + 1}. {stage}</span>
        ))}
      </div>
    </section>
  );
}

function RegistrationPlaceholder({ type }: { type: "doctor" | "clinic" | "hospital" }) {
  const labels = {
    doctor: ["Doctor registration", "Prepare an individual professional profile for review."],
    clinic: ["Clinic registration", "Prepare an organisation profile, doctors, services, locations, and branding for review."],
    hospital: ["Hospital registration", "Prepare departments, facilities, doctors, emergency availability, and public page details for review."],
  };
  const [title, body] = labels[type];
  return (
    <ShellPage
      eyebrow="Provider onboarding"
      title={title}
      body={body}
      ctaLabel="Back to provider options"
      ctaTo={DISCOVER_ROUTES.listPractice.path}
      secondaryLabel="Book Demo"
      secondaryTo={DISCOVER_ROUTES.contact.path}
    />
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
          <Route path={DISCOVER_ROUTES.hospitals.path} element={<ShellPage eyebrow="Hospitals" title="Find hospitals by department and facility." body="Hospital discovery is reserved for public hospital listings and does not require hospital operations modules in this phase." ctaLabel="Register a Hospital" ctaTo={DISCOVER_ROUTES.registerHospital.path} />} />
          <Route path={DISCOVER_ROUTES.specialities.path} element={<PublicSpecialitiesPage />} />
          <Route path="/specialities/:specialitySlug" element={<PublicSpecialityDetailPage />} />
          <Route path={DISCOVER_ROUTES.services.path} element={<ShellPage eyebrow="Services" title="Discover healthcare services." body="Service discovery will cover consultations, diagnostics, vaccinations, pharmacy-linked services, and approved provider offerings." ctaLabel="Find Care" ctaTo={`${DISCOVER_ROUTES.home.path}#find-care`} />} />
          <Route path={DISCOVER_ROUTES.healthcare.path} element={<ShellPage eyebrow="Jeevanam Healthcare" title="Operations platform for clinics and hospitals." body="Jeevanam Healthcare supports reception, queue, EMR, care documentation, lab, pharmacy, revenue workflows, vaccination, and administration." ctaLabel="Clinic / Hospital Login" ctaTo={DISCOVER_ROUTES.login.path} />} />
          <Route path={DISCOVER_ROUTES.pricing.path} element={<ShellPage eyebrow="Plans and pricing" title="Plan information for healthcare providers." body="Public-safe pricing will use approved commercial plan projections after the pricing publication surface is ready." ctaLabel="Book Demo" ctaTo={DISCOVER_ROUTES.contact.path} secondaryLabel="List Your Practice" secondaryTo={DISCOVER_ROUTES.listPractice.path} />} />
          <Route path={DISCOVER_ROUTES.listPractice.path} element={<ProviderEntryPage />} />
          <Route path={DISCOVER_ROUTES.registerDoctor.path} element={<RegistrationPlaceholder type="doctor" />} />
          <Route path={DISCOVER_ROUTES.registerClinic.path} element={<RegistrationPlaceholder type="clinic" />} />
          <Route path={DISCOVER_ROUTES.registerHospital.path} element={<RegistrationPlaceholder type="hospital" />} />
          <Route path={DISCOVER_ROUTES.login.path} element={<LoginChooserPage />} />
          <Route path={DISCOVER_ROUTES.about.path} element={<ShellPage eyebrow="About" title="About Jeevanam." body="Jeevanam connects public discovery, patient care access, and healthcare operations through focused applications." ctaLabel="Find Care" ctaTo={`${DISCOVER_ROUTES.home.path}#find-care`} />} />
          <Route path={DISCOVER_ROUTES.contact.path} element={<ShellPage eyebrow="Contact" title="Contact Jeevanam." body="Use this route for provider enquiries, product demos, and public support. Lead capture will be added in a later Discover phase." ctaLabel="List Your Practice" ctaTo={DISCOVER_ROUTES.listPractice.path} />} />
          <Route path={DISCOVER_ROUTES.privacy.path} element={<ShellPage eyebrow="Privacy" title="Privacy." body="Approved privacy content will be published here before public launch." ctaLabel="Return home" ctaTo={DISCOVER_ROUTES.home.path} />} />
          <Route path={DISCOVER_ROUTES.terms.path} element={<ShellPage eyebrow="Terms" title="Terms." body="Approved terms for public discovery, provider registration, and booking initiation will be published here before public launch." ctaLabel="Return home" ctaTo={DISCOVER_ROUTES.home.path} />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        </Shell>
      </PublicLocationProvider>
    </DiscoverErrorBoundary>
  );
}

export default App;
