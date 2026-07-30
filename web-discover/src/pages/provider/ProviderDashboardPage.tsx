import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  createProviderOnboardingAccess,
  loadProviderApplicationDashboard,
} from "../../api/providerAuth";
import type { ProviderDashboard, ProviderStatus } from "../../api/providerOnboarding";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { DISCOVER_ROUTES } from "../../routes";

const TOKEN_KEY = "jeevanam.discover.providerOnboardingToken";
const TOKEN_KEYS = [
  TOKEN_KEY,
  `${TOKEN_KEY}.INDIVIDUAL_DOCTOR`,
  `${TOKEN_KEY}.CLINIC`,
  `${TOKEN_KEY}.HOSPITAL`,
];

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
  const { applicationReference } = useParams<{ applicationReference: string }>();
  const [dashboard, setDashboard] = useState<ProviderDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [openingOnboarding, setOpeningOnboarding] = useState(false);

  useEffect(() => {
    if (!applicationReference) {
      setDashboard(null);
      return;
    }
    setLoading(true);
    setError(null);
    setDashboard(null);
    loadProviderApplicationDashboard(applicationReference)
      .then((result) => {
        setDashboard(result);
      })
      .catch((ex) => {
        setError(ex instanceof Error ? ex.message : "Could not load the selected provider application.");
        setDashboard(null);
      })
      .finally(() => setLoading(false));
  }, [applicationReference]);

  async function openOnboarding(step: string) {
    if (!applicationReference || !dashboard) return;
    setOpeningOnboarding(true);
    setError(null);
    try {
      const access = await createProviderOnboardingAccess(applicationReference);
      for (const key of TOKEN_KEYS) {
        localStorage.removeItem(key);
      }
      localStorage.setItem(TOKEN_KEY, access.onboardingToken);
      navigate(`/provider/onboarding/${access.applicationId}/${step}`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not open the selected application.");
    } finally {
      setOpeningOnboarding(false);
    }
  }

  if (error && !dashboard) {
    return (
      <section className="page-section provider-dashboard-page">
        <DiscoverEmptyState
          icon="!"
          title="We could not load this provider application"
          description={error}
          primaryAction="Try again"
          primaryHref={window.location.href}
          secondaryAction="Back to Provider Workspace"
          secondaryTo={DISCOVER_ROUTES.providerWorkspace.path}
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
            <button className="secondary-button" type="button" onClick={() => void openOnboarding(continueStep)} disabled={openingOnboarding}>
              Continue registration
            </button>
            <button className="secondary-button" type="button" onClick={() => void openOnboarding("preview")} disabled={openingOnboarding}>
              Preview profile
            </button>
            <button className="primary-button" type="button" onClick={() => void openOnboarding("submit")} disabled={openingOnboarding}>
              Submit
            </button>
            {application.status === "PUBLISHED" ? (
              <Link className="secondary-button" to={DISCOVER_ROUTES.providerLandingPage.path}>Landing page</Link>
            ) : null}
          </div>
          {dashboard.nextRecommendedAction === "Address requested changes" && !readOnly ? (
            <button className="primary-button" type="button" onClick={() => void openOnboarding("submit")} disabled={openingOnboarding}>
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

      {(loading || openingOnboarding) ? <p className="autosave-row" role="status">{openingOnboarding ? "Opening application…" : "Updating dashboard…"}</p> : null}
    </section>
  );
}
