import { type FormEvent, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { loginProviderAccess } from "../../api/providerAuth";
import { useProviderSession } from "../../context/ProviderSessionContext";
import { DISCOVER_ROUTES } from "../../routes";
import {
  getProviderAccessCodeError,
  getProviderLoginIdentifierError,
  normalizeProviderLoginIdentifier,
  providerLoginIdentifierHelpText,
  sanitizeProviderAccessCodeInput,
} from "./providerAccessValidation";

type ProviderAccessApprovalLoginPageProps = {
  returnTo: string;
};

export function ProviderAccessApprovalLoginPage({ returnTo }: ProviderAccessApprovalLoginPageProps) {
  const { status, refreshSession } = useProviderSession();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState("");
  const [accessCode, setAccessCode] = useState("");
  const [identifierTouched, setIdentifierTouched] = useState(false);
  const [accessCodeTouched, setAccessCodeTouched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const trimmedIdentifier = normalizeProviderLoginIdentifier(identifier);
  const trimmedAccessCode = sanitizeProviderAccessCodeInput(accessCode);
  const identifierError = getProviderLoginIdentifierError(identifier);
  const accessCodeError = getProviderAccessCodeError(accessCode);
  const showIdentifierError = identifierTouched && Boolean(identifierError);
  const showAccessCodeError = accessCodeTouched && Boolean(accessCodeError);
  const canSignIn = !identifierError && !accessCodeError;
  const requestAccessPath = useMemo(() => {
    const next = new URLSearchParams();
    const currentReturnTo = searchParams.get("returnTo") || returnTo;
    if (currentReturnTo) {
      next.set("returnTo", currentReturnTo);
    }
    const suffix = next.toString() ? `?${next.toString()}` : "";
    return `${DISCOVER_ROUTES.providerRequestAccess.path}${suffix}`;
  }, [returnTo, searchParams]);

  async function signIn(event: FormEvent) {
    event.preventDefault();
    setIdentifierTouched(true);
    setAccessCodeTouched(true);
    if (identifierError || accessCodeError) {
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await loginProviderAccess({ identifier: trimmedIdentifier, accessCode: trimmedAccessCode });
      await refreshSession(true);
      navigate(returnTo, { replace: true });
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "We could not sign you in right now.");
    } finally {
      setLoading(false);
    }
  }

  if (status === "authenticated") {
    return <Navigate replace to={returnTo} />;
  }

  return (
    <section className="page-section provider-auth-page provider-auth-page--access">
      <div className="provider-auth-access-layout">
        <article className="provider-auth-access-panel provider-auth-access-panel--intro">
          <div className="provider-auth-heading">
            <span className="eyebrow">Provider Login</span>
            <h1>Friends &amp; Family access to Jeevanam Provider</h1>
            <p>
              Approved providers can sign in with their registered email address or mobile number and a temporary access code.
            </p>
            <p className="provider-auth-note">
              Controlled preview access is enabled for this environment. Request access if you are not yet approved.
            </p>
          </div>

          <div className="provider-auth-access-highlights" aria-label="Controlled access highlights">
            <div className="provider-auth-access-highlight">
              <strong>Approved providers only</strong>
              <span>Platform Admin reviews access requests before sign-in is enabled.</span>
            </div>
            <div className="provider-auth-access-highlight">
              <strong>Temporary access code</strong>
              <span>Use the code shared after approval to open your provider workspace.</span>
            </div>
            <div className="provider-auth-access-highlight">
              <strong>Existing lifecycle preserved</strong>
              <span>Your provider applications and public profile workflow stay intact.</span>
            </div>
          </div>

          <div className="provider-auth-access-preview" aria-hidden="true">
            <div className="provider-auth-access-preview-shell">
              <div className="provider-auth-access-preview-header">
                <span className="provider-auth-access-preview-pill">Private access</span>
                <span>Verified providers only</span>
              </div>
              <div className="provider-auth-access-preview-card">
                <span className="provider-auth-access-preview-kicker">Provider workspace</span>
                <strong>Profile management, review and publication</strong>
                <p>Preview, submit, review and publish provider profiles from a controlled workspace.</p>
              </div>
              <div className="provider-auth-access-preview-row">
                <span>Access requests</span>
                <span>Platform Admin review</span>
              </div>
            </div>
          </div>
        </article>

        <article className="provider-auth-card provider-auth-card--access">
          <form className="provider-auth-form" onSubmit={signIn}>
            <div className="provider-auth-state-heading">
              <span className="eyebrow">Already approved?</span>
              <p>Sign in with the access code shared after Platform Admin approval.</p>
            </div>

            <label className="provider-auth-field">
              <span>Email address or mobile number <span className="provider-field-required" aria-hidden="true">*</span></span>
              <input
                type="text"
                value={identifier}
                onChange={(event) => {
                  setIdentifier(event.target.value);
                  setError(null);
                  setIdentifierTouched(true);
                }}
                onBlur={() => setIdentifierTouched(true)}
                autoComplete="username"
                inputMode="text"
                placeholder="name@clinic.com or +91 98765 01200"
                spellCheck={false}
                maxLength={254}
                aria-describedby="provider-access-identifier-help"
                aria-invalid={showIdentifierError || Boolean(error) ? true : undefined}
              />
            </label>
            <p id="provider-access-identifier-help" className="provider-auth-helper">
              {providerLoginIdentifierHelpText(identifier)}
            </p>
            {showIdentifierError ? (
              <p className="provider-auth-field-error" role="alert">
                {identifierError}
              </p>
            ) : null}

            <label className="provider-auth-field">
              <span>Temporary access code <span className="provider-field-required" aria-hidden="true">*</span></span>
              <input
                aria-invalid={showAccessCodeError || Boolean(error) ? true : undefined}
                type="text"
                value={accessCode}
                onChange={(event) => {
                  setAccessCode(sanitizeProviderAccessCodeInput(event.target.value));
                  setAccessCodeTouched(true);
                  setError(null);
                }}
                onBlur={() => setAccessCodeTouched(true)}
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={8}
                placeholder="12345678"
                autoComplete="one-time-code"
                aria-describedby="provider-access-code-help"
              />
            </label>
            <p id="provider-access-code-help" className="provider-auth-helper">
              Enter the 8-digit access code shared by Platform Admin after approval.
            </p>
            {showAccessCodeError ? (
              <p className="provider-auth-field-error" role="alert">
                {accessCodeError}
              </p>
            ) : null}

            {error ? (
              <p className="provider-auth-error" role="alert">
                {error}
              </p>
            ) : null}

            <div className="provider-auth-actions">
              <button className="primary-button" type="submit" disabled={loading || !canSignIn}>
                {loading ? "Signing in..." : "Sign in"}
              </button>
              <div className="provider-auth-secondary">
                <small>Need access?</small>
                <Link className="secondary-button" to={requestAccessPath}>
                  Request Provider Access
                </Link>
              </div>
            </div>
          </form>
        </article>
      </div>
    </section>
  );
}
