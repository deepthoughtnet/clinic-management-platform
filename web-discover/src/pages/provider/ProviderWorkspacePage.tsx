import * as React from "react";
import { useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  getProviderClaimReview,
  submitProviderClaim,
  type ProviderClaimReviewResponse,
  type ProviderWorkspaceProfile,
  type ProviderWorkspaceWorkItem,
} from "../../api/providerAuth";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { useProviderSession } from "../../context/ProviderSessionContext";
import { DISCOVER_ROUTES } from "../../routes";

const TOKEN_KEY = "jeevanam.discover.providerOnboardingToken";
const TOKEN_KEYS = [
  TOKEN_KEY,
  `${TOKEN_KEY}.INDIVIDUAL_DOCTOR`,
  `${TOKEN_KEY}.CLINIC`,
  `${TOKEN_KEY}.HOSPITAL`,
];

function allowedActionLabel(action: string) {
  switch (action) {
    case "CREATE_PUBLIC_PROFILE_DRAFT":
      return "Open Profile";
    case "START_PROFILE":
      return "Open Profile";
    case "OPEN_PROFILE":
      return "Open Profile";
    case "CONTINUE_PROFILE":
      return "Continue Profile";
    case "CONTINUE_EDITING":
      return "Continue Profile";
    case "ENABLE_DISCOVER":
      return "Enable Discover";
    case "SUBMIT_FOR_REVIEW":
      return "Submit for Platform Review";
    case "REVIEW_CHANGES":
      return "Review Changes";
    case "VIEW_REVIEW":
      return "View Under Review";
    case "VIEW_UNDER_REVIEW":
      return "View Under Review";
    case "VIEW_REVIEW_STATUS":
      return "View Review";
    case "AWAITING_APPROVAL":
      return "Awaiting Approval";
    case "VIEW_APPROVAL_STATUS":
      return "Awaiting Approval";
    case "VIEW_PUBLISHED_PROFILE":
      return "View Published Profile";
    case "VIEW_DETAILS":
      return "View Details";
    case "OPEN_CLAIM":
      return "Open claim";
    case "BACK_TO_DASHBOARD":
      return "Back to Dashboard";
    case "VIEW_OWNERSHIP":
      return "View Ownership";
    case "VIEW_PREVIEW":
      return "Preview Profile";
    case "VIEW_READINESS":
      return "View Readiness";
    case "OPEN_PUBLIC_PROFILE":
      return "Open Public Profile";
    default:
      return action.replaceAll("_", " ").replace(/\b\w/g, (char) => char.toUpperCase());
  }
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

function allowedActionHrefForProfile(profile: ProviderWorkspaceProfile, action: string | null) {
  if (!action) {
    return null;
  }
  if (action === "VIEW_PUBLIC_PROFILE") {
    return profile.publicProfilePath ?? DISCOVER_ROUTES.providerWorkspace.path;
  }
  if (action === "VIEW_REVIEW_STATUS" || action === "VIEW_APPROVAL_STATUS") {
    return DISCOVER_ROUTES.providerPublicProfileReview.path
      .replace(":profileReference", encodeURIComponent(profile.publicProfileReference));
  }
  if (action === "SUBMIT_FOR_REVIEW" || action === "REVIEW_CHANGES" || action === "CONTINUE_PROFILE" || action === "OPEN_PROFILE" || action === "EDIT_PUBLIC_PROFILE") {
    return DISCOVER_ROUTES.providerPublicProfileDraft.path
      .replace(":profileReference", encodeURIComponent(profile.publicProfileReference))
      .replace(":section", action === "SUBMIT_FOR_REVIEW" ? "readiness" : "overview");
  }
  return DISCOVER_ROUTES.providerPublicProfileDraft.path
    .replace(":profileReference", encodeURIComponent(profile.publicProfileReference))
    .replace(":section", "overview");
}

function profileActionLabel(profile: ProviderWorkspaceProfile) {
  return profile.primaryAction ? allowedActionLabel(profile.primaryAction) : null;
}

function isOwnershipClaimWorkItem(item: ProviderWorkspaceWorkItem) {
  return item.workItemType === "OWNERSHIP_CLAIM";
}

function claimSubtitle(item: ProviderWorkspaceWorkItem) {
  if (item.ownershipStatus === "VERIFIED" || item.workItemStatus === "OWNERSHIP_VERIFIED") {
    return item.publicDiscoveryConsent === "DISABLED"
      ? "Tenant consent disabled"
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
  return item.allowedActions.length ? allowedActionLabel(item.allowedActions[0]) : null;
}

function claimPrimaryActionHref(item: ProviderWorkspaceWorkItem) {
  if (item.allowedActions.length > 0) {
    const profileReference = item.publicProfileReference?.trim();
    const action = item.allowedActions[0];
    if (action === "OPEN_CLAIM" || action === "VIEW_DETAILS") {
      const reference = item.connectionReference?.trim();
      return reference ? `${DISCOVER_ROUTES.providerWorkspace.path}?connectionReference=${encodeURIComponent(reference)}` : null;
    }
    if (!profileReference) {
      return null;
    }
    const section = action === "VIEW_PREVIEW"
      ? "preview"
      : action === "VIEW_READINESS"
        ? "readiness"
        : "overview";
    return DISCOVER_ROUTES.providerPublicProfileDraft.path
      .replace(":profileReference", encodeURIComponent(profileReference))
      .replace(":section", section);
  }
  return null;
}

function claimLocationLabel(item: ProviderWorkspaceWorkItem) {
  if (!item.city) {
    return "—";
  }
  return item.area ? `${item.city} · ${item.area}` : item.city;
}

function claimAttentionReason(item: ProviderWorkspaceWorkItem) {
  if (item.ownershipStatus === "VERIFIED" || item.workItemStatus === "OWNERSHIP_VERIFIED") {
    return item.publicDiscoveryConsent === "DISABLED" ? "Tenant consent disabled" : "Ownership verified";
  }
  if (item.claimStatus === "CLAIM_PENDING" || item.workItemStatus === "PLATFORM_REVIEW") {
    return "Ownership verification pending";
  }
  if (item.reviewStatus === "REJECTED" || item.claimStatus === "REJECTED") {
    return "Rejected";
  }
  if (item.reviewStatus === "PENDING_REVIEW" || item.claimStatus === "CLAIM_SUBMITTED") {
    return "Platform review in progress";
  }
  if (item.claimStatus === "DISPUTED" || item.ownershipStatus === "DISPUTED") {
    return "Ownership verification pending";
  }
  return "Needs attention";
}

function profileAttentionReason(profile: ProviderWorkspaceProfile) {
  return profile.attentionLabel || profile.nextActionLabel || null;
}

function profileLocationLabel(profile: ProviderWorkspaceProfile) {
  if (!profile.city) {
    return "—";
  }
  return profile.area ? `${profile.city} · ${profile.area}` : profile.city;
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
  const [error, setError] = useState<string | null>(null);
  const [claimReference, setClaimReference] = useState<string | null>(null);
  const [claimReview, setClaimReview] = useState<ProviderClaimReviewResponse | null>(null);
  const [claimLoading, setClaimLoading] = useState(false);
  const [claimSubmitting, setClaimSubmitting] = useState(false);
  const [claimNote, setClaimNote] = useState("");

  const providerProfiles = workspace?.profiles ?? [];
  const workItems = workspace?.workItems ?? [];
  const supportedProviderTypes = workspace?.supportedProviderTypes ?? [];
  const attentionItems = useMemo(
    () => [
      ...providerProfiles
        .filter((profile) => profile.providerActionRequired)
        .map((profile) => ({
          kind: "PUBLIC_PROFILE" as const,
          updatedAt: profile.lastUpdatedAt ?? null,
          profile,
        })),
      ...workItems
        .filter(isOwnershipClaimWorkItem)
        .filter((item) => item.workItemStatus !== "PUBLISHED")
        .filter((item) => item.workItemStatus !== "OWNERSHIP_VERIFIED" || item.publicDiscoveryConsent === "DISABLED")
        .map((item) => ({
          kind: "OWNERSHIP_CLAIM" as const,
          updatedAt: item.lastUpdatedAt ?? null,
          item,
        })),
    ].sort((left, right) => new Date(right.updatedAt ?? 0).getTime() - new Date(left.updatedAt ?? 0).getTime()),
    [providerProfiles, workItems],
  );
  const recentActivity = useMemo(() => providerProfiles.slice(0, 5), [providerProfiles]);

  const summaryCards = workspace ? [
    { label: "Active Profiles", value: workspace.activeProfileCount },
    { label: "Ready for Review", value: workspace.readyForReviewCount },
    { label: "Under Platform Review", value: workspace.underReviewCount },
    { label: "Published", value: workspace.publishedCount },
    { label: "Needs Attention", value: workspace.needsAttentionCount },
  ] : [];

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
  const profileCards = providerProfiles;

  return (
    <section className="page-section provider-account-page">
      <header className="provider-account-header">
        <div className="provider-account-heading">
          <span className="eyebrow">Provider account</span>
          <h1>Manage your provider profiles.</h1>
          <p>Review what still needs attention, continue the right profile workflow, and keep published profiles separate from work in progress.</p>
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
            <h2>Needs Attention</h2>
            <p>Each card shows the exact blocker that needs your next action.</p>
          </div>
          {supportedProviderTypes.length ? (
            <Link className="primary-button" to={`${DISCOVER_ROUTES.listPractice.path}?mode=add`}>
              Create another profile
            </Link>
          ) : null}
        </div>
        {attentionItems.length ? (
          <div className="provider-account-attention-list">
            {attentionItems.map((entry) =>
              entry.kind === "PUBLIC_PROFILE" ? (
                <article className="provider-account-attention-item" key={`${entry.profile.draftReference}-attention`}>
                  <div className="provider-account-attention-copy">
                    <strong>{entry.profile.displayName || "Provider profile"}</strong>
                    <p>{providerTypeLabel(entry.profile.profileType)} · {profileLocationLabel(entry.profile)}</p>
                    <span>{profileAttentionReason(entry.profile)}</span>
                    <small>{entry.profile.lifecycleLabel}</small>
                  </div>
                  <div className="provider-account-attention-meta">
                    <span>{entry.profile.nextActionLabel}</span>
                    <small>Ownership: {entry.profile.ownershipStatus}</small>
                    <small>Publication: {entry.profile.publicationStatus}</small>
                    <small>Connection: {entry.profile.platformConnectionStatus}</small>
                    {entry.profile.primaryAction && allowedActionHrefForProfile(entry.profile, entry.profile.primaryAction) ? (
                      <div className="cta-row">
                        <Link className="primary-button" to={allowedActionHrefForProfile(entry.profile, entry.profile.primaryAction)!}>
                          {profileActionLabel(entry.profile)}
                        </Link>
                      </div>
                    ) : null}
                  </div>
                </article>
              ) : entry.kind === "OWNERSHIP_CLAIM" ? (
                <article className="provider-account-attention-item" key={`${entry.item.workItemReference}-attention`}>
                  <div className="provider-account-attention-copy">
                    <strong>{claimCardTitle(entry.item)}</strong>
                    <p>{entry.item.displayName || "Provider profile"} · {publicProfileTypeLabel(entry.item.publicProfileType)}</p>
                    <span>{claimAttentionReason(entry.item) ?? claimSubtitle(entry.item)}</span>
                    <small>{claimLocationLabel(entry.item)}</small>
                  </div>
                  <div className="provider-account-attention-meta">
                    <span>{claimStatusPill(entry.item)}</span>
                    <small>Ownership: {entry.item.ownershipStatus || "UNCLAIMED"}</small>
                    <small>Publication: {entry.item.publicationStatus || "UNPUBLISHED"}</small>
                    <small>Connection: {entry.item.platformConnectionStatus || "NOT_CONNECTED"}</small>
                    {claimPrimaryActionLabel(entry.item) && claimPrimaryActionHref(entry.item) ? (
                      <div className="cta-row">
                        <Link className="primary-button" to={claimPrimaryActionHref(entry.item)!}>
                          {claimPrimaryActionLabel(entry.item)}
                        </Link>
                      </div>
                    ) : null}
                  </div>
                </article>
              ) : null,
            )}
          </div>
        ) : (
          <DiscoverEmptyState
            icon="◌"
            title="No actions currently require your attention."
            description="Provider profiles that are complete or published will remain visible below."
            variant="compact"
          />
        )}
      </section>

      <section className="provider-account-section">
        <div className="provider-account-section-heading">
          <div>
            <h2>My Provider Profiles</h2>
            <p>Profile lifecycle, review, publication, and visibility are shown here.</p>
          </div>
          {supportedProviderTypes.length ? (
            <Link className="secondary-button" to={`${DISCOVER_ROUTES.listPractice.path}?mode=add`}>
              Create another profile
            </Link>
          ) : null}
        </div>
        {profileCards.length ? (
          <div className="provider-account-application-grid">
            {profileCards.map((profile) => (
              <article className="provider-account-application-card" key={profile.draftReference}>
                <div className="provider-account-card-header">
                  <div>
                    <strong>{profile.displayName}</strong>
                    <p>{providerTypeLabel(profile.profileType)}</p>
                  </div>
                  <span className="provider-account-status-pill">{profile.lifecycleLabel}</span>
                </div>
                <dl className="provider-account-detail-list">
                  <div>
                    <dt>Lifecycle</dt>
                    <dd>{profile.lifecycleLabel}</dd>
                  </div>
                  <div>
                    <dt>Completion</dt>
                    <dd>{profile.completenessPercentage}% complete</dd>
                  </div>
                  <div>
                    <dt>Visibility</dt>
                    <dd>{profile.publicationStatus === "PUBLISHED" ? "Published" : "Private"}</dd>
                  </div>
                  <div>
                    <dt>Last updated</dt>
                    <dd>{formatDateTime(profile.lastUpdatedAt)}</dd>
                  </div>
                  <div>
                    <dt>Reference</dt>
                    <dd>{profile.publicProfileReference}</dd>
                  </div>
                </dl>
                {profile.primaryAction && allowedActionHrefForProfile(profile, profile.primaryAction) ? (
                  <div className="provider-account-card-actions">
                    <Link className="primary-button" to={allowedActionHrefForProfile(profile, profile.primaryAction)!}>
                      {profileActionLabel(profile)}
                    </Link>
                  </div>
                ) : null}
              </article>
            ))}
          </div>
        ) : (
          <DiscoverEmptyState
            icon="◌"
            title="No provider profiles yet"
            description="Create a provider profile to manage its draft, review, publication, and visibility lifecycle from one place."
            primaryAction="Create Profile"
            primaryTo={DISCOVER_ROUTES.listPractice.path}
          />
        )}
      </section>

      <section className="provider-account-section">
        <div className="provider-account-section-heading">
          <div>
            <h2>Recent activity</h2>
            <p>Recent provider profile updates ordered by the latest change.</p>
          </div>
        </div>
        {recentActivity.length ? (
          <div className="provider-account-activity-list">
            {recentActivity.map((profile) => (
              <article className="provider-account-activity-item" key={`${profile.draftReference}-activity`}>
                <strong>{profile.displayName || providerTypeLabel(profile.profileType)}</strong>
                <p>{profile.publicProfileReference}</p>
                <span>{profile.lifecycleLabel} · {profile.completenessPercentage}% complete</span>
                <small>{formatDateTime(profile.lastUpdatedAt)}</small>
              </article>
            ))}
          </div>
        ) : (
          <DiscoverEmptyState
            icon="·"
            title="No recent activity"
            description="Provider profile updates will appear here once work begins."
            variant="compact"
          />
        )}
      </section>
    </section>
  );
}
