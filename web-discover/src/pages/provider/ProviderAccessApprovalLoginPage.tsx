import { type FormEvent, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { loginProviderAccess } from "../../api/providerAuth";
import { useProviderSession } from "../../context/ProviderSessionContext";
import { DISCOVER_ROUTES } from "../../routes";

type ProviderAccessApprovalLoginPageProps = {
  returnTo: string;
};

function isEmail(value: string) {
  return value.includes("@");
}

function formatIdentifierHint(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return "your registered email or mobile number";
  }
  return isEmail(trimmed) ? "your registered email address" : "your registered mobile number";
}

function resolveAccessCode(value: string) {
  return value.replace(/\D/g, "").slice(0, 8);
}

export function ProviderAccessApprovalLoginPage({ returnTo }: ProviderAccessApprovalLoginPageProps) {
  const { status, refreshSession } = useProviderSession();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState("");
  const [accessCode, setAccessCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const trimmedIdentifier = identifier.trim();
  const trimmedAccessCode = resolveAccessCode(accessCode);
  const canSignIn = trimmedIdentifier.length > 0 && trimmedAccessCode.length === 8;
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
    if (!trimmedIdentifier) {
      setError("Enter your registered email address or mobile number.");
      return;
    }
    if (trimmedAccessCode.length !== 8) {
      setError("Enter the 8-digit access code shared by Platform Admin.");
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
              Approved providers can sign in with their registered email or mobile number and a temporary access code.
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
              <span>Email address or mobile number</span>
              <input
                type="text"
                value={identifier}
                onChange={(event) => {
                  setIdentifier(event.target.value);
                  setError(null);
                }}
                autoComplete="username"
                inputMode="text"
                placeholder="name@clinic.com or +91 98765 01200"
                spellCheck={false}
                aria-describedby="provider-access-identifier-help"
              />
            </label>
            <p id="provider-access-identifier-help" className="provider-auth-helper">
              Use the registered {formatIdentifierHint(identifier)} linked to your approved provider account.
            </p>

            <label className="provider-auth-field">
              <span>Temporary access code</span>
              <input
                type="text"
                value={accessCode}
                onChange={(event) => {
                  setAccessCode(resolveAccessCode(event.target.value));
                  setError(null);
                }}
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
