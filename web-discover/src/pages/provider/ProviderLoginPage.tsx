import { type FormEvent, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  requestProviderLoginChallenge,
  verifyProviderLoginCode,
  type ProviderLoginChallengeResponse,
} from "../../api/providerAuth";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { DISCOVER_ROUTES } from "../../routes";

type Step = "identifier" | "code";

function isEmail(value: string) {
  return value.includes("@");
}

export function ProviderLoginPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>("identifier");
  const [identifier, setIdentifier] = useState("");
  const [code, setCode] = useState("");
  const [challenge, setChallenge] = useState<ProviderLoginChallengeResponse | null>(null);
  const [challengeRequestedAt, setChallengeRequestedAt] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nowTick, setNowTick] = useState(Date.now());

  useEffect(() => {
    if (step !== "code" || !challenge) {
      return;
    }
    const interval = window.setInterval(() => setNowTick(Date.now()), 1000);
    return () => window.clearInterval(interval);
  }, [challenge, step]);

  const resendInSeconds = useMemo(() => {
    if (!challenge || !challengeRequestedAt) {
      return 0;
    }
    const remaining = Math.max(0, challenge.resendAfterSeconds - Math.floor((nowTick - challengeRequestedAt) / 1000));
    return remaining;
  }, [challenge, challengeRequestedAt, nowTick]);

  async function sendCode(event?: FormEvent) {
    event?.preventDefault();
    if (!identifier.trim()) {
      setError("Enter your email address or mobile number.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await requestProviderLoginChallenge(identifier.trim());
      setChallenge(result);
      setChallengeRequestedAt(Date.now());
      setCode("");
      setStep("code");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "We could not send a login code right now.");
    } finally {
      setLoading(false);
    }
  }

  async function verifyCode(event: FormEvent) {
    event.preventDefault();
    if (!code.trim()) {
      setError("Enter the verification code.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await verifyProviderLoginCode(identifier.trim(), code.trim());
      navigate(DISCOVER_ROUTES.providerWorkspace.path, { replace: true });
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "We could not verify your login code right now.");
    } finally {
      setLoading(false);
    }
  }

  if (step === "identifier") {
    return (
      <section className="page-section provider-dashboard-page">
        <div className="provider-dashboard-hero">
          <div>
            <span className="eyebrow">Provider Login</span>
            <h1>Sign in to your provider workspace</h1>
            <p>Use the email address or mobile number linked to your Jeevanam Discover provider application.</p>
          </div>
          <div className="resume-card">
            <strong>Passwordless access</strong>
            <span>Verification code sent by email or SMS</span>
          </div>
        </div>

        <article className="provider-dashboard-panel">
          <form className="stacked-form" onSubmit={sendCode}>
            <label>
              Email address or mobile number
              <input
                type="text"
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
                autoComplete="username"
                placeholder="name@example.com or 9876543210"
              />
            </label>
            {error ? <p className="inline-error" role="alert">{error}</p> : null}
            <div className="cta-row">
            <button className="primary-button" type="submit" disabled={loading}>
              Continue
            </button>
            <Link className="secondary-button" to={DISCOVER_ROUTES.listPractice.path}>
              Back to provider info
              </Link>
            </div>
          </form>
        </article>
      </section>
    );
  }

  return (
    <section className="page-section provider-dashboard-page">
      <div className="provider-dashboard-hero">
        <div>
          <span className="eyebrow">Provider Login</span>
          <h1>Enter your verification code</h1>
          <p>
            {challenge?.message ?? "If this contact is registered, a verification code has been sent."}
          </p>
          <p>
            {isEmail(identifier) ? "We sent the code to your email address." : "We sent the code to your mobile number."}
          </p>
        </div>
        <div className="resume-card">
          <strong>{identifier.trim()}</strong>
          <span>{challenge?.developmentCode ? "Local/UAT code available" : "Awaiting code entry"}</span>
        </div>
      </div>

      <article className="provider-dashboard-panel">
        <form className="stacked-form" onSubmit={verifyCode}>
          <label>
            Verification code
            <input
              type="text"
              inputMode="numeric"
              value={code}
              onChange={(event) => setCode(event.target.value)}
              autoComplete="one-time-code"
              placeholder="123456"
            />
          </label>
          {challenge?.developmentCode ? (
            <div className="change-request-item" role="status">
              <strong>Local / UAT verification code</strong>
              <p>{challenge.developmentCode}</p>
            </div>
          ) : null}
          {error ? <p className="inline-error" role="alert">{error}</p> : null}
          <div className="cta-row">
            <button className="primary-button" type="submit" disabled={loading}>
              Verify and continue
            </button>
            <button
              className="secondary-button"
              type="button"
              onClick={() => void sendCode()}
              disabled={loading || (challenge ? resendInSeconds > 0 : false)}
            >
              Resend code
            </button>
            <button className="ghost-button" type="button" onClick={() => setStep("identifier")}>
              Change contact
            </button>
          </div>
          {challenge ? (
            <small className="autosave-row">
              {resendInSeconds > 0 ? `Resend available in ${resendInSeconds}s` : "You can request another code now."}
            </small>
          ) : null}
        </form>
      </article>
    </section>
  );
}
