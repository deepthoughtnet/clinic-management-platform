import * as React from "react";
import { useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  createProviderOnboardingAccess,
  getProviderClaimReview,
  submitProviderClaim,
  type ProviderClaimReviewResponse,
  type ProviderWorkspaceApplication,
  type ProviderWorkspaceWorkItem,
} from "../../api/providerAuth";
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

function isOwnershipClaimWorkItem(item: ProviderWorkspaceWorkItem) {
  return item.workItemType === "OWNERSHIP_CLAIM";
}

function claimSubtitle(item: ProviderWorkspaceWorkItem) {
  if (item.ownershipStatus === "VERIFIED" || item.workItemStatus === "OWNERSHIP_VERIFIED") {
    return item.publicDiscoveryConsent === "DISABLED"
      ? "Tenant consent required"
      : "Your ownership has been verified.";
  }
  if (item.workItemStatus === "PLATFORM_REVIEW" || item.claimStatus === "CLAIM_SUBMITTED" || item.ownershipStatus === "CLAIM_PENDING" || item.reviewStatus === "PENDING_REVIEW") {
    return "Claim submitted - awaiting Platform review";
  }
  if (item.claimStatus === "PROVIDER_AUTHENTICATED") {
    return "Claim review ready";
  }
  if (item.claimStatus === "APPROVED" || item.ownershipStatus === "APPROVED") {
    return "Claim approved";
  }
  if (item.claimStatus === "REJECTED") {
    return "Claim rejected";
  }
  if (item.claimStatus === "REVOKED") {
    return "Claim revoked";
  }
  if (item.claimStatus === "DISPUTED") {
    return "Claim disputed";
  }
  if (item.claimStatus === "EXPIRED" || item.ownershipStatus === "EXPIRED") {
    return "Claim expired";
  }
  return "Claim in progress";
}

function claimCardTitle(item: ProviderWorkspaceWorkItem) {
  if (item.ownershipStatus === "VERIFIED" || item.workItemStatus === "OWNERSHIP_VERIFIED") {
    return "Ownership verified";
  }
  return item.displayName || "Pending claim";
}

function claimPrimaryActionLabel(item: ProviderWorkspaceWorkItem) {
  if (item.allowedActions.includes("OPEN_PUBLIC_PROFILE")) {
    return "Open public profile";
  }
  if (item.allowedActions.includes("VIEW_PREVIEW")) {
    return "Preview profile";
  }
  if (item.allowedActions.includes("VIEW_READINESS")) {
    return "View readiness";
  }
  if (item.allowedActions.includes("OPEN_CLAIM")) {
    return "Open claim";
  }
  if (item.allowedActions.includes("OPEN_PROFILE")) {
    return "Open profile";
  }
  if (item.allowedActions.includes("VIEW_DETAILS")) {
    return "View details";
  }
  return null;
}

function claimPrimaryActionHref(item: ProviderWorkspaceWorkItem) {
  if (item.allowedActions.includes("OPEN_PUBLIC_PROFILE") || item.allowedActions.includes("VIEW_PREVIEW") || item.allowedActions.includes("VIEW_READINESS")) {
    const profileReference = item.publicProfileReference?.trim();
    if (!profileReference) {
      return null;
    }
    const section = item.allowedActions.includes("VIEW_PREVIEW")
      ? "preview"
      : item.allowedActions.includes("VIEW_READINESS")
        ? "readiness"
        : "overview";
    return DISCOVER_ROUTES.providerPublicProfileDraft.path
      .replace(":profileReference", encodeURIComponent(profileReference))
      .replace(":section", section);
  }
  const reference = item.connectionReference?.trim();
  if (!reference) {
    return null;
  }
  return `${DISCOVER_ROUTES.providerWorkspace.path}?connectionReference=${encodeURIComponent(reference)}`;
}

function claimLocationLabel(item: ProviderWorkspaceWorkItem) {
  if (!item.city) {
    return "—";
  }
  return item.area ? `${item.city} · ${item.area}` : item.city;
}

function publicProfileTypeLabel(value: string) {
  switch (value) {
    case "DOCTOR":
      return "Doctor";
    case "HOSPITAL":
      return "Hospital";
    case "CLINIC":
      return "Clinic";
    default:
      return value.replaceAll("_", " ").replace(/\b\w/g, (char) => char.toUpperCase());
  }
}

function claimStatusPill(item: ProviderWorkspaceWorkItem) {
  return item.workItemStatus || item.ownershipStatus || item.reviewStatus || item.claimStatus || "PENDING";
}

function claimReviewStateLabel(review: ProviderClaimReviewResponse) {
  return review.pageMode || review.workItemStatus || review.status || "READ_ONLY_FALLBACK";
}

function claimReviewTitle(review: ProviderClaimReviewResponse) {
  if (review.pageMode === "OWNERSHIP_VERIFIED") {
    return "Ownership verified";
  }
  if (review.pageMode === "CLAIM_REJECTED") {
    return "Claim rejected";
  }
  if (review.pageMode === "CLAIM_DISPUTED") {
    return "Claim disputed";
  }
  if (review.pageMode === "CLAIM_REVOKED") {
    return "Claim revoked";
  }
  if (review.pageMode === "CLAIM_EXPIRED") {
    return "Claim expired";
  }
  return "Claim review";
}

function claimReviewSubtitle(review: ProviderClaimReviewResponse) {
  if (review.pageMode === "OWNERSHIP_VERIFIED") {
    return `Your ownership of ${review.displayName || "this provider"} has been verified. Tenant consent is required before the public profile can proceed toward publication.`;
  }
  if (review.pageMode === "CLAIM_PENDING" || review.pageMode === "CLAIM_SUBMITTED") {
    return "Your claim is being reviewed.";
  }
  if (review.pageMode === "PROVIDER_AUTHENTICATED" || review.pageMode === "CLAIM_INTENT_CREATED") {
    return "Your claim can be submitted after you review the details.";
  }
  return "This ownership record is read-only.";
}

function claimReviewCanSubmit(review: ProviderClaimReviewResponse) {
  return review.allowedActions.includes("SUBMIT_CLAIM");
}

export function ProviderWorkspacePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { workspace, logout } = useProviderSession();
  const [loggingOut, setLoggingOut] = useState(false);
  const [openingReference, setOpeningReference] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [claimReference, setClaimReference] = useState<string | null>(null);
  const [claimReview, setClaimReview] = useState<ProviderClaimReviewResponse | null>(null);
  const [claimLoading, setClaimLoading] = useState(false);
  const [claimSubmitting, setClaimSubmitting] = useState(false);
  const [claimNote, setClaimNote] = useState("");

  const activeApplications = workspace?.applications ?? [];
  const publishedProfiles = workspace?.publishedProfiles ?? [];
  const workItems = workspace?.workItems ?? [];
  const supportedProviderTypes = workspace?.supportedProviderTypes ?? [];
  const attentionItems = useMemo(
    () => [
      ...workItems
        .filter(isOwnershipClaimWorkItem)
        .filter((item) => item.workItemStatus !== "PUBLISHED")
        .filter((item) => item.workItemStatus !== "OWNERSHIP_VERIFIED" || item.publicDiscoveryConsent === "DISABLED")
        .map((item) => ({
          kind: "OWNERSHIP_CLAIM" as const,
          updatedAt: item.lastUpdatedAt ?? null,
          item,
        })),
      ...activeApplications
        .filter(isAttentionApplication)
        .map((application) => ({
          kind: "ONBOARDING_APPLICATION" as const,
          updatedAt: application.updatedAt,
          application,
        })),
    ].sort((left, right) => new Date(right.updatedAt ?? 0).getTime() - new Date(left.updatedAt ?? 0).getTime()),
    [activeApplications, workItems],
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
  const connectionReference = searchParams.get("connectionReference")?.trim() || null;

  React.useEffect(() => {
    let cancelled = false;
    async function loadClaim() {
      if (!connectionReference) {
        setClaimReference(null);
        setClaimReview(null);
        return;
      }
      setClaimLoading(true);
      try {
        const review = await getProviderClaimReview(connectionReference);
        if (!cancelled) {
          setClaimReference(connectionReference);
          setClaimReview(review);
          setClaimNote(review.claimNote ?? review.reason ?? "");
        }
      } catch (ex) {
        if (!cancelled) {
          setError(ex instanceof Error ? ex.message : "Could not load claim review.");
        }
      } finally {
        if (!cancelled) {
          setClaimLoading(false);
        }
      }
    }
    void loadClaim();
    return () => {
      cancelled = true;
    };
  }, [connectionReference]);

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

  async function submitClaim() {
    if (!claimReference) {
      return;
    }
    setClaimSubmitting(true);
    setError(null);
    try {
      const review = await submitProviderClaim(claimReference, { reason: claimNote.trim() || null });
      setClaimReview(review);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not submit claim.");
    } finally {
      setClaimSubmitting(false);
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

      {connectionReference ? (
        <section className="provider-account-section" aria-label="Claim review">
          <div className="provider-account-section-heading">
            <div>
              <h2>{claimReview ? claimReviewTitle(claimReview) : "Claim review"}</h2>
              <p>{claimReview ? claimReviewSubtitle(claimReview) : "Loading ownership lifecycle state."}</p>
            </div>
            <span className="provider-account-session-pill">{claimReview ? claimReviewStateLabel(claimReview) : "Pending"}</span>
          </div>
          {claimLoading ? (
            <div className="provider-dashboard-skeleton" role="status" aria-label="Loading claim review">
              <span />
              <span />
              <span />
            </div>
          ) : claimReview ? (
            <div className="provider-account-section">
              <div className="provider-account-summary-grid">
                <article className="provider-account-summary-card">
                  <span>Provider</span>
                  <strong>{claimReview.displayName || "Selected provider"}</strong>
                  <p>{claimReview.city || "—"}{claimReview.area ? ` · ${claimReview.area}` : ""}</p>
                </article>
                <article className="provider-account-summary-card">
                  <span>Ownership</span>
                  <strong>{claimReview.ownershipStatus}</strong>
                  <p>{claimReview.maskedProviderMobile ? `Owner mobile ending ${claimReview.maskedProviderMobile.slice(-4)}` : "Verified provider account"}</p>
                </article>
                <article className="provider-account-summary-card">
                  <span>Review</span>
                  <strong>{claimReview.reviewStatus}</strong>
                  <p>{claimReview.workItemStatus}</p>
                </article>
                <article className="provider-account-summary-card">
                  <span>Tenant consent</span>
                  <strong>{claimReview.tenantConsentStatus}</strong>
                  <p>Publication {claimReview.publicProfileStatus}</p>
                </article>
                <article className="provider-account-summary-card">
                  <span>Connection</span>
                  <strong>{claimReview.platformConnectionStatus}</strong>
                  <p>{claimReview.bookingCapability}</p>
                </article>
                <article className="provider-account-summary-card">
                  <span>Ownership updated</span>
                  <strong>{formatDateTime(claimReview.ownershipUpdatedAt)}</strong>
                  <p>{claimReview.publicProfileStatus === "PUBLISHED" ? "Public profile synchronized" : "Public profile not yet synchronized"}</p>
                </article>
              </div>
              <div className="provider-account-section">
                <label className="field-label">Submitted claim note</label>
                <p className="body-small">{claimReview.claimNote?.trim() || claimReview.reason?.trim() || "No claim note was submitted."}</p>
                <dl className="provider-account-detail-list">
                  <div>
                    <dt>Submitted at</dt>
                    <dd>{formatDateTime(claimReview.submittedAt)}</dd>
                  </div>
                  <div>
                    <dt>Reviewed at</dt>
                    <dd>{formatDateTime(claimReview.reviewedAt)}</dd>
                  </div>
                  <div>
                    <dt>Connection reference</dt>
                    <dd>{claimReview.connectionReference}</dd>
                  </div>
                </dl>
                {claimReviewCanSubmit(claimReview) ? (
                  <>
                    <label className="field-label" htmlFor="claim-note">Claim note</label>
                    <textarea
                      id="claim-note"
                      className="text-area"
                      value={claimNote}
                      onChange={(event) => setClaimNote(event.target.value)}
                      rows={3}
                      aria-label="Claim note"
                    />
                    <div className="cta-row">
                      <button className="primary-button" type="button" onClick={() => void submitClaim()} disabled={claimSubmitting}>
                        {claimSubmitting ? "Submitting..." : "Submit claim"}
                      </button>
                      <button className="secondary-button" type="button" onClick={() => setClaimNote("")}>
                        Clear note
                      </button>
                    </div>
                  </>
                ) : null}
                <div className="cta-row">
                  {claimReview.allowedActions.includes("BACK_TO_DASHBOARD") ? (
                    <Link className="primary-button" to={DISCOVER_ROUTES.providerWorkspace.path}>
                      Back to dashboard
                    </Link>
                  ) : null}
                  {claimReview.allowedActions.includes("VIEW_OWNERSHIP") ? (
                    <Link className="secondary-button" to={`${DISCOVER_ROUTES.providerWorkspace.path}?connectionReference=${encodeURIComponent(claimReview.connectionReference)}`}>
                      View ownership details
                    </Link>
                  ) : null}
                </div>
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

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
            <p>These work items are incomplete or waiting on your next action.</p>
          </div>
          {supportedProviderTypes.length ? (
            <Link className="primary-button" to={`${DISCOVER_ROUTES.listPractice.path}?mode=add`}>
              Add another profile
            </Link>
          ) : null}
        </div>
        {attentionItems.length ? (
          <div className="provider-account-attention-list">
            {attentionItems.map((entry) =>
              entry.kind === "OWNERSHIP_CLAIM" ? (
                <article className="provider-account-attention-item" key={`${entry.item.workItemReference}-attention`}>
                  <div className="provider-account-attention-copy">
                    <strong>{claimCardTitle(entry.item)}</strong>
                    <p>{entry.item.displayName || "Pending claim"} · {publicProfileTypeLabel(entry.item.publicProfileType)}</p>
                    <span>{claimSubtitle(entry.item)}</span>
                    <small>{claimLocationLabel(entry.item)}</small>
                  </div>
                  <div className="provider-account-attention-meta">
                    <span>{claimStatusPill(entry.item)}</span>
                    <small>Ownership: {entry.item.ownershipStatus || "UNCLAIMED"}</small>
                    <small>Publication: {entry.item.publicationStatus || "UNPUBLISHED"}</small>
                    <small>Connection: {entry.item.platformConnectionStatus || "NOT_CONNECTED"}</small>
                    <div className="cta-row">
                      {claimPrimaryActionLabel(entry.item) && claimPrimaryActionHref(entry.item) ? (
                        <Link className="primary-button" to={claimPrimaryActionHref(entry.item)!}>
                          {claimPrimaryActionLabel(entry.item)}
                        </Link>
                      ) : null}
                      {claimPrimaryActionLabel(entry.item) !== "View details" ? (
                        <Link className="secondary-button" to={claimPrimaryActionHref(entry.item) ?? DISCOVER_ROUTES.providerWorkspace.path}>
                          View details
                        </Link>
                      ) : null}
                    </div>
                  </div>
                </article>
              ) : (
                <article className="provider-account-attention-item" key={`${entry.application.id}-attention`}>
                  <div className="provider-account-attention-copy">
                    <strong>{supportText(entry.application)}</strong>
                    <p>{entry.application.referenceNumber}</p>
                    <span>{attentionSubtitle(entry.application)}</span>
                    <small>Current step: {currentStepLabel(entry.application.currentStep)}</small>
                  </div>
                  <div className="provider-account-attention-meta">
                    <span>{statusLabel(entry.application.status)}</span>
                    <div className="cta-row">
                      {entry.application.status === "PUBLISHED" ? null : (
                        <button
                          className="primary-button"
                          type="button"
                          onClick={() => void continueRegistration(entry.application)}
                          disabled={openingReference === entry.application.referenceNumber}
                        >
                          Continue registration
                        </button>
                      )}
                      <Link className="secondary-button" to={primaryActionHref(entry.application)}>
                        View details
                      </Link>
                    </div>
                  </div>
                </article>
              ),
            )}
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
              <h2>My managed profiles</h2>
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
