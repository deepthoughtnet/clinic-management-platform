import { type FormEvent, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { requestProviderAccess } from "../../api/providerAuth";
import { DISCOVER_ROUTES } from "../../routes";
import type { ProviderType } from "../../api/providerOnboarding";

const PROVIDER_TYPE_OPTIONS: Array<{ value: ProviderType; label: string; description: string }> = [
  { value: "INDIVIDUAL_DOCTOR", label: "Doctor", description: "Independent doctors and consultants" },
  { value: "CLINIC", label: "Clinic", description: "Outpatient and ambulatory practices" },
  { value: "HOSPITAL", label: "Hospital", description: "Hospitals and multi-speciality organisations" },
];

function resolveReturnTo(value: string | null) {
  const trimmed = value?.trim();
  if (!trimmed) {
    return DISCOVER_ROUTES.providerLogin.path;
  }
  return trimmed.startsWith("/provider/") || trimmed === "/provider" ? trimmed : DISCOVER_ROUTES.providerLogin.path;
}

export function ProviderRequestAccessPage() {
  const [searchParams] = useSearchParams();
  const returnTo = useMemo(() => resolveReturnTo(searchParams.get("returnTo")), [searchParams]);
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [mobile, setMobile] = useState("");
  const [providerType, setProviderType] = useState<ProviderType>("CLINIC");
  const [providerApplicationReference, setProviderApplicationReference] = useState("");
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submittedReference, setSubmittedReference] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!fullName.trim()) {
      setError("Enter your name.");
      return;
    }
    if (!mobile.trim()) {
      setError("Enter a mobile number.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setSuccess(null);
    try {
      const response = await requestProviderAccess({
        fullName: fullName.trim(),
        email: email.trim() || null,
        mobile: mobile.trim(),
        providerType,
        providerApplicationReference: providerApplicationReference.trim() || null,
        note: note.trim() || null,
      });
      setSubmittedReference(response.id);
      setSuccess("Your access request has been submitted. Platform Admin will review it shortly.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "We could not submit the access request right now.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="page-section provider-auth-page provider-request-access-page">
      <div className="provider-auth-access-layout provider-auth-access-layout--request">
        <article className="provider-auth-access-panel provider-auth-access-panel--intro">
          <div className="provider-auth-heading">
            <span className="eyebrow">Provider Access</span>
            <h1>Request access to Jeevanam Provider</h1>
            <p>
              Tell us who you are and which provider workspace you should access. Platform Admin will review and approve eligible requests.
            </p>
            <p className="provider-auth-note">
              Your existing provider application and publication lifecycle stay unchanged.
            </p>
          </div>

          <div className="provider-auth-access-highlights" aria-label="Access request notes">
            <div className="provider-auth-access-highlight">
              <strong>Business identity</strong>
              <span>Use your real provider name, email and mobile number.</span>
            </div>
            <div className="provider-auth-access-highlight">
              <strong>Workspace linkage</strong>
              <span>Help Platform Admin connect your access request to the correct provider account.</span>
            </div>
            <div className="provider-auth-access-highlight">
              <strong>Controlled preview</strong>
              <span>Only approved providers can sign in with their temporary access code.</span>
            </div>
          </div>
        </article>

        <article className="provider-auth-card provider-auth-card--access">
          <form className="provider-auth-form" onSubmit={submit}>
            <div className="provider-auth-state-heading">
              <span className="eyebrow">Request access</span>
              <p>Platform Admin reviews every request before sign-in is enabled.</p>
            </div>

            <label className="provider-auth-field">
              <span>Provider name</span>
              <input
                type="text"
                value={fullName}
                onChange={(event) => {
                  setFullName(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                autoComplete="name"
                placeholder="Jeevanam Multispeciality Hospital"
              />
            </label>

            <label className="provider-auth-field">
              <span>Email address</span>
              <input
                type="email"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                autoComplete="email"
                placeholder="provider@example.com"
              />
            </label>

            <label className="provider-auth-field">
              <span>Mobile number</span>
              <input
                type="tel"
                value={mobile}
                onChange={(event) => {
                  setMobile(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                autoComplete="tel"
                placeholder="+91 98765 01200"
              />
            </label>

            <label className="provider-auth-field">
              <span>Provider type</span>
              <select
                value={providerType}
                onChange={(event) => {
                  setProviderType(event.target.value as ProviderType);
                  setError(null);
                  setSuccess(null);
                }}
              >
                {PROVIDER_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label} - {option.description}
                  </option>
                ))}
              </select>
            </label>

            <label className="provider-auth-field">
              <span>Provider application reference</span>
              <input
                type="text"
                value={providerApplicationReference}
                onChange={(event) => {
                  setProviderApplicationReference(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                placeholder="Optional if you already have a reference"
              />
            </label>

            <label className="provider-auth-field">
              <span>Note</span>
              <textarea
                rows={4}
                value={note}
                onChange={(event) => {
                  setNote(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                placeholder="Tell Platform Admin anything that helps identify your workspace."
              />
            </label>

            {error ? (
              <p className="provider-auth-error" role="alert">
                {error}
              </p>
            ) : null}

            {success ? (
              <p className="provider-auth-success" role="status">
                {success}
              </p>
            ) : null}

            {submittedReference ? (
              <p className="provider-auth-helper">
                Request reference: <strong>{submittedReference}</strong>
              </p>
            ) : null}

            <div className="provider-auth-actions">
              <button className="primary-button" type="submit" disabled={submitting}>
                {submitting ? "Submitting..." : "Submit access request"}
              </button>
              <div className="provider-auth-secondary">
                <small>Already approved?</small>
                <Link className="secondary-button" to={returnTo}>
                  Return to sign in
                </Link>
              </div>
            </div>
          </form>
        </article>
      </div>
    </section>
  );
}
