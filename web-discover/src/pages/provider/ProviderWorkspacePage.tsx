import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { createProviderOnboardingAccess, type ProviderWorkspaceApplication } from "../../api/providerAuth";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { useProviderSession } from "../../context/ProviderSessionContext";
import { DISCOVER_ROUTES } from "../../routes";
import { providerOnboardingStepRoute } from "../../features/provider/providerOnboardingRoutes";

const TOKEN_KEY = "jeevanam.discover.providerOnboardingToken";
const TOKEN_KEYS = [
  TOKEN_KEY,
  `${TOKEN_KEY}.INDIVIDUAL_DOCTOR`,
  `${TOKEN_KEY}.CLINIC`,
  `${TOKEN_KEY}.HOSPITAL`,
];

function statusLabel(status: ProviderWorkspaceApplication["status"]) {
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
    case "DIAGNOSTIC_CENTRE":
      return "Diagnostic Centre";
    default:
      return providerType.replaceAll("_", " ").replace(/\b\w/g, (char) => char.toUpperCase());
  }
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return "Not yet updated";
  }
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function maskContact(value: string | null, kind: "email" | "phone") {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  if (kind === "email") {
    const [localPart, domain = ""] = trimmed.split("@");
    const prefix = localPart.slice(0, 1) || "";
    return `${prefix}${"*".repeat(Math.max(0, Math.min(8, localPart.length - 1)))}@${domain}`;
  }
  const digits = trimmed.replace(/[^0-9]/g, "");
  return `${"*".repeat(Math.max(0, digits.length - 4))}${digits.slice(-4)}`;
}

function isAttentionApplication(application: ProviderWorkspaceApplication) {
  return application.requiresAttention || application.completionPercent < 100;
}

function primaryActionLabel(application: ProviderWorkspaceApplication) {
  if (application.status === "PUBLISHED") {
    return "Open public profile";
  }
  if (isAttentionApplication(application)) {
    return "Continue registration";
  }
  return "View details";
}

function primaryActionHref(application: ProviderWorkspaceApplication) {
  if (application.status === "PUBLISHED") {
    return application.publicProfilePath ?? DISCOVER_ROUTES.providerWorkspace.path;
  }
  return DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(application.referenceNumber));
}

function supportText(application: ProviderWorkspaceApplication) {
  if (application.status === "PUBLISHED") {
    return "Published profile";
  }
  if (isAttentionApplication(application)) {
    return `Complete your ${providerTypeLabel(application.providerType)} profile`;
  }
  return `${providerTypeLabel(application.providerType)} profile`;
}

function currentStepLabel(step: string) {
  return step.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function attentionSubtitle(application: ProviderWorkspaceApplication) {
  return `${application.completionPercent}% complete · ${application.missingRequirementCount} required items remaining`;
}

export function ProviderWorkspacePage() {
  const navigate = useNavigate();
  const { workspace, logout } = useProviderSession();
  const [loggingOut, setLoggingOut] = useState(false);
  const [openingReference, setOpeningReference] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const activeApplications = workspace?.applications ?? [];
  const publishedProfiles = workspace?.publishedProfiles ?? [];
  const supportedProviderTypes = workspace?.supportedProviderTypes ?? [];
  const attentionItems = useMemo(
    () => [...activeApplications]
      .filter(isAttentionApplication)
      .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()),
    [activeApplications],
  );
  const recentActivity = useMemo(
    () => [...activeApplications, ...publishedProfiles]
      .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
      .slice(0, 5),
    [activeApplications, publishedProfiles],
  );

  const summaryCards = [
    { label: "Active applications", value: activeApplications.length },
    { label: "Published profiles", value: publishedProfiles.length },
    { label: "Items needing attention", value: workspace?.attentionCount ?? attentionItems.length },
    { label: "Supported profile types", value: supportedProviderTypes.length },
  ];

  const emailSummary = maskContact(workspace?.contactEmail ?? null, "email");
  const phoneSummary = maskContact(workspace?.contactPhone ?? null, "phone");

  async function endSession(targetPath: string) {
    setLoggingOut(true);
    setError(null);
    try {
      await logout();
      navigate(targetPath, { replace: true });
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not end your provider session right now.");
    } finally {
      setLoggingOut(false);
    }
  }

  async function continueRegistration(application: ProviderWorkspaceApplication) {
    setOpeningReference(application.referenceNumber);
    setError(null);
    try {
      const access = await createProviderOnboardingAccess(application.referenceNumber);
      for (const key of TOKEN_KEYS) {
        localStorage.removeItem(key);
      }
      localStorage.setItem(TOKEN_KEY, access.onboardingToken);
      navigate(`/provider/onboarding/${access.applicationId}/${providerOnboardingStepRoute(application.currentStep)}`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not open the selected application.");
    } finally {
      setOpeningReference(null);
    }
  }

  if (!workspace) {
    return null;
  }

  const hasAnyApplication = activeApplications.length > 0 || publishedProfiles.length > 0;

  return (
    <section className="page-section provider-account-page">
      <header className="provider-account-header">
        <div className="provider-account-heading">
          <span className="eyebrow">Provider account</span>
          <h1>Manage your applications and published profiles.</h1>
          <p>Review what still needs attention, continue the right onboarding step, and keep published profiles separate from unfinished drafts.</p>
        </div>
        <aside className="provider-account-session-card">
          <div className="provider-account-session-row">
            <strong>{emailSummary ?? phoneSummary ?? "Verified provider account"}</strong>
            <span className="provider-account-session-pill">Active session</span>
          </div>
          <p>{phoneSummary ? `Verified mobile ending ${phoneSummary.slice(-4)}` : "Provider session is active and ready."}</p>
          <div className="cta-row">
            <button className="primary-button" type="button" onClick={() => void endSession(DISCOVER_ROUTES.providerLogin.path)} disabled={loggingOut}>
              Logout
            </button>
            <button className="secondary-button" type="button" onClick={() => void endSession(DISCOVER_ROUTES.providerLogin.path)} disabled={loggingOut}>
              Switch account
            </button>
          </div>
        </aside>
      </header>

      {error ? <p className="inline-error" role="alert">{error}</p> : null}

      <section className="provider-account-summary-grid" aria-label="Provider account summary">
        {summaryCards.map((card) => (
          <article className="provider-account-summary-card" key={card.label}>
            <span>{card.label}</span>
            <strong>{card.value}</strong>
          </article>
        ))}
      </section>

      <section className="provider-account-section">
        <div className="provider-account-section-heading">
          <div>
            <h2>Attention</h2>
            <p>These applications are incomplete or waiting on your next action.</p>
          </div>
          {supportedProviderTypes.length ? (
            <Link className="primary-button" to={`${DISCOVER_ROUTES.listPractice.path}?mode=add`}>
              Add another profile
            </Link>
          ) : null}
        </div>
        {attentionItems.length ? (
          <div className="provider-account-attention-list">
            {attentionItems.map((application) => (
              <article className="provider-account-attention-item" key={`${application.id}-attention`}>
                <div className="provider-account-attention-copy">
                  <strong>{supportText(application)}</strong>
                  <p>{application.referenceNumber}</p>
                  <span>{attentionSubtitle(application)}</span>
                  <small>Current step: {currentStepLabel(application.currentStep)}</small>
                </div>
                <div className="provider-account-attention-meta">
                  <span>{statusLabel(application.status)}</span>
                  <div className="cta-row">
                    {application.status === "PUBLISHED" ? null : (
                      <button
                        className="primary-button"
                        type="button"
                        onClick={() => void continueRegistration(application)}
                        disabled={openingReference === application.referenceNumber}
                      >
                        Continue registration
                      </button>
                    )}
                    <Link className="secondary-button" to={primaryActionHref(application)}>
                      View details
                    </Link>
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <DiscoverEmptyState
            icon="◌"
            title="No actions currently require your attention."
            description="Finished or published profiles will stay visible below. Any incomplete application will appear here."
            variant="compact"
          />
        )}
      </section>

      <section className="provider-account-section">
        <div className="provider-account-section-heading">
          <div>
            <h2>My applications</h2>
            <p>Active applications are shown here. Published profiles remain separate.</p>
          </div>
          {hasAnyApplication && supportedProviderTypes.length ? (
            <Link className="secondary-button" to={`${DISCOVER_ROUTES.listPractice.path}?mode=add`}>
              Add another profile
            </Link>
          ) : null}
        </div>
        {activeApplications.length ? (
          <div className="provider-account-application-grid">
            {activeApplications.map((application) => (
              <article className="provider-account-application-card" key={application.id}>
                <div className="provider-account-card-header">
                  <div>
                    <strong>{supportText(application)}</strong>
                    <p>{providerTypeLabel(application.providerType)}</p>
                  </div>
                  <span className="provider-account-status-pill">{statusLabel(application.status)}</span>
                </div>
                <dl className="provider-account-detail-list">
                  <div>
                    <dt>Business reference</dt>
                    <dd>{application.referenceNumber}</dd>
                  </div>
                  <div>
                    <dt>Completion</dt>
                    <dd>{application.completionPercent}% complete</dd>
                  </div>
                  <div>
                    <dt>Current step</dt>
                    <dd>{currentStepLabel(application.currentStep)}</dd>
                  </div>
                  <div>
                    <dt>Missing items</dt>
                    <dd>{application.missingRequirementCount}</dd>
                  </div>
                  <div>
                    <dt>Last updated</dt>
                    <dd>{formatDateTime(application.updatedAt)}</dd>
                  </div>
                </dl>
                <div className="provider-account-card-actions">
                  {isAttentionApplication(application) ? (
                    <button
                      className="primary-button"
                      type="button"
                      onClick={() => void continueRegistration(application)}
                      disabled={openingReference === application.referenceNumber}
                    >
                      Continue registration
                    </button>
                  ) : null}
                  <Link className={isAttentionApplication(application) ? "secondary-button" : "primary-button"} to={primaryActionHref(application)}>
                    View details
                  </Link>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <DiscoverEmptyState
            icon="◌"
            title="No applications are linked to this provider account yet"
            description="Start a doctor, clinic, or hospital registration to see it appear here."
            primaryAction="Start registration"
            primaryTo={DISCOVER_ROUTES.listPractice.path}
          />
        )}
      </section>

      <div className="provider-account-two-column">
        <section className="provider-account-section">
          <div className="provider-account-section-heading">
            <div>
              <h2>My public profiles</h2>
              <p>Published profiles that are already visible in Discover.</p>
            </div>
          </div>
          {publishedProfiles.length ? (
            <div className="provider-account-profile-grid">
              {publishedProfiles.map((application) => (
                <article className="provider-account-profile-card" key={`${application.id}-profile`}>
                  <div className="provider-account-card-header">
                    <div>
                      <strong>{providerTypeLabel(application.providerType)}</strong>
                      <p>{application.displayName}</p>
                    </div>
                    <span className="provider-account-status-pill">Published</span>
                  </div>
                  <dl className="provider-account-detail-list">
                    <div>
                      <dt>Public path</dt>
                      <dd>{application.publicProfilePath}</dd>
                    </div>
                    <div>
                      <dt>Updated</dt>
                      <dd>{formatDateTime(application.updatedAt)}</dd>
                    </div>
                  </dl>
                  <div className="provider-account-card-actions">
                    <Link className="secondary-button" to={application.publicProfilePath ?? DISCOVER_ROUTES.providerLandingPage.path}>
                      Open public profile
                    </Link>
                    <Link className="secondary-button" to={DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(application.referenceNumber))}>
                      View details
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <DiscoverEmptyState
              icon="□"
              title="No published profiles yet"
              description="Published provider profiles will appear here after review and publication."
              variant="compact"
            />
          )}
        </section>

        <section className="provider-account-section">
          <div className="provider-account-section-heading">
            <div>
              <h2>Recent activity</h2>
              <p>Recent provider workspace changes ordered by the latest update.</p>
            </div>
          </div>
          {recentActivity.length ? (
            <div className="provider-account-activity-list">
              {recentActivity.map((application) => (
                <article className="provider-account-activity-item" key={`${application.id}-activity`}>
                  <strong>{supportText(application)}</strong>
                  <p>{application.referenceNumber}</p>
                  <span>{statusLabel(application.status)} · {application.completionPercent}% complete</span>
                  <small>{formatDateTime(application.updatedAt)}</small>
                </article>
              ))}
            </div>
          ) : (
            <DiscoverEmptyState
              icon="·"
              title="No recent activity"
              description="Provider application updates will appear here once work begins."
              variant="compact"
            />
          )}
        </section>
      </div>
    </section>
  );
}
