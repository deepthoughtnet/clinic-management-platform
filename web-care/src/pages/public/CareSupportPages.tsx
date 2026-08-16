import { type ReactNode } from "react";
import { Link } from "react-router-dom";
import { careConfig } from "../../config";

const DEFAULT_SUPPORT_EMAIL = "support@jeevanam.health";

function supportHref() {
  if (careConfig.supportUrl) {
    return careConfig.supportUrl;
  }
  return `mailto:${DEFAULT_SUPPORT_EMAIL}?subject=${encodeURIComponent("Jeevanam Care support")}`;
}

function SupportPageShell({
  eyebrow,
  title,
  subtitle,
  children,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <section className="page-section narrow-page">
      <div className="section-heading">
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      <div className="login-placeholder care-support-page">
        {children}
        <div className="cta-row">
          <Link className="secondary-button" to="/">
            Back to home
          </Link>
        </div>
      </div>
    </section>
  );
}

export function ContactPage() {
  const href = supportHref();
  const linkLabel = href.startsWith("mailto:") ? "Email support" : "Open support portal";

  return (
    <SupportPageShell
      eyebrow="Support"
      title="Contact Jeevanam Care"
      subtitle="Get help with access requests, sign-in, bookings, prescriptions, reports, and patient support."
    >
      <ul className="portal-inline-list">
        <li>
          <strong>Patient support</strong>
          <span>Contact our support team for help with patient access, appointments, prescriptions, reports, or portal usage.</span>
        </li>
        <li>
          <strong>General enquiries</strong>
          <span>Questions about access, appointments, or portal usage can be sent to the same support channel.</span>
        </li>
        <li>
          <strong>Provider demos</strong>
          <span>If you need help listing a practice, contact the provider onboarding team through the same support channel.</span>
        </li>
      </ul>
      <p className="portal-help-text">
        {href.startsWith("mailto:")
          ? "Email support@jeevanam.health for direct help with patient access, bookings, prescriptions, and reports."
          : "Open the support portal to submit patient support and provider enquiry requests."}
      </p>
      <div className="cta-row">
        <a className="primary-button" href={href}>
          {linkLabel}
        </a>
      </div>
    </SupportPageShell>
  );
}

export function HelpCentrePage() {
  return (
    <SupportPageShell
      eyebrow="Help Centre"
      title="Jeevanam Care help centre"
      subtitle="Short answers for the most common patient portal tasks."
    >
      <ul className="portal-inline-list">
        <li>
          <strong>Request access</strong>
          <span>Use Request Access when you are part of a Friends &amp; Family preview and need approval before sign-in.</span>
        </li>
        <li>
          <strong>Sign in</strong>
          <span>Approved users can sign in with their temporary access code. OTP mode remains available where enabled.</span>
        </li>
        <li>
          <strong>Bookings</strong>
          <span>Use the booking flow to choose a doctor, clinic, and appointment time before confirming a visit.</span>
        </li>
        <li>
          <strong>Prescriptions and reports</strong>
          <span>Your shared prescriptions and lab reports appear inside the patient workspace after your clinic shares them.</span>
        </li>
        <li>
          <strong>Need more help?</strong>
          <span>Contact support if you cannot complete a request, login, or booking step.</span>
        </li>
      </ul>
    </SupportPageShell>
  );
}

export function PrivacyPolicyPage() {
  return (
    <SupportPageShell
      eyebrow="Legal"
      title="Privacy Policy"
      subtitle="How Jeevanam Care handles patient data in this web application."
    >
      <ul className="portal-inline-list">
        <li>
          <strong>Data collected</strong>
          <span>We only process the details needed to support patient login, bookings, prescriptions, bills, and shared reports.</span>
        </li>
        <li>
          <strong>Use of data</strong>
          <span>Patient information is used to operate the care journey, support records, and maintain access control.</span>
        </li>
        <li>
          <strong>Sharing</strong>
          <span>Information is shown to the patient, their authorized care team, and platform administrators where required by workflow.</span>
        </li>
        <li>
          <strong>Security</strong>
          <span>Access is protected by authenticated sessions, tenant boundaries, and role-based controls.</span>
        </li>
        <li>
          <strong>Questions</strong>
          <span>Contact Jeevanam Care support if you have questions about how your information is handled.</span>
        </li>
      </ul>
    </SupportPageShell>
  );
}

export function TermsPage() {
  return (
    <SupportPageShell
      eyebrow="Legal"
      title="Terms and Conditions"
      subtitle="The current terms for using Jeevanam Care."
    >
      <ul className="portal-inline-list">
        <li>
          <strong>Account use</strong>
          <span>Use your portal access only for the patient account and tenant that Platform Admin has approved.</span>
        </li>
        <li>
          <strong>Acceptable use</strong>
          <span>Do not attempt to access another patient&apos;s records or share credentials with anyone else.</span>
        </li>
        <li>
          <strong>Service availability</strong>
          <span>Portal access may depend on the services enabled by your clinic or hospital and the underlying care workflows.</span>
        </li>
        <li>
          <strong>Clinical information</strong>
          <span>Shared care content remains subject to the clinic or hospital workflow that produced it.</span>
        </li>
        <li>
          <strong>Support</strong>
          <span>Contact support if you have questions about these terms or your access to Jeevanam Care.</span>
        </li>
      </ul>
    </SupportPageShell>
  );
}
