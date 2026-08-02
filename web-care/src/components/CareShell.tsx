import { type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { Link, NavLink } from "react-router-dom";
import {
  ArrowBackRounded,
  AutoAwesomeOutlined,
  CalendarMonthOutlined,
  CheckCircleOutlined,
  ContactSupportOutlined,
  AssignmentTurnedInOutlined,
  NotificationsOutlined,
  ScienceOutlined,
  DashboardOutlined,
  HelpOutlineOutlined,
  LogoutOutlined,
  LockOutlined,
  MenuOpenOutlined,
  PersonOutlined,
  SearchOutlined,
  ShieldOutlined,
} from "@mui/icons-material";
import type { PatientPortalSession } from "../api/patientPortal";
import { branding, productAndTagline } from "../branding";
import { careConfig } from "../config";

type CareBenefit = {
  icon: ReactNode;
  title: string;
  description: string;
};

type CareLoginHeroProps = {
  title: string;
  subtitle: string;
  benefits: CareBenefit[];
};

export type CareDashboardBranding = {
  name: string;
  tagline: string;
  initials: string;
};

function CareLoginIllustration() {
  return (
    <div className="care-login-illustration" aria-hidden="true">
      <div className="care-login-illustration__glow care-login-illustration__glow--left" />
      <div className="care-login-illustration__glow care-login-illustration__glow--right" />
      <div className="care-login-illustration__scene">
        <div className="care-login-illustration__panel care-login-illustration__panel--summary">
          <span className="care-login-illustration__eyebrow">
            <ShieldOutlined fontSize="small" aria-hidden="true" />
            Secure access
          </span>
          <strong>Everything in one place</strong>
          <p>Appointments, prescriptions, reports, vaccination records and AIVA.</p>
        </div>

        <div className="care-login-illustration__device">
          <div className="care-login-illustration__device-top">
            <span className="care-login-illustration__device-dot" />
            <span className="care-login-illustration__device-title">Jeevanam Care</span>
            <span className="care-login-illustration__device-dot" />
          </div>

          <div className="care-login-illustration__screen">
            <div className="care-login-illustration__screen-header">
              <span className="care-login-illustration__screen-kicker">Today</span>
              <span className="care-login-illustration__screen-status">
                <LockOutlined fontSize="inherit" aria-hidden="true" />
                Protected
              </span>
            </div>

            <div className="care-login-illustration__appointment">
              <CalendarMonthOutlined fontSize="small" aria-hidden="true" />
              <div>
                <strong>Upcoming appointment</strong>
                <span>Today · 5:30 PM</span>
              </div>
            </div>

            <div className="care-login-illustration__cards">
              <article className="care-login-illustration__card care-login-illustration__card--accent">
                <AssignmentTurnedInOutlined fontSize="small" aria-hidden="true" />
                <strong>Prescription ready</strong>
                <span>Review medicines and visit notes.</span>
              </article>
              <article className="care-login-illustration__card">
                <ScienceOutlined fontSize="small" aria-hidden="true" />
                <strong>Lab report</strong>
                <span>Secure results from your care team.</span>
              </article>
              <article className="care-login-illustration__card">
                <ShieldOutlined fontSize="small" aria-hidden="true" />
                <strong>Vaccination record</strong>
                <span>Essential immunisations at hand.</span>
              </article>
              <article className="care-login-illustration__card care-login-illustration__card--assistant">
                <AutoAwesomeOutlined fontSize="small" aria-hidden="true" />
                <strong>AIVA assistant</strong>
                <span>Find the next care step quickly.</span>
              </article>
            </div>
          </div>
        </div>

        <div className="care-login-illustration__side-stack">
          <div className="care-login-illustration__mini-panel care-login-illustration__mini-panel--clinic">
            <CheckCircleOutlined fontSize="small" aria-hidden="true" />
            <div>
              <strong>Private access</strong>
              <span>Verified patients only</span>
            </div>
          </div>
          <div className="care-login-illustration__mini-panel care-login-illustration__mini-panel--report">
            <ContactSupportOutlined fontSize="small" aria-hidden="true" />
            <div>
              <strong>Records available</strong>
              <span>Care updates, reports and billing</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

const authenticatedNavItems = [
  { to: "/patient/notifications", label: "Notifications", icon: <NotificationsOutlined fontSize="small" aria-hidden="true" /> },
  { to: "/patient/careai", label: "AIVA", icon: <AutoAwesomeOutlined fontSize="small" aria-hidden="true" /> },
];

const patientProfileLinks = [
  { to: "/patient/profile", label: "My Profile", icon: <PersonOutlined fontSize="small" aria-hidden="true" /> },
  { to: "/help-centre", label: "Help", icon: <HelpOutlineOutlined fontSize="small" aria-hidden="true" /> },
];

const footerCareLinks = [
  { to: "/patient/dashboard", label: "Dashboard", icon: <DashboardOutlined fontSize="small" aria-hidden="true" /> },
  { to: "/patient/appointments", label: "Appointments", icon: <CalendarMonthOutlined fontSize="small" aria-hidden="true" /> },
  { to: "/patient/profile", label: "Profile", icon: <PersonOutlined fontSize="small" aria-hidden="true" /> },
];

const footerSupportLinks = [
  { to: "/contact", label: "Contact" },
  { to: "/help-centre", label: "Help Centre" },
  { to: "/privacy-policy", label: "Privacy" },
  { to: "/terms", label: "Terms" },
];

function patientInitials(label: string | null | undefined) {
  const value = label?.trim() || "Patient";
  return value
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

export function formatPatientDisplayName(value: string | null | undefined) {
  const raw = value?.trim() || "Patient";
  return raw
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => {
      if (!part) {
        return part;
      }
      if (part === part.toLowerCase() || part === part.toUpperCase()) {
        return part.charAt(0).toUpperCase() + part.slice(1).toLowerCase();
      }
      return part;
    })
    .join(" ");
}

function maskPhoneNumber(value: string | null | undefined) {
  if (!value) {
    return "Phone unavailable";
  }
  const digits = value.replace(/\D/g, "");
  if (digits.length < 4) {
    return "Phone available";
  }
  return `Mobile ending ${digits.slice(-4)}`;
}

export function CareBenefitItem({ icon, title, description }: CareBenefit) {
  return (
    <article className="care-benefit-item">
      <div className="care-benefit-item__icon" aria-hidden="true">
        {icon}
      </div>
      <strong>{title}</strong>
      <p>{description}</p>
    </article>
  );
}

export function CareLoginHero({ title, subtitle, benefits }: CareLoginHeroProps) {
  return (
    <section className="care-login-hero" aria-labelledby="care-login-hero-title">
      <span className="eyebrow">Jeevanam Care</span>
      <h1 id="care-login-hero-title">{title}</h1>
      <p>{subtitle}</p>
      <CareLoginIllustration />
      <div className="care-login-hero__benefits" aria-label="Care benefits">
        {benefits.map((item) => (
          <CareBenefitItem key={item.title} {...item} />
        ))}
      </div>
    </section>
  );
}

export function CarePublicEntryHeader() {
  return (
    <header className="care-public-entry-header" aria-label="Jeevanam Care entry header">
      <Link to="/patient/login" className="care-brand-lockup">
        <span className="care-brand-mark" aria-hidden="true">
          <span>JC</span>
        </span>
        <span className="care-brand-copy">
          <strong>Jeevanam Care</strong>
          <small>Secure Patient Portal</small>
        </span>
      </Link>
      <div className="care-public-entry-header__actions">
        <span className="care-entry-pill">Secure patient access</span>
        <a className="secondary-button" href={careConfig.discoverAppUrl}>
          <ArrowBackRounded fontSize="small" aria-hidden="true" />
          Back to Discover
        </a>
      </div>
    </header>
  );
}

export function PatientProfileMenu({
  session,
  patientDisplayName,
  onSignOut,
}: {
  session: PatientPortalSession;
  patientDisplayName?: string | null;
  onSignOut: () => void;
}) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const displayName = useMemo(
    () => formatPatientDisplayName(patientDisplayName ?? session.patientLabel),
    [patientDisplayName, session.patientLabel],
  );
  const initials = useMemo(() => patientInitials(displayName), [displayName]);

  useEffect(() => {
    if (!open) {
      return;
    }

    function handleDocumentKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }

    function handleDocumentClick(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    document.addEventListener("keydown", handleDocumentKeyDown);
    document.addEventListener("mousedown", handleDocumentClick);
    return () => {
      document.removeEventListener("keydown", handleDocumentKeyDown);
      document.removeEventListener("mousedown", handleDocumentClick);
    };
  }, [open]);

  return (
    <div className="care-profile-menu" ref={menuRef}>
      <button
        className="care-profile-menu__trigger"
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="care-profile-menu__avatar" aria-hidden="true">
          {initials}
        </span>
        <span className="care-profile-menu__copy">
          <strong>{displayName}</strong>
          <small>{maskPhoneNumber(session.phone)}</small>
        </span>
        <MenuOpenOutlined fontSize="small" aria-hidden="true" />
      </button>
      {open ? (
        <div className="care-profile-menu__panel" role="menu" aria-label="Patient profile menu">
          {patientProfileLinks.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className="care-profile-menu__item"
              role="menuitem"
              onClick={() => setOpen(false)}
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
          <button className="care-profile-menu__item care-profile-menu__item--danger" type="button" onClick={onSignOut}>
            <span aria-hidden="true">
              <LogoutOutlined fontSize="small" />
            </span>
            Sign out
          </button>
        </div>
      ) : null}
    </div>
  );
}

export function CareAuthenticatedHeader({
  session,
  patientDisplayName,
  unreadNotificationCount = 0,
  onSignOut,
}: {
  session: PatientPortalSession;
  patientDisplayName?: string | null;
  unreadNotificationCount?: number;
  onSignOut: () => void;
}) {
  return (
    <header className="care-authenticated-header" aria-label="Jeevanam Care header">
      <div className="care-authenticated-header__brand-row">
        <Link to="/patient/dashboard" className="care-brand-lockup">
          <span className="care-brand-mark" aria-hidden="true">
            <span>JC</span>
          </span>
          <span className="care-brand-copy">
            <strong>{branding.productName}</strong>
            <small>Authenticated care portal</small>
          </span>
        </Link>
        <div className="care-authenticated-header__mobile-actions">
          <a className="secondary-button" href={careConfig.discoverAppUrl}>
            Find Care
          </a>
        </div>
      </div>
      <nav className="care-authenticated-header__nav" aria-label="Patient shortcuts">
        <a className="secondary-button" href={careConfig.discoverAppUrl}>
          <SearchOutlined fontSize="small" aria-hidden="true" />
          Find Care
        </a>
        {authenticatedNavItems.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => `care-authenticated-header__link${isActive ? " is-active" : ""}`}>
            <span aria-hidden="true">{item.icon}</span>
            <span className="care-authenticated-header__link-copy">
              {item.label}
              {item.label === "Notifications" && unreadNotificationCount > 0 ? (
                <span className="care-authenticated-header__badge" aria-label={`${unreadNotificationCount} unread notifications`}>
                  {unreadNotificationCount}
                </span>
              ) : null}
            </span>
          </NavLink>
        ))}
        <PatientProfileMenu session={session} patientDisplayName={patientDisplayName} onSignOut={onSignOut} />
      </nav>
    </header>
  );
}

export function CareFooter({ authenticated = false }: { authenticated?: boolean }) {
  return (
    <footer className="care-footer">
      <div className="care-footer__grid">
        <section className="care-footer__brand">
          <span className="eyebrow">Jeevanam Care</span>
          <strong>{branding.productName}</strong>
          <p>{productAndTagline()}</p>
        </section>
        {authenticated ? (
          <section className="care-footer__column">
            <strong>Care</strong>
            <div className="care-footer__links">
              {footerCareLinks.map((item) => (
                <Link key={item.to} to={item.to}>
                  <span aria-hidden="true">{item.icon}</span>
                  {item.label}
                </Link>
              ))}
            </div>
          </section>
        ) : null}
        <section className="care-footer__column">
          <strong>Explore</strong>
          <div className="care-footer__links">
            <a href={careConfig.discoverAppUrl}>Find Care</a>
          </div>
        </section>
        <section className="care-footer__column">
          <strong>Support</strong>
          <div className="care-footer__links">
            {footerSupportLinks.map((item) => (
              <Link key={item.to} to={item.to}>
                {item.label}
              </Link>
            ))}
          </div>
        </section>
      </div>
    </footer>
  );
}

export function CareEntrySessionNotice({
  title,
  body,
  actionLabel,
  onAction,
}: {
  title: string;
  body: string;
  actionLabel: string;
  onAction: () => void;
}) {
  return (
    <div className="care-session-notice">
      <div>
        <strong>{title}</strong>
        <p>{body}</p>
      </div>
      <button className="secondary-button" type="button" onClick={onAction}>
        {actionLabel}
      </button>
    </div>
  );
}

export function CareEntrySecurityStrip() {
  return (
    <div className="care-entry-security-strip" aria-label="Security indicators">
      <span>
        <ShieldOutlined fontSize="small" aria-hidden="true" />
        Secure OTP sign-in
      </span>
      <span>
        <CheckCircleOutlined fontSize="small" aria-hidden="true" />
        Private health information
      </span>
      <span>
        <LockOutlined fontSize="small" aria-hidden="true" />
        Protected patient access
      </span>
      <span>
        <ContactSupportOutlined fontSize="small" aria-hidden="true" />
        Help available when needed
      </span>
    </div>
  );
}
