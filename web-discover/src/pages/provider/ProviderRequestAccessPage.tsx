import { type FormEvent, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { requestProviderAccess } from "../../api/providerAuth";
import { DISCOVER_ROUTES } from "../../routes";
import type { ProviderType } from "../../api/providerOnboarding";
import {
  getProviderAccessRequestEmailError,
  getProviderAccessRequestFullNameError,
  getProviderAccessRequestMobileError,
  getProviderAccessRequestNoteError,
  getProviderAccessRequestProviderTypeError,
  getProviderAccessRequestReferenceError,
  normalizeProviderAccessRequestEmail,
  normalizeProviderAccessRequestFullName,
  normalizeProviderAccessRequestMobile,
  normalizeProviderAccessRequestNote,
  normalizeProviderAccessRequestProviderType,
  normalizeProviderAccessRequestReference,
} from "./providerAccessValidation";

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
  const [providerType, setProviderType] = useState<ProviderType | "">("");
  const [providerApplicationReference, setProviderApplicationReference] = useState("");
  const [note, setNote] = useState("");
  const [touched, setTouched] = useState({
    fullName: false,
    email: false,
    mobile: false,
    providerType: false,
    providerApplicationReference: false,
    note: false,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const fullNameError = getProviderAccessRequestFullNameError(fullName);
  const emailError = getProviderAccessRequestEmailError(email);
  const mobileError = getProviderAccessRequestMobileError(mobile);
  const providerTypeError = getProviderAccessRequestProviderTypeError(providerType);
  const providerApplicationReferenceError = getProviderAccessRequestReferenceError(providerApplicationReference);
  const noteError = getProviderAccessRequestNoteError(note);
  const canSubmit = !fullNameError && !emailError && !mobileError && !providerTypeError && !providerApplicationReferenceError && !noteError;
  const showError = (field: keyof typeof touched, errorMessage: string | null) => touched[field] && Boolean(errorMessage);

  function touchAllFields() {
    setTouched({
      fullName: true,
      email: true,
      mobile: true,
      providerType: true,
      providerApplicationReference: true,
      note: true,
    });
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    touchAllFields();
    if (!canSubmit) {
      setError(null);
      return;
    }
    setSubmitting(true);
    setError(null);
    setSuccess(null);
    try {
      const response = await requestProviderAccess({
        fullName: normalizeProviderAccessRequestFullName(fullName),
        email: normalizeProviderAccessRequestEmail(email) || null,
        mobile: normalizeProviderAccessRequestMobile(mobile),
        providerType: normalizeProviderAccessRequestProviderType(providerType),
        providerApplicationReference: normalizeProviderAccessRequestReference(providerApplicationReference) || null,
        note: normalizeProviderAccessRequestNote(note) || null,
      });
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
          <form className="provider-auth-form provider-auth-form--request" onSubmit={submit}>
            <div className="provider-auth-state-heading">
              <span className="eyebrow">Request access</span>
              <p>Platform Admin reviews every request before sign-in is enabled.</p>
            </div>

            <div className="provider-auth-form-grid">
            <label className="provider-auth-field provider-auth-field--full">
              <span>Provider name <span className="provider-field-required" aria-hidden="true">*</span></span>
              <input
                aria-invalid={showError("fullName", fullNameError) ? true : undefined}
                type="text"
                value={fullName}
                onChange={(event) => {
                  setFullName(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                onBlur={() => setTouched((current) => ({ ...current, fullName: true }))}
                autoComplete="name"
                placeholder="Jeevanam Multispeciality Hospital"
                maxLength={120}
              />
              {showError("fullName", fullNameError) ? (
                <p className="provider-auth-field-error" role="alert">{fullNameError}</p>
              ) : null}
            </label>

            <label className="provider-auth-field">
              <span>Email address <span className="provider-field-required" aria-hidden="true">*</span></span>
              <input
                aria-invalid={showError("email", emailError) ? true : undefined}
                type="email"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                onBlur={() => setTouched((current) => ({ ...current, email: true }))}
                autoComplete="email"
                placeholder="provider@example.com"
                maxLength={254}
              />
              {showError("email", emailError) ? (
                <p className="provider-auth-field-error" role="alert">{emailError}</p>
              ) : null}
            </label>

            <label className="provider-auth-field">
              <span>Mobile number <span className="provider-field-required" aria-hidden="true">*</span></span>
              <input
                aria-invalid={showError("mobile", mobileError) ? true : undefined}
                type="tel"
                value={mobile}
                onChange={(event) => {
                  setMobile(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                onBlur={() => setTouched((current) => ({ ...current, mobile: true }))}
                autoComplete="tel"
                placeholder="+91 98765 01200"
                maxLength={20}
              />
              {showError("mobile", mobileError) ? (
                <p className="provider-auth-field-error" role="alert">{mobileError}</p>
              ) : null}
            </label>

            <label className="provider-auth-field">
              <span>Provider type <span className="provider-field-required" aria-hidden="true">*</span></span>
              <select
                aria-invalid={showError("providerType", providerTypeError) ? true : undefined}
                value={providerType}
                onFocus={() => setTouched((current) => ({ ...current, providerType: true }))}
                onChange={(event) => {
                  setProviderType(event.target.value as ProviderType | "");
                  setError(null);
                  setSuccess(null);
                }}
                onBlur={() => setTouched((current) => ({ ...current, providerType: true }))}
              >
                <option value="" disabled>
                  Select provider type
                </option>
                {PROVIDER_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label} - {option.description}
                  </option>
                ))}
              </select>
              {showError("providerType", providerTypeError) ? (
                <p className="provider-auth-field-error" role="alert">{providerTypeError}</p>
              ) : null}
            </label>

            <label className="provider-auth-field">
              <span>Provider application reference <span className="provider-field-optional" aria-hidden="true">(optional)</span></span>
              <input
                aria-invalid={showError("providerApplicationReference", providerApplicationReferenceError) ? true : undefined}
                type="text"
                value={providerApplicationReference}
                onChange={(event) => {
                  setProviderApplicationReference(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                onBlur={() => setTouched((current) => ({ ...current, providerApplicationReference: true }))}
                placeholder="Business reference or application number"
                maxLength={80}
              />
              <p className="provider-auth-helper">Optional. Use a business-readable application or workspace reference if you have one.</p>
              {showError("providerApplicationReference", providerApplicationReferenceError) ? (
                <p className="provider-auth-field-error" role="alert">{providerApplicationReferenceError}</p>
              ) : null}
            </label>

            <label className="provider-auth-field provider-auth-field--full">
              <span>Note <span className="provider-field-optional" aria-hidden="true">(optional)</span></span>
              <textarea
                aria-invalid={showError("note", noteError) ? true : undefined}
                rows={4}
                value={note}
                onChange={(event) => {
                  setNote(event.target.value);
                  setError(null);
                  setSuccess(null);
                }}
                onBlur={() => setTouched((current) => ({ ...current, note: true }))}
                placeholder="Tell Platform Admin anything that helps identify your workspace."
                maxLength={500}
              />
              {showError("note", noteError) ? (
                <p className="provider-auth-field-error" role="alert">{noteError}</p>
              ) : null}
            </label>
            </div>

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

            <div className="provider-auth-actions">
              <button className="primary-button" type="submit" disabled={submitting || !canSubmit}>
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
