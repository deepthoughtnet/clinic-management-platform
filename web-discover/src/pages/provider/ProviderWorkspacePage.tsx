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

function formatCount(value: number, singular: string, plural = `${singular}s`) {
  return `${value} ${value === 1 ? singular : plural}`;
}

function primaryActionLabel(status: ProviderWorkspaceApplication["status"]) {
  switch (status) {
    case "DRAFT":
      return "Continue registration";
    case "CHANGES_REQUESTED":
      return "Review feedback and edit";
    case "SUBMITTED":
      return "View application";
    case "UNDER_REVIEW":
      return "Track review";
    case "APPROVED":
      return "Prepare publication";
    case "PUBLISHED":
      return "Open public profile";
    default:
      return "View application";
  }
}

function primaryActionHref(application: ProviderWorkspaceApplication) {
  switch (application.status) {
    case "DRAFT":
    case "CHANGES_REQUESTED":
      return DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(application.referenceNumber));
    case "SUBMITTED":
    case "UNDER_REVIEW":
      return DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(application.referenceNumber));
    case "APPROVED":
      return DISCOVER_ROUTES.providerLandingPage.path;
    case "PUBLISHED":
      return application.publicProfilePath ?? DISCOVER_ROUTES.providerLandingPage.path;
    default:
      return DISCOVER_ROUTES.providerApplicationDashboard.path.replace(":applicationReference", encodeURIComponent(application.referenceNumber));
  }
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
  const applications = workspace?.applications ?? [];
  const attentionNeeded = applications.filter((item) => item.status === "CHANGES_REQUESTED" || item.status === "SUBMITTED" || item.status === "UNDER_REVIEW");
  const draftCount = applications.filter((item) => item.status === "DRAFT").length;
  const approvedCount = applications.filter((item) => item.status === "APPROVED").length;
  const publishedCount = publishedProfiles.length;
  const emailSummary = maskContact(workspace?.contactEmail ?? null, "email");
  const phoneSummary = maskContact(workspace?.contactPhone ?? null, "phone");

  async function endSession(targetPath: string) {
    setLoggingOut(true);
    try {
      await logoutProviderSession();
      setWorkspace(null);
      setError(null);
      navigate(targetPath, { replace: true });
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

  return (
    <section className="page-section provider-dashboard-page">
      <div className="provider-dashboard-hero">
        <div>
          <span className="eyebrow">Provider workspace</span>
          <h1>Manage your applications and public profiles</h1>
          <p>View your active applications, publication status, and account actions from one place.</p>
        </div>
        <div className="resume-card">
          <strong>{applications.length ? formatCount(applications.length, "application") : "No applications yet"}</strong>
          <span>{emailSummary || phoneSummary || "Verified provider session active"}</span>
        </div>
      </div>

      {error ? <p className="inline-error" role="alert">{error}</p> : null}

      <div className="provider-dashboard-layout">
        <article className="provider-dashboard-panel">
          <h2>Overview</h2>
          <div className="completion-summary">
            <span>{formatCount(applications.length, "application")}</span>
            <span>{formatCount(draftCount, "draft")}</span>
            <span>{formatCount(approvedCount, "approved profile")}</span>
            <span>{formatCount(publishedCount, "published profile")}</span>
          </div>
          <div className="missing-list">
            <strong>Attention needed</strong>
            {attentionNeeded.length ? (
              attentionNeeded.slice(0, 3).map((item) => (
                <span key={`${item.id}-${item.status}`}>{item.displayName} · {statusLabel(item.status)}</span>
              ))
            ) : (
              <span>No applications currently need attention.</span>
            )}
          </div>
        </article>

        <article className="provider-dashboard-panel">
          <h2>Account &amp; Security</h2>
          <p>{emailSummary ? `Signed in with ${emailSummary}` : "Signed in with a verified provider account."}</p>
          {phoneSummary ? <p>{phoneSummary}</p> : null}
          <div className="cta-row">
            <button className="primary-button" type="button" onClick={() => void endSession(DISCOVER_ROUTES.providerLogin.path)} disabled={loggingOut}>
              Logout
            </button>
            <button className="secondary-button" type="button" onClick={() => void endSession(DISCOVER_ROUTES.providerLogin.path)} disabled={loggingOut}>
              Switch account
            </button>
          </div>
        </article>
      </div>

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
                    <Link className="secondary-button" to={primaryActionHref(application)}>
                      {primaryActionLabel(application.status)}
                    </Link>
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
            <p>Approved profiles will appear here once they are published.</p>
          )}
        </article>
      </div>

      <div className="provider-dashboard-layout">
        <article className="provider-dashboard-panel">
          <h2>Recent Activity</h2>
          {applications.length ? (
            <div className="status-timeline vertical">
              {[...applications]
                .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
                .slice(0, 5)
                .map((application) => (
                  <div key={`${application.id}-activity`}>
                    <strong>{application.displayName}</strong>
                    <span>{statusLabel(application.status)} · {application.referenceNumber}</span>
                    <small>Updated {new Date(application.updatedAt).toLocaleString()}</small>
                  </div>
                ))}
            </div>
          ) : (
            <p>No recent activity yet.</p>
          )}
          <div className="cta-row">
            <Link className="secondary-button" to={DISCOVER_ROUTES.providerLandingPage.path}>
              Provider profiles
            </Link>
          </div>
        </article>
      </div>
    </section>
  );
}
