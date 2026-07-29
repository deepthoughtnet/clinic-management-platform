import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  loadProviderWorkspace,
  logoutProviderSession,
  type ProviderWorkspaceApplication,
  type ProviderWorkspaceResponse,
} from "../../api/providerAuth";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { DISCOVER_ROUTES } from "../../routes";

function statusLabel(status: ProviderWorkspaceApplication["status"]) {
  return status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function primaryActionLabel(status: ProviderWorkspaceApplication["status"]) {
  switch (status) {
    case "DRAFT":
      return "Continue registration";
    case "CHANGES_REQUESTED":
      return "Review feedback and edit";
    case "SUBMITTED":
    case "UNDER_REVIEW":
      return "Track application";
    case "APPROVED":
      return "View approval status";
    case "PUBLISHED":
      return "Open public profile";
    default:
      return "View application";
  }
}

export function ProviderWorkspacePage() {
  const navigate = useNavigate();
  const [workspace, setWorkspace] = useState<ProviderWorkspaceResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    setLoading(true);
    setError(null);
    loadProviderWorkspace()
      .then((result) => setWorkspace(result))
      .catch((ex) => {
        setError(ex instanceof Error ? ex.message : "Could not load your provider workspace.");
        setWorkspace(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const publishedProfiles = useMemo(
    () => workspace?.applications.filter((item) => item.status === "PUBLISHED" && item.publicProfilePath) ?? [],
    [workspace],
  );

  async function logout() {
    setLoggingOut(true);
    try {
      await logoutProviderSession();
      navigate(DISCOVER_ROUTES.providerLogin.path, { replace: true });
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not log you out right now.");
    } finally {
      setLoggingOut(false);
    }
  }

  if (loading) {
    return (
      <section className="page-section provider-dashboard-page">
        <div className="provider-dashboard-skeleton" role="status" aria-label="Loading provider workspace">
          <span />
          <span />
          <span />
        </div>
      </section>
    );
  }

  if (error && !workspace) {
    return (
      <section className="page-section provider-dashboard-page">
        <DiscoverEmptyState
          icon="!"
          title="We could not load your provider workspace"
          description={error}
          primaryAction="Try again"
          primaryHref={window.location.href}
          secondaryAction="Provider login"
          secondaryTo={DISCOVER_ROUTES.providerLogin.path}
        />
      </section>
    );
  }

  if (!workspace) {
    return null;
  }

  const applications = workspace.applications;

  return (
    <section className="page-section provider-dashboard-page">
      <div className="provider-dashboard-hero">
        <div>
          <span className="eyebrow">Provider workspace</span>
          <h1>Manage your Jeevanam Discover applications</h1>
          <p>View owned applications, public profiles, and your account session from one place.</p>
        </div>
        <div className="resume-card">
          <strong>{applications.length} applications</strong>
          <span>Provider account connected</span>
        </div>
      </div>

      {error ? <p className="inline-error" role="alert">{error}</p> : null}

      <div className="provider-dashboard-layout">
        <article className="provider-dashboard-panel">
          <h2>My Applications</h2>
          {applications.length ? (
            <div className="missing-list">
              {applications.map((application) => (
                <div className="change-request-item" key={application.id}>
                  <strong>{application.displayName}</strong>
                  <p>
                    {application.referenceNumber} · {application.providerType} · {statusLabel(application.status)}
                  </p>
                  <small>
                    {application.completionPercent}% complete · {application.currentStep}
                  </small>
                  <small>Updated {new Date(application.updatedAt).toLocaleString()}</small>
                  <div className="cta-row">
                    {application.status === "PUBLISHED" && application.publicProfilePath ? (
                      <Link className="secondary-button" to={application.publicProfilePath}>
                        Open public profile
                      </Link>
                    ) : null}
                    <span className="ghost-button" aria-hidden="true">
                      {primaryActionLabel(application.status)}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <DiscoverEmptyState
              icon="◌"
              title="No applications are linked to this provider account yet"
              description="Once a provider application is verified and linked, it will appear here."
              primaryAction="Start registration"
              primaryTo={DISCOVER_ROUTES.listPractice.path}
            />
          )}
        </article>

        <article className="provider-dashboard-panel">
          <h2>My Public Profiles</h2>
          {publishedProfiles.length ? (
            <div className="missing-list">
              {publishedProfiles.map((application) => (
                <div className="change-request-item" key={`${application.id}-public`}>
                  <strong>{application.displayName}</strong>
                  <p>{application.publicProfilePath}</p>
                  <Link className="secondary-button" to={application.publicProfilePath ?? DISCOVER_ROUTES.clinics.path}>
                    Open profile
                  </Link>
                </div>
              ))}
            </div>
          ) : (
            <p>No published public profiles yet.</p>
          )}
        </article>
      </div>

      <div className="provider-dashboard-layout">
        <article className="provider-dashboard-panel">
          <h2>Account &amp; Security</h2>
          <p>Provider account ID: {workspace.providerAccountId}</p>
          <p>Your session is active and secured with an HttpOnly cookie.</p>
          <div className="cta-row">
            <button className="primary-button" type="button" onClick={() => void logout()} disabled={loggingOut}>
              Logout
            </button>
            <Link className="secondary-button" to={DISCOVER_ROUTES.providerLogin.path}>
              Switch account
            </Link>
          </div>
        </article>

        <article className="provider-dashboard-panel">
          <h2>Next steps</h2>
          <p>Open a submitted application to continue the review lifecycle in Platform Admin.</p>
          <Link className="secondary-button" to={DISCOVER_ROUTES.providerLandingPage.path}>
            Landing page builder
          </Link>
        </article>
      </div>
    </section>
  );
}
