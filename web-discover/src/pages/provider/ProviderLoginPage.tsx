import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  requestProviderLoginChallenge,
  verifyProviderLoginCode,
  type ProviderLoginChallengeResponse,
} from "../../api/providerAuth";
import { DISCOVER_ROUTES } from "../../routes";

type Step = "identifier" | "code";
type ActiveProviderLoginChallenge = {
  challengeId: string;
  channel: ProviderLoginChallengeResponse["channel"];
  maskedRecipient: string;
  message: string;
  developmentCode: string | null;
  verificationMode: ProviderLoginChallengeResponse["verificationMode"];
  expiresAt: string;
  resendAvailableAt: string;
};

function toActiveChallenge(response: ProviderLoginChallengeResponse): ActiveProviderLoginChallenge {
  return {
    challengeId: response.challengeId,
    channel: response.channel,
    maskedRecipient: response.maskedRecipient ?? "",
    message: response.message,
    developmentCode: response.developmentCode ?? null,
    verificationMode: response.verificationMode ?? null,
    expiresAt: response.expiresAt,
    resendAvailableAt: response.resendAvailableAt,
  };
}

function isEmail(value: string) {
  return value.includes("@");
}

function maskRecipient(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }
  if (isEmail(trimmed)) {
    const [localPart, domain = ""] = trimmed.split("@");
    const prefix = localPart.slice(0, 1) || "";
    return `${prefix}${"*".repeat(Math.max(0, Math.min(8, localPart.length - 1)))}@${domain}`;
  }
  const digits = trimmed.replace(/[^0-9+]/g, "");
  const tail = digits.slice(-4);
  return `${"*".repeat(Math.max(0, digits.length - 4))}${tail}`;
}

function formatCountdown(seconds: number) {
  if (seconds <= 0) {
    return "Ready now";
  }
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return minutes > 0 ? `${minutes}m ${remaining.toString().padStart(2, "0")}s` : `${remaining}s`;
}

function channelLabel(channel: ProviderLoginChallengeResponse["channel"] | null) {
  if (channel === "EMAIL") {
    return "Email verification";
  }
  if (channel === "SMS") {
    return "Mobile verification";
  }
  return "Verification";
}

function safeVerificationError(ex: unknown) {
  const message = ex instanceof Error ? ex.message : "We could not complete verification right now.";
  if (/carepilot|enabled=false|not configured|providerName|deliveryReference|verificationMode|internal server error/i.test(message)) {
    return "Verification service is temporarily unavailable. Please try again later.";
  }
  return message;
}

export function ProviderLoginPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>("identifier");
  const [identifier, setIdentifier] = useState("");
  const [code, setCode] = useState("");
  const [activeChallenge, setActiveChallenge] = useState<ActiveProviderLoginChallenge | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nowTick, setNowTick] = useState(Date.now());
  const codeInputRef = useRef<HTMLInputElement | null>(null);
  const identifierInputRef = useRef<HTMLInputElement | null>(null);
  const activeChallengeRef = useRef<ActiveProviderLoginChallenge | null>(null);
  const sendLockRef = useRef(false);
  const verifyLockRef = useRef(false);
  const challengeRequestSequenceRef = useRef(0);
  const latestAppliedChallengeSequenceRef = useRef(0);

  function clearChallengeState(nextStep: Step = "identifier") {
    activeChallengeRef.current = null;
    setActiveChallenge(null);
    setCode("");
    setError(null);
    setNowTick(Date.now());
    setStep(nextStep);
  }

  useEffect(() => {
    activeChallengeRef.current = activeChallenge;
  }, [activeChallenge]);

  useEffect(() => {
    if (!activeChallenge || step !== "code") {
      return;
    }
    const interval = window.setInterval(() => setNowTick(Date.now()), 1000);
    return () => window.clearInterval(interval);
  }, [activeChallenge, step]);

  useEffect(() => {
    if (step === "code") {
      const timer = window.setTimeout(() => {
        codeInputRef.current?.focus();
        codeInputRef.current?.select();
      }, 0);
      return () => window.clearTimeout(timer);
    }
    const timer = window.setTimeout(() => {
      identifierInputRef.current?.focus();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [activeChallenge, step]);

  const resendInSeconds = useMemo(() => {
    if (!activeChallenge) {
      return 0;
    }
    const remaining = Math.max(0, Math.ceil((new Date(activeChallenge.resendAvailableAt).getTime() - nowTick) / 1000));
    return remaining;
  }, [activeChallenge, nowTick]);

  const expiresInSeconds = useMemo(() => {
    if (!activeChallenge) {
      return 0;
    }
    return Math.max(0, Math.ceil((new Date(activeChallenge.expiresAt).getTime() - nowTick) / 1000));
  }, [activeChallenge, nowTick]);

  const maskedRecipient = activeChallenge?.maskedRecipient || maskRecipient(identifier);
  const codeDigits = code.replace(/\D/g, "").slice(0, 6);
  const canVerify = codeDigits.length === 6 && !!activeChallenge;
  const sending = loading && step === "identifier";
  const verifying = loading && step === "code";
  async function sendCode(event?: FormEvent) {
    event?.preventDefault();
    if (sendLockRef.current) {
      return;
    }
    const trimmed = identifier.trim();
    if (!trimmed) {
      setError("Enter your email address or mobile number.");
      return;
    }
    sendLockRef.current = true;
    setLoading(true);
    setError(null);
    const requestSequence = ++challengeRequestSequenceRef.current;
    try {
      const result = await requestProviderLoginChallenge(trimmed);
      if (requestSequence < latestAppliedChallengeSequenceRef.current) {
        return;
      }
      latestAppliedChallengeSequenceRef.current = requestSequence;
      const nextChallenge = toActiveChallenge(result);
      activeChallengeRef.current = nextChallenge;
      setActiveChallenge(nextChallenge);
      setCode("");
      setStep("code");
      setError(null);
      setNowTick(Date.now());
    } catch (ex) {
      setError(safeVerificationError(ex));
    } finally {
      if (requestSequence === challengeRequestSequenceRef.current) {
        sendLockRef.current = false;
        setLoading(false);
      }
    }
  }

  async function verifyCode(event: FormEvent) {
    event.preventDefault();
    if (verifyLockRef.current) {
      return;
    }
    const currentChallenge = activeChallengeRef.current;
    if (!currentChallenge) {
      setError("Request a verification code first.");
      return;
    }
    if (!/^\d{6}$/.test(codeDigits)) {
      setError("Enter the six-digit verification code.");
      return;
    }
    verifyLockRef.current = true;
    setLoading(true);
    setError(null);
    try {
      await verifyProviderLoginCode(currentChallenge.challengeId, codeDigits);
      navigate(DISCOVER_ROUTES.providerWorkspace.path, { replace: true });
    } catch (ex) {
      setError(safeVerificationError(ex));
    } finally {
      verifyLockRef.current = false;
      setLoading(false);
    }
  }

  return (
    <section className="page-section provider-auth-page">
      <header className="provider-auth-hero">
        <div className="provider-auth-heading">
          <span className="eyebrow">Provider Login</span>
          <h1>Manage your provider applications and public profiles</h1>
          <p>Sign in using the verified email address or mobile number connected to your provider account.</p>
          <p className="provider-auth-note">No password required. We will send a one-time verification code.</p>
        </div>
      </header>

      <article className="provider-auth-card">
        {step === "identifier" ? (
          <form className="provider-auth-form" onSubmit={sendCode}>
            <label className="provider-auth-field">
              <span>Email address or mobile number</span>
              <input
                ref={identifierInputRef}
                type="text"
                value={identifier}
                onChange={(event) => {
                  setIdentifier(event.target.value);
                  if (error) {
                    setError(null);
                  }
                }}
                autoComplete="username"
                inputMode="text"
                placeholder="name@clinic.com or +91 98765 01200"
                spellCheck={false}
                aria-describedby="provider-login-identifier-help"
              />
            </label>
            <p id="provider-login-identifier-help" className="provider-auth-helper">
              We will send a verification code to the registered contact on file.
            </p>
            {error ? (
              <p className="provider-auth-error" role="alert">
                {error}
              </p>
            ) : null}
            <div className="provider-auth-actions">
              <button className="primary-button" type="submit" disabled={sending}>
                {sending ? "Sending..." : "Send Verification Code"}
              </button>
              <div className="provider-auth-secondary">
                <small>New to Jeevanam Discover?</small>
                <Link className="secondary-button" to={DISCOVER_ROUTES.listPractice.path}>
                  List Your Practice
                </Link>
              </div>
            </div>
          </form>
        ) : (
          <form className="provider-auth-form" onSubmit={verifyCode}>
            <div className="provider-auth-state-heading">
              <span className="eyebrow">Enter your verification code</span>
              <p>We sent a code to {maskedRecipient || "your registered contact"}.</p>
            </div>
            <div className="provider-auth-contact-card">
              <span className="provider-auth-contact-label">{channelLabel(activeChallenge?.channel ?? null)}</span>
              <strong>{maskedRecipient || "Awaiting code entry"}</strong>
              <small>{activeChallenge?.message ?? "If this contact is registered and verified, a verification code has been sent."}</small>
            </div>

            <label className="provider-auth-field">
              <span>Verification code</span>
              <input
                ref={codeInputRef}
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={6}
                className="provider-auth-code-input"
                value={code}
                onChange={(event) => {
                  setCode(event.target.value.replace(/\D/g, "").slice(0, 6));
                  if (error) {
                    setError(null);
                  }
                }}
                onPaste={(event) => {
                  const pasted = event.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
                  if (pasted) {
                    event.preventDefault();
                    setCode(pasted);
                    if (error) {
                      setError(null);
                    }
                  }
                }}
                autoComplete="one-time-code"
                placeholder="000000"
                aria-describedby="provider-login-code-help"
              />
            </label>
            <p id="provider-login-code-help" className="provider-auth-helper">
              Enter the six-digit code we sent to {maskedRecipient || "your registered contact"}.
            </p>

            {activeChallenge?.verificationMode === "LOCAL" && activeChallenge.developmentCode ? (
              <section className="provider-login-dev-code provider-auth-dev-panel" role="status" aria-live="polite">
                <strong>Development verification code</strong>
                <p className="provider-login-dev-code-value">{activeChallenge.developmentCode}</p>
                <small>Shown only in Local/UAT. Local test code.</small>
              </section>
            ) : null}

            {error ? (
              <p className="provider-auth-error" role="alert">
                {error}
              </p>
            ) : null}

            <div className="provider-auth-status-row">
              <span>{expiresInSeconds > 0 ? `Code expires in ${formatCountdown(expiresInSeconds)}` : "Code expired"}</span>
              <span>{resendInSeconds > 0 ? `Resend available in ${formatCountdown(resendInSeconds)}` : "You can request another code now."}</span>
            </div>

            <div className="provider-auth-actions">
              <button className="primary-button" type="submit" disabled={!canVerify || verifying}>
                {verifying ? "Verifying..." : "Verify and Continue"}
              </button>
              <div className="provider-auth-inline-actions">
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => void sendCode()}
                  disabled={sending || resendInSeconds > 0}
                >
                  {sending ? "Sending..." : "Resend Code"}
                </button>
                <button
                  className="ghost-button"
                  type="button"
                  onClick={() => {
                    clearChallengeState("identifier");
                  }}
                >
                  Change Email or Mobile Number
                </button>
              </div>
            </div>
          </form>
        )}
      </article>
    </section>
  );
}
