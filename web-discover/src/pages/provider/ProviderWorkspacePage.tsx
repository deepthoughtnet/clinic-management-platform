import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { ProviderWorkspaceApplication } from "../../api/providerAuth";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { useProviderSession } from "../../context/ProviderSessionContext";
import { DISCOVER_ROUTES } from "../../routes";

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
    default:
      return providerType.replaceAll("_", " ");
  }
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return "Not yet updated";
  }
  return new Date(value).toLocaleString();
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

function currentStepLabel(step: string) {
  return step.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function primaryActionLabel(status: ProviderWorkspaceApplication["status"]) {
  switch (status) {
    case "DRAFT":
      return "Continue application";
    case "CHANGES_REQUESTED":
      return "Address requested changes";
    case "SUBMITTED":
      return "View submission";
    case "UNDER_REVIEW":
      return "View status";
    case "APPROVED":
      return "Open publication setup";
    case "PUBLISHED":
      return "Open public profile";
    default:
      return "View application";
  }
}

function primaryActionHref(application: ProviderWorkspaceApplication) {
  switch (application.status) {
    case "APPROVED":
      return DISCOVER_ROUTES.providerLandingPage.path;
    case "PUBLISHED":
      return application.publicProfilePath ?? DISCOVER_ROUTES.providerLandingPage.path;
    default:
      return DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(application.referenceNumber));
  }
}

function attentionReason(application: ProviderWorkspaceApplication) {
  switch (application.status) {
    case "DRAFT":
      return "Continue the incomplete application.";
    case "CHANGES_REQUESTED":
      return "Review feedback and resubmit.";
    case "APPROVED":
      return "Complete publication setup for the approved profile.";
    default:
      return null;
  }
}

function activityTitle(application: ProviderWorkspaceApplication) {
  switch (application.status) {
    case "PUBLISHED":
      return "Profile published";
    case "APPROVED":
      return "Profile approved";
    case "UNDER_REVIEW":
      return "Review in progress";
    case "SUBMITTED":
      return "Application submitted";
    case "CHANGES_REQUESTED":
      return "Changes requested";
    case "DRAFT":
      return "Draft updated";
    default:
      return "Application updated";
  }
}

export function ProviderWorkspacePage() {
  const navigate = useNavigate();
  const { workspace, logout } = useProviderSession();
  const [loggingOut, setLoggingOut] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const applications = workspace?.applications ?? [];
  const publishedProfiles = useMemo(
    () => applications.filter((item) => item.status === "PUBLISHED" && item.publicProfilePath),
    [applications],
  );
  const attentionItems = useMemo(
    () => applications.filter((item) => attentionReason(item)),
    [applications],
  );
  const recentActivity = useMemo(
    () => [...applications]
      .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
      .slice(0, 5),
    [applications],
  );

  const summaryCards = [
    { label: "Total applications", value: applications.length },
    { label: "Draft applications", value: applications.filter((item) => item.status === "DRAFT").length },
    { label: "Submitted applications", value: applications.filter((item) => item.status === "SUBMITTED" || item.status === "UNDER_REVIEW").length },
    { label: "Published profiles", value: publishedProfiles.length },
    { label: "Items needing attention", value: attentionItems.length },
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

  if (!workspace) {
    return null;
  }

  return (
    <section className="page-section provider-account-page">
      <header className="provider-account-header">
        <div className="provider-account-heading">
          <span className="eyebrow">Provider account</span>
          <h1>Manage your applications, published profiles, and account access.</h1>
          <p>Review the status of every provider application, reopen the next task quickly, and monitor what is already visible to patients.</p>
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
            <p>Clear the next provider tasks that are waiting on you.</p>
          </div>
        </div>
        {attentionItems.length ? (
          <div className="provider-account-attention-list">
            {attentionItems.map((application) => (
              <article className="provider-account-attention-item" key={`${application.id}-attention`}>
                <div>
                  <strong>{application.displayName}</strong>
                  <p>{attentionReason(application)}</p>
                </div>
                <div className="provider-account-attention-meta">
                  <span>{statusLabel(application.status)}</span>
                  <Link className="secondary-button" to={primaryActionHref(application)}>
                    {primaryActionLabel(application.status)}
                  </Link>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <DiscoverEmptyState
            icon="✓"
            title="No actions currently require your attention."
            description="Your provider workspace is up to date. New review requests or incomplete drafts will appear here."
            variant="compact"
          />
        )}
      </section>

      <section className="provider-account-section">
        <div className="provider-account-section-heading">
          <div>
            <h2>My applications</h2>
            <p>Continue each onboarding flow or review its current lifecycle state.</p>
          </div>
        </div>
        {applications.length ? (
          <div className="provider-account-application-grid">
            {applications.map((application) => (
              <article className="provider-account-application-card" key={application.id}>
                <div className="provider-account-card-header">
                  <div>
                    <strong>{application.displayName}</strong>
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
                    <dt>Current step</dt>
                    <dd>{currentStepLabel(application.currentStep)}</dd>
                  </div>
                  <div>
                    <dt>Completion</dt>
                    <dd>{application.completionPercent}% complete</dd>
                  </div>
                  <div>
                    <dt>Last updated</dt>
                    <dd>{formatDateTime(application.updatedAt)}</dd>
                  </div>
                </dl>
                <div className="provider-account-card-actions">
                  <Link className="primary-button" to={primaryActionHref(application)}>
                    {primaryActionLabel(application.status)}
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
                      <strong>{application.displayName}</strong>
                      <p>{providerTypeLabel(application.providerType)}</p>
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
                    <Link className="secondary-button" to={application.publicProfilePath ?? DISCOVER_ROUTES.doctors.path}>
                      Open public profile
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
              <h2>Recent Activity</h2>
              <p>Recent provider workspace changes ordered by the latest update.</p>
            </div>
          </div>
          {recentActivity.length ? (
            <div className="provider-account-activity-list">
              {recentActivity.map((application) => (
                <article className="provider-account-activity-item" key={`${application.id}-activity`}>
                  <strong>{activityTitle(application)}</strong>
                  <p>{application.displayName}</p>
                  <span>{statusLabel(application.status)} · {application.referenceNumber}</span>
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
