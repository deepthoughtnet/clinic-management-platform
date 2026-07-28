import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  loadProviderDashboard,
  resubmitProviderApplication,
  type ProviderDashboard,
  type ProviderStatus,
} from "../../api/providerOnboarding";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { DISCOVER_ROUTES } from "../../routes";

const TOKEN_KEYS = [
  "jeevanam.discover.providerOnboardingToken.INDIVIDUAL_DOCTOR",
  "jeevanam.discover.providerOnboardingToken.CLINIC",
  "jeevanam.discover.providerOnboardingToken.HOSPITAL",
  "jeevanam.discover.providerOnboardingToken",
];

function readStoredToken() {
  for (const key of TOKEN_KEYS) {
    const token = localStorage.getItem(key);
    if (token) return token;
  }
  return "";
}

function formatDateTime(value?: string | null) {
  if (!value) return "Not yet saved";
  return new Date(value).toLocaleString();
}

function stepRoute(step: string) {
  const normalized = step.toUpperCase();
  if (normalized.includes("ACCOUNT")) return "account";
  if (normalized.includes("PROFILE")) return "organisation";
  if (normalized.includes("ORGANISATION")) return "organisation";
  if (normalized.includes("PROFESSIONAL")) return "professional";
  if (normalized.includes("DETAILS")) return "professional";
  if (normalized.includes("SERVICES")) return "services";
  if (normalized.includes("LOCATION")) return "locations";
  if (normalized.includes("BRANDING")) return "branding";
  if (normalized.includes("DOCUMENT")) return "branding";
  if (normalized.includes("PREVIEW")) return "preview";
  return "submit";
}

function statusLabel(status: ProviderStatus) {
  return status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

export function ProviderDashboardPage() {
  const navigate = useNavigate();
  const [token, setToken] = useState(() => readStoredToken());
  const [dashboard, setDashboard] = useState<ProviderDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [responseNote, setResponseNote] = useState("");

  useEffect(() => {
    if (!token) {
      setDashboard(null);
      return;
    }
    setLoading(true);
    setError(null);
    loadProviderDashboard(token)
      .then((result) => {
        setDashboard(result);
        setResponseNote(result.changeRequests.find((item) => !item.resolved)?.providerResponseNote ?? "");
      })
      .catch((ex) => {
        setError(ex instanceof Error ? ex.message : "Could not load your onboarding dashboard.");
        setDashboard(null);
      })
      .finally(() => setLoading(false));
  }, [token]);

  const activeRequest = useMemo(() => dashboard?.changeRequests.find((item) => !item.resolved) ?? null, [dashboard]);

  async function resubmit() {
    if (!dashboard || !token) return;
    setLoading(true);
    setError(null);
    try {
      const updated = await resubmitProviderApplication(dashboard.application.id, token, responseNote || undefined);
      setDashboard({ ...dashboard, application: updated, readOnly: updated.status === "SUBMITTED" || updated.status === "UNDER_REVIEW" });
      setResponseNote("");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not resubmit the application.");
    } finally {
      setLoading(false);
    }
  }

  if (!token) {
    return (
      <section className="page-section provider-dashboard-page">
        <DiscoverEmptyState
          icon="★"
          title="Start your provider onboarding"
          description="Create a doctor, clinic, or hospital application and continue in your provider portal."
          primaryAction="Register as Doctor"
          primaryTo={DISCOVER_ROUTES.registerDoctor.path}
          secondaryAction="Register a Clinic"
          secondaryTo={DISCOVER_ROUTES.registerClinic.path}
        />
      </section>
    );
  }

  if (error && !dashboard) {
    return (
      <section className="page-section provider-dashboard-page">
        <DiscoverEmptyState
          icon="!"
          title="We could not load your provider dashboard"
          description={error}
          primaryAction="Try again"
          primaryHref={window.location.href}
          secondaryAction="Back to practice registration"
          secondaryTo={DISCOVER_ROUTES.listPractice.path}
        />
      </section>
    );
  }

  if (!dashboard) {
    return (
      <section className="page-section provider-dashboard-page">
        <div className="provider-dashboard-skeleton" role="status" aria-label="Loading provider dashboard">
          <span />
          <span />
          <span />
        </div>
      </section>
    );
  }

  const { application, completion } = dashboard;
  const continueStep = stepRoute(completion.recommendedNextStep);
  const readOnly = dashboard.readOnly;

  return (
    <section className="page-section provider-dashboard-page">
      <div className="provider-dashboard-hero">
        <div>
          <span className="eyebrow">Provider dashboard</span>
          <h1>{application.displayName ?? application.legalName ?? "Your application"}</h1>
          <p>{application.referenceNumber} · {statusLabel(application.status)}</p>
        </div>
        <div className="resume-card">
          <strong>{completion.completionPercentage}% complete</strong>
          <span>{completion.recommendedNextStep}</span>
          <div className="progress-track" aria-label={`${completion.completionPercentage}% complete`}>
            <span style={{ width: `${completion.completionPercentage}%` }} />
          </div>
          <small>Last saved {formatDateTime(application.lastSavedAt)}</small>
          {application.submittedAt ? <small>Submitted {formatDateTime(application.submittedAt)}</small> : null}
        </div>
      </div>

      {dashboard.changeRequests.length ? (
        <article className="provider-dashboard-panel">
          <h2>Needs attention</h2>
          {dashboard.changeRequests.map((request) => (
            <div className="change-request-item" key={request.id}>
              <strong>Requested changes</strong>
              <p>{request.reviewerMessage ?? "Review team feedback"}</p>
              {request.requestedSections.length ? <small>{request.requestedSections.join(", ")}</small> : null}
              {request.providerResponseNote ? <small>Response: {request.providerResponseNote}</small> : null}
            </div>
          ))}
          {!readOnly ? (
            <label>
              Response note
              <textarea value={responseNote} onChange={(event) => setResponseNote(event.target.value)} placeholder="Describe the changes you made" />
            </label>
          ) : null}
        </article>
      ) : null}

      <div className="provider-dashboard-layout">
        <article className="provider-dashboard-panel">
          <h2>Completion</h2>
          <div className="completion-summary">
            <span>{completion.completedSteps.length} completed</span>
            <span>{completion.incompleteSteps.length} remaining</span>
            <span>{completion.blockingErrors.length} blockers</span>
          </div>
          <div className="missing-list">
            <strong>Missing requirements</strong>
            {completion.blockingErrors.length ? completion.blockingErrors.map((item) => <span key={item}>{item}</span>) : <span>None</span>}
          </div>
          <div className="cta-row">
            <Link className="secondary-button" to={`/provider/onboarding/${application.id}/${continueStep}`}>Continue registration</Link>
            <Link className="secondary-button" to={`/provider/onboarding/${application.id}/preview`}>Preview profile</Link>
            <Link className="primary-button" to={`/provider/onboarding/${application.id}/submit`}>Submit</Link>
          </div>
          {dashboard.nextRecommendedAction === "Address requested changes" && !readOnly ? (
            <button className="primary-button" type="button" onClick={() => void resubmit()} disabled={loading}>
              Resubmit for review
            </button>
          ) : null}
        </article>

        <article className="provider-dashboard-panel">
          <h2>Status timeline</h2>
          <div className="status-timeline vertical">
            {dashboard.timeline.map((event) => (
              <div key={`${event.label}-${event.timestamp}`}>
                <strong>{event.label}</strong>
                <span>{event.actorCategory}</span>
                {event.description ? <p>{event.description}</p> : null}
                <small>{formatDateTime(event.timestamp)}</small>
              </div>
            ))}
          </div>
        </article>
      </div>

      {loading ? <p className="autosave-row" role="status">Updating dashboard…</p> : null}
    </section>
  );
}
