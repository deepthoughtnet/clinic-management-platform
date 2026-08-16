import { type FormEvent, type ReactNode, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { discoverConfig } from "../../config";
import { DISCOVER_ROUTES } from "../../routes";

const DISCOVER_SUPPORT_EMAIL = "support@jeevanam.health";

function InfoCard({
  title,
  body,
  action,
  to,
  href,
}: {
  title: string;
  body: string;
  action: string;
  to?: string;
  href?: string;
}) {
  return (
    <article className="public-info-card">
      <strong>{title}</strong>
      <p>{body}</p>
      {href ? (
        <a className="text-button" href={href} target="_blank" rel="noopener noreferrer">
          {action}
        </a>
      ) : (
        <Link className="text-button" to={to ?? DISCOVER_ROUTES.home.path}>
          {action}
        </Link>
      )}
    </article>
  );
}

function PublicInfoShell({
  eyebrow,
  title,
  body,
  children,
}: {
  eyebrow: string;
  title: string;
  body: string;
  children: ReactNode;
}) {
  return (
    <section className="page-section public-info-page">
      <div className="section-heading compact-page-hero">
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{body}</p>
      </div>
      <div className="public-info-grid">{children}</div>
    </section>
  );
}

export function HelpPage() {
  return (
    <PublicInfoShell
      eyebrow="Help"
      title="Help and support"
      body="Find the right Jeevanam entry point for patients, providers, and healthcare teams."
    >
      <InfoCard
        title="Find care"
        body="Use Discover to compare doctors, clinics, hospitals, and specialities."
        action="Browse Discover"
        to={DISCOVER_ROUTES.home.path}
      />
      <InfoCard
        title="Patient care"
        body="Jeevanam Care is the dedicated patient workspace for appointments, records, and follow-up."
        action="Open Care"
        href={discoverConfig.careAppUrl}
      />
      <InfoCard
        title="Provider workspace"
        body="Connect and manage your provider profile, publication workflow, and controlled access."
        action="Provider login"
        to={DISCOVER_ROUTES.providerLogin.path}
      />
      <InfoCard
        title="Need more help?"
        body={`Send an enquiry to ${DISCOVER_SUPPORT_EMAIL} or use the contact page for product and support questions.`}
        action="Contact us"
        to={DISCOVER_ROUTES.contact.path}
      />
    </PublicInfoShell>
  );
}

export function AccessibilityPage() {
  return (
    <PublicInfoShell
      eyebrow="Accessibility"
      title="Accessibility at Jeevanam Discover"
      body="We design public discovery pages to remain usable on keyboard, touch, and small screens."
    >
      <article className="public-info-panel public-info-panel--wide">
        <strong>What we support</strong>
        <ul className="public-info-list">
          <li>Keyboard-accessible navigation, buttons, and menus.</li>
          <li>Responsive layouts that reflow from desktop to mobile.</li>
          <li>Visible focus states and readable contrast.</li>
          <li>Content that remains usable without motion or hover-only interactions.</li>
        </ul>
      </article>
      <article className="public-info-panel public-info-panel--wide">
        <strong>Need help with accessibility?</strong>
        <p>
          If you spot a problem, use the contact page and describe the route, browser, and what happened. That helps us reproduce and fix it quickly.
        </p>
        <div className="cta-row">
          <Link className="primary-button" to={DISCOVER_ROUTES.contact.path}>
            Contact support
          </Link>
          <Link className="secondary-button" to={DISCOVER_ROUTES.home.path}>
            Return home
          </Link>
        </div>
      </article>
    </PublicInfoShell>
  );
}

export function SitemapPage() {
  const sections = useMemo(
    () => [
      {
        title: "Discover",
        links: [
          { label: "Home", to: DISCOVER_ROUTES.home.path },
          { label: "Doctors", to: DISCOVER_ROUTES.doctors.path },
          { label: "Clinics", to: DISCOVER_ROUTES.clinics.path },
          { label: "Hospitals", to: DISCOVER_ROUTES.hospitals.path },
          { label: "Specialities", to: DISCOVER_ROUTES.specialities.path },
        ],
      },
      {
        title: "Provider",
        links: [
          { label: "Provider login", to: DISCOVER_ROUTES.providerLogin.path },
          { label: "Request provider access", to: DISCOVER_ROUTES.providerRequestAccess.path },
          { label: "List your practice", to: DISCOVER_ROUTES.listPractice.path },
          { label: "Provider workspace", to: DISCOVER_ROUTES.providerWorkspace.path },
        ],
      },
      {
        title: "Information",
        links: [
          { label: "Help", to: DISCOVER_ROUTES.help.path },
          { label: "Accessibility", to: DISCOVER_ROUTES.accessibility.path },
          { label: "Contact", to: DISCOVER_ROUTES.contact.path },
          { label: "Privacy", to: DISCOVER_ROUTES.privacy.path },
          { label: "Terms", to: DISCOVER_ROUTES.terms.path },
          { label: "Security", to: DISCOVER_ROUTES.security.path },
          { label: "Cookies", to: DISCOVER_ROUTES.cookies.path },
        ],
      },
    ],
    [],
  );

  return (
    <PublicInfoShell
      eyebrow="Sitemap"
      title="Jeevanam Discover sitemap"
      body="Quickly move between the public Discover pages and the provider entry points."
    >
      {sections.map((section) => (
        <article className="public-info-panel" key={section.title}>
          <strong>{section.title}</strong>
          <div className="public-sitemap-links">
            {section.links.map((link) => (
              <Link key={link.to} className="text-button" to={link.to}>
                {link.label}
              </Link>
            ))}
          </div>
        </article>
      ))}
    </PublicInfoShell>
  );
}

function PolicyPage({
  eyebrow,
  title,
  body,
  points,
}: {
  eyebrow: string;
  title: string;
  body: string;
  points: string[];
}) {
  return (
    <PublicInfoShell eyebrow={eyebrow} title={title} body={body}>
      <article className="public-info-panel public-info-panel--wide">
        <strong>{title}</strong>
        <div className="public-info-copy">
          {points.map((point) => (
            <p key={point}>{point}</p>
          ))}
        </div>
        <div className="cta-row">
          <Link className="secondary-button" to={DISCOVER_ROUTES.contact.path}>
            Contact us
          </Link>
          <Link className="text-button" to={DISCOVER_ROUTES.home.path}>
            Return home
          </Link>
        </div>
      </article>
    </PublicInfoShell>
  );
}

export function PrivacyPage() {
  return (
    <PolicyPage
      eyebrow="Privacy"
      title="Privacy"
      body="Jeevanam Discover exposes published public information while keeping account and workspace data separated."
      points={[
        "Public provider pages use published profile data only.",
        "Location preferences are used to personalise search results.",
        "Authentication and provider workspace data stay in their respective applications.",
      ]}
    />
  );
}

export function TermsPage() {
  return (
    <PolicyPage
      eyebrow="Terms"
      title="Terms"
      body="These pages explain how Discover is intended to be used for public healthcare search and provider entry."
      points={[
        "Use Discover to browse public provider information and supported entry points.",
        "Provider actions remain subject to workspace, verification, and moderation flows.",
        "If content differs from the live public profile, the published profile is the source of truth.",
      ]}
    />
  );
}

export function SecurityPage() {
  return (
    <PolicyPage
      eyebrow="Security"
      title="Security"
      body="Discover keeps public browsing separate from provider and healthcare workspaces."
      points={[
        "Provider and patient sign-in routes remain in their dedicated applications.",
        "Public pages do not expose protected workspace data.",
        "Authorization and tenant-scoped workflows continue to live behind the correct application boundaries.",
      ]}
    />
  );
}

export function CookiesPage() {
  return (
    <PolicyPage
      eyebrow="Cookies"
      title="Cookies"
      body="Discover uses browser storage for a few lightweight preferences that improve the public experience."
      points={[
        "Location preferences remember your selected city or current-location mode.",
        "The app may store UI choices such as search context for a smoother return visit.",
        "No clinical or provider workflow data is stored in public browsing cookies.",
      ]}
    />
  );
}

export function ContactPage() {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [topic, setTopic] = useState("Public support");
  const [message, setMessage] = useState("");
  const [status, setStatus] = useState<string | null>(null);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const subject = encodeURIComponent(`[Jeevanam Discover] ${topic}`);
    const body = encodeURIComponent(
      `Name: ${fullName.trim() || "Not provided"}\nEmail: ${email.trim() || "Not provided"}\nTopic: ${topic}\n\n${message.trim() || "No message provided."}`,
    );
    setStatus("Opening your email app with the enquiry details.");
    if (typeof window !== "undefined") {
      window.location.href = `mailto:${DISCOVER_SUPPORT_EMAIL}?subject=${subject}&body=${body}`;
    }
  }

  return (
    <section className="page-section public-info-page">
      <div className="section-heading compact-page-hero">
        <span className="eyebrow">Contact</span>
        <h1>Contact Jeevanam</h1>
        <p>Use the form below to prepare a support or product enquiry, or use the visible support channel if you prefer email.</p>
      </div>

      <div className="public-info-grid public-info-grid--contact">
        <article className="public-info-panel public-info-panel--wide">
          <strong>Support channel</strong>
          <p>
            Email <a href={`mailto:${DISCOVER_SUPPORT_EMAIL}`}>{DISCOVER_SUPPORT_EMAIL}</a> for help with public Discover browsing, product demos, or support questions.
          </p>
          <p>
            If you are a provider, use <Link to={DISCOVER_ROUTES.listPractice.path}>List your practice</Link> instead of this contact form.
          </p>
        </article>

        <article className="public-info-panel public-info-panel--wide">
          <form className="public-contact-form" onSubmit={submit}>
            <label>
              <span>Your name</span>
              <input value={fullName} onChange={(event) => setFullName(event.target.value)} autoComplete="name" placeholder="Your name" />
            </label>
            <label>
              <span>Email address</span>
              <input value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" placeholder="you@example.com" />
            </label>
            <label>
              <span>Topic</span>
              <select value={topic} onChange={(event) => setTopic(event.target.value)}>
                <option>Public support</option>
                <option>Product demo</option>
                <option>Provider enquiry</option>
                <option>Accessibility feedback</option>
              </select>
            </label>
            <label>
              <span>Message</span>
              <textarea rows={5} value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Tell us how we can help." />
            </label>
            <div className="cta-row">
              <button className="primary-button" type="submit">
                Send enquiry
              </button>
              <Link className="secondary-button" to={DISCOVER_ROUTES.help.path}>
                Browse help
              </Link>
            </div>
            {status ? <p className="form-note" role="status">{status}</p> : null}
          </form>
        </article>
      </div>
    </section>
  );
}
