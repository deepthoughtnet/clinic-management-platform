import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  createProviderOnboardingAccess,
  discardWorkspaceApplication,
  loadProviderApplicationDashboard,
} from "../../api/providerAuth";
import type { ProviderDashboard, ProviderStatus } from "../../api/providerOnboarding";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { useProviderSession } from "../../context/ProviderSessionContext";
import { DISCOVER_ROUTES } from "../../routes";
import { groupProviderRequirements, providerRequirementLabel } from "../../features/provider/providerRequirementLabels";
import { providerOnboardingStepRoute } from "../../features/provider/providerOnboardingRoutes";

const TOKEN_KEY = "jeevanam.discover.providerOnboardingToken";
const TOKEN_KEYS = [
  TOKEN_KEY,
  `${TOKEN_KEY}.INDIVIDUAL_DOCTOR`,
  `${TOKEN_KEY}.CLINIC`,
  `${TOKEN_KEY}.HOSPITAL`,
];

function formatDateTime(value?: string | null) {
  if (!value) return "Not yet saved";
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function statusLabel(status: ProviderStatus) {
  return status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function providerTypeLabel(providerType: string) {
  switch (providerType) {
    case "INDIVIDUAL_DOCTOR":
      return "Individual Doctor";
    case "CLINIC":
      return "Clinic";
    case "HOSPITAL":
      return "Hospital";
    default:
      return providerType.replaceAll("_", " ").replace(/\b\w/g, (char) => char.toUpperCase());
  }
}

function stepLabel(step: string) {
  return step.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function currentTimelineLabel(index: number, total: number) {
  return index === total - 1 ? "Current" : "Completed";
}

export function ProviderDashboardPage() {
  const navigate = useNavigate();
  const { applicationReference } = useParams<{ applicationReference: string }>();
  const { refreshSession } = useProviderSession();
  const [dashboard, setDashboard] = useState<ProviderDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [openingOnboarding, setOpeningOnboarding] = useState(false);
  const [discardOpen, setDiscardOpen] = useState(false);
  const [discardReason, setDiscardReason] = useState("");
  const [discardBusy, setDiscardBusy] = useState(false);
  const [discardError, setDiscardError] = useState<string | null>(null);

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

  const blockedRequirements = useMemo(
    () => groupProviderRequirements(dashboard?.completion.blockingErrors ?? []),
    [dashboard],
  );

  const canDiscard = Boolean(dashboard && ["DRAFT", "CONTACT_VERIFIED", "PROFILE_INCOMPLETE", "READY_FOR_REVIEW", "CHANGES_REQUESTED"].includes(dashboard.application.status));
  const previewReady = Boolean(dashboard?.completion.previewReady);
  const canSubmit = Boolean(dashboard?.completion.canSubmit && !dashboard.readOnly);

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
      navigate(`/provider/onboarding/${access.applicationId}/${providerOnboardingStepRoute(step)}`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not open the selected application.");
    } finally {
      setOpeningOnboarding(false);
    }
  }

  async function confirmDiscard() {
    if (!applicationReference || !dashboard) return;
    setDiscardBusy(true);
    setDiscardError(null);
    try {
      await discardWorkspaceApplication(applicationReference, discardReason.trim() || undefined);
      await refreshSession(true);
      navigate(DISCOVER_ROUTES.providerWorkspace.path, { replace: true });
    } catch (ex) {
      setDiscardError(ex instanceof Error ? ex.message : "Could not discard this onboarding right now.");
    } finally {
      setDiscardBusy(false);
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
  const continueStep = completion.currentStep;
  const submitLabel = application.status === "CHANGES_REQUESTED" ? "Resubmit for review" : "Submit";
  const missingCount = completion.blockingErrors.length;

  return (
    <section className="page-section provider-dashboard-page">
      <header className="provider-dashboard-hero">
        <div className="provider-dashboard-hero-copy">
          <span className="eyebrow">Provider application</span>
          <h1>Your {providerTypeLabel(application.providerType)} application</h1>
          <p>{application.referenceNumber}</p>
          <div className="provider-dashboard-meta-row">
            <span>{statusLabel(application.status)}</span>
            <span>{completion.completionPercentage}% complete</span>
          </div>
        </div>
        <div className="resume-card">
          <strong>{completion.completionPercentage}% complete</strong>
          <span>Current step: {stepLabel(completion.currentStep)}</span>
          <div className="progress-track" aria-label={`${completion.completionPercentage}% complete`}>
            <span style={{ width: `${completion.completionPercentage}%` }} />
          </div>
          <small>{completion.incompleteSteps.length} step{completion.incompleteSteps.length === 1 ? "" : "s"} remaining</small>
        </div>
      </header>

      <div className="provider-dashboard-toolbar">
        <button className="primary-button" type="button" onClick={() => void openOnboarding(continueStep)} disabled={openingOnboarding || dashboard.readOnly}>
          Continue registration
        </button>
        <details className="provider-dashboard-actions">
          <summary className="secondary-button">More actions</summary>
          <div className="provider-dashboard-actions-menu">
            <button className="secondary-button" type="button" onClick={() => setDiscardOpen(true)} disabled={!canDiscard}>
              Discard onboarding
            </button>
          </div>
        </details>
        <button className="secondary-button" type="button" onClick={() => void openOnboarding("preview")} disabled={!previewReady || openingOnboarding}>
          Preview profile
        </button>
        {canSubmit ? (
          <button className="primary-button" type="button" onClick={() => void openOnboarding("submit")} disabled={openingOnboarding}>
            {submitLabel}
          </button>
        ) : (
          <span className="provider-dashboard-helper">Complete all required items before submission.</span>
        )}
        {!previewReady ? <span className="provider-dashboard-helper">Preview is available once the profile has enough information to render safely.</span> : null}
      </div>

      {dashboard.changeRequests.length ? (
        <article className="provider-dashboard-panel">
          <h2>Requested changes</h2>
          {dashboard.changeRequests.map((request) => (
            <div className="change-request-item" key={request.id}>
              <strong>Changes requested</strong>
              <p>{request.reviewerMessage ?? "Review team feedback"}</p>
              {request.requestedSections.length ? <small>{request.requestedSections.join(", ")}</small> : null}
              {request.providerResponseNote ? <small>Response: {request.providerResponseNote}</small> : null}
            </div>
          ))}
        </article>
      ) : null}

      <div className="provider-dashboard-layout">
        <article className="provider-dashboard-panel">
          <h2>What remains</h2>
          <p>{missingCount} required items remaining</p>
          {missingCount ? (
            <div className="submission-blocker-groups">
              {(["Organisation", "Services", "Locations", "Branding", "Account", "Other"] as const).map((group) => {
                const items = blockedRequirements[group] ?? [];
                if (!items.length) {
                  return null;
                }
                return (
                  <section key={group}>
                    <strong>{group}</strong>
                    {items.map((item) => (
                      <span key={item}>{providerRequirementLabel(item)}</span>
                    ))}
                  </section>
                );
              })}
            </div>
          ) : (
            <p>No required items remain.</p>
          )}
        </article>

        <article className="provider-dashboard-panel">
          <h2>Status timeline</h2>
          <div className="status-timeline vertical">
            {dashboard.timeline.map((event, index) => (
              <div key={`${event.label}-${event.timestamp}`}>
                <div className="provider-dashboard-timeline-header">
                  <strong>{event.label}</strong>
                  <span>{currentTimelineLabel(index, dashboard.timeline.length)}</span>
                </div>
                {event.description ? <p>{event.description}</p> : null}
                <small>{formatDateTime(event.timestamp)}</small>
              </div>
            ))}
          </div>
        </article>
      </div>

      {discardOpen ? (
        <div className="provider-dashboard-modal" role="dialog" aria-modal="true" aria-labelledby="discard-onboarding-title">
          <div className="provider-dashboard-modal-card">
            <h2 id="discard-onboarding-title">Discard onboarding</h2>
            <p>This application will be removed from active onboarding. Published profiles and other applications will not be affected.</p>
            <label>
              Reason, optional
              <textarea value={discardReason} onChange={(event) => setDiscardReason(event.target.value)} placeholder="Tell us why you are discarding this onboarding" />
            </label>
            {discardError ? <p className="inline-error" role="alert">{discardError}</p> : null}
            <div className="cta-row">
              <button className="secondary-button" type="button" onClick={() => setDiscardOpen(false)} disabled={discardBusy}>
                Cancel
              </button>
              <button className="primary-button" type="button" onClick={() => void confirmDiscard()} disabled={discardBusy}>
                Discard onboarding
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {(loading || openingOnboarding || discardBusy) ? <p className="autosave-row" role="status">{discardBusy ? "Discarding onboarding…" : openingOnboarding ? "Opening application…" : "Updating dashboard…"}</p> : null}
    </section>
  );
}
