import * as React from "react";
import { useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  getProviderClaimReview,
  submitProviderClaim,
  type ProviderClaimReviewResponse,
  type ProviderWorkspaceApplication,
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
      return "View submitted preview";
    case "VIEW_REVIEW_STATUS":
      return "View submitted preview";
    case "AWAITING_APPROVAL":
      return "Awaiting Approval";
    case "VIEW_APPROVAL_STATUS":
      return "Awaiting Approval";
    case "VIEW_PUBLISHED_PROFILE":
      return "View public profile";
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

function applicationStatusLabel(status: string) {
  return status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function applicationStageLabel(step: string) {
  return step.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function applicationLatestUpdateLabel(application: ProviderWorkspaceApplication) {
  switch (application.status) {
    case "DRAFT":
      return `${applicationStageLabel(application.currentStep)} · Draft in progress`;
    case "CONTACT_VERIFIED":
      return `${applicationStageLabel(application.currentStep)} · Contact verified`;
    case "PROFILE_INCOMPLETE":
      return `${applicationStageLabel(application.currentStep)} · Profile incomplete`;
    case "READY_FOR_REVIEW":
      return `${applicationStageLabel(application.currentStep)} · Ready for review`;
    case "SUBMITTED":
      return `${applicationStageLabel(application.currentStep)} · Under Platform Review`;
    case "UNDER_REVIEW":
      return `${applicationStageLabel(application.currentStep)} · Under Platform Review`;
    case "CHANGES_REQUESTED":
      return `${applicationStageLabel(application.currentStep)} · Changes requested`;
    case "APPROVED":
      return `${applicationStageLabel(application.currentStep)} · Approved`;
    case "PUBLISHED":
      return `${applicationStageLabel(application.currentStep)} · Published`;
    default:
      return `${applicationStageLabel(application.currentStep)} · ${applicationStatusLabel(application.status)}`;
  }
}

function applicationAttentionReason(application: ProviderWorkspaceApplication) {
  switch (application.status) {
    case "DRAFT":
      return "Continue setup to complete the application.";
    case "CONTACT_VERIFIED":
      return "Complete the remaining provider details.";
    case "PROFILE_INCOMPLETE":
      return "Complete the missing profile information.";
    case "READY_FOR_REVIEW":
      return "Submit the completed application for review.";
    case "CHANGES_REQUESTED":
      return "Review the requested changes and resubmit.";
    default:
      return null;
  }
}

function applicationPrimaryActionLabel(application: ProviderWorkspaceApplication) {
  const action = application.allowedActions[0];
  switch (action) {
    case "OPEN_PROFILE":
      return "Resume draft";
    case "CONTINUE_PROFILE":
      return "Continue setup";
    case "ENABLE_DISCOVER":
      return "Enable Discover";
    case "SUBMIT_FOR_REVIEW":
      return "Submit for review";
    case "VIEW_UNDER_REVIEW":
      return "View status";
    case "AWAITING_APPROVAL":
      return "Awaiting publication";
    case "REVIEW_CHANGES":
      return "Review changes";
    case "VIEW_REVIEW":
      return "View review";
    case "VIEW_PUBLISHED_PROFILE":
      return "View profile";
    case "VIEW_DETAILS":
      return "View details";
    default:
      return action ? allowedActionLabel(action) : null;
  }
}

function applicationSecondaryActionLabel(application: ProviderWorkspaceApplication) {
  const action = application.allowedActions[1];
  switch (action) {
    case "OPEN_PROFILE":
      return "Resume draft";
    case "CONTINUE_PROFILE":
      return "Continue setup";
    case "ENABLE_DISCOVER":
      return "Enable Discover";
    case "SUBMIT_FOR_REVIEW":
      return "Submit for review";
    case "VIEW_UNDER_REVIEW":
      return "View status";
    case "AWAITING_APPROVAL":
      return "Awaiting publication";
    case "REVIEW_CHANGES":
      return "Review changes";
    case "VIEW_REVIEW":
      return "View review";
    case "VIEW_PUBLISHED_PROFILE":
      return "View profile";
    case "VIEW_DETAILS":
      return "View details";
    default:
      return action ? allowedActionLabel(action) : null;
  }
}

function applicationActionHref(application: ProviderWorkspaceApplication, action: string | null) {
  if (!action) {
    return null;
  }
  if (action === "VIEW_PUBLISHED_PROFILE") {
    return application.publicProfilePath ?? null;
  }
  return DISCOVER_ROUTES.providerApplicationDashboard.path
    .replace(":applicationReference", encodeURIComponent(application.referenceNumber));
}

function applicationStatusPill(application: ProviderWorkspaceApplication) {
  switch (application.status) {
    case "PUBLISHED":
      return "Published";
    case "SUBMITTED":
    case "UNDER_REVIEW":
      return "Under Platform Review";
    case "READY_FOR_REVIEW":
      return "Ready for Review";
    case "CHANGES_REQUESTED":
      return "Changes requested";
    case "APPROVED":
      return "Approved";
    case "PROFILE_INCOMPLETE":
      return "Profile incomplete";
    case "CONTACT_VERIFIED":
      return "Contact verified";
    default:
      return applicationStatusLabel(application.status);
  }
}

function applicationPrimaryActionHref(application: ProviderWorkspaceApplication) {
  return applicationActionHref(application, application.allowedActions[0] ?? null);
}

function applicationSecondaryActionHref(application: ProviderWorkspaceApplication) {
  return applicationActionHref(application, application.allowedActions[1] ?? null);
}

function isApplicationActive(application: ProviderWorkspaceApplication) {
  return !["DISCARDED", "ARCHIVED", "SUSPENDED"].includes(application.status);
}

function isApplicationAttentionRequired(application: ProviderWorkspaceApplication) {
  return [
    "DRAFT",
    "CONTACT_VERIFIED",
    "PROFILE_INCOMPLETE",
    "READY_FOR_REVIEW",
    "CHANGES_REQUESTED",
  ].includes(application.status);
}

function profileManageActionLabel(profile: ProviderWorkspaceProfile) {
  if (profile.moderationStatus === "SUBMITTED" || profile.moderationStatus === "UNDER_REVIEW") {
    return "View submitted preview";
  }
  if (profile.publicationStatus === "PUBLISHED") {
    return "Manage profile";
  }
  if (profile.publicationStatus === "UNPUBLISHED") {
    return "Review unpublished profile";
  }
  if (profile.moderationStatus === "CHANGES_REQUESTED") {
    return "Update profile";
  }
  if (profile.primaryAction === "SUBMIT_FOR_REVIEW" || profile.primaryAction === "CONTINUE_PROFILE" || profile.primaryAction === "CONTINUE_EDITING" || profile.primaryAction === "OPEN_PROFILE") {
    return "Continue profile";
  }
  return "Manage profile";
}

function profileManageActionHref(profile: ProviderWorkspaceProfile) {
  const profileReference = profile.publicProfileReference?.trim();
  if (!profileReference) {
    return null;
  }
  if (profile.moderationStatus === "SUBMITTED" || profile.moderationStatus === "UNDER_REVIEW") {
    return DISCOVER_ROUTES.providerPublicProfileReview.path
      .replace(":profileReference", encodeURIComponent(profileReference));
  }
  return DISCOVER_ROUTES.providerPublicProfileDraft.path
    .replace(":profileReference", encodeURIComponent(profileReference))
    .replace(":section", "overview");
}

function profileLatestUpdateLabel(profile: ProviderWorkspaceProfile) {
  const versionLabel = `Version ${profile.draftVersion}`;
  if (profile.publicationStatus === "PUBLISHED") {
    return `${versionLabel} · Published`;
  }
  if (profile.publicationStatus === "UNPUBLISHED") {
    return `${versionLabel} · Unpublished`;
  }
  if (profile.moderationStatus === "SUBMITTED" || profile.moderationStatus === "UNDER_REVIEW") {
    return `${versionLabel} · Under Platform Review`;
  }
  if (profile.moderationStatus === "CHANGES_REQUESTED") {
    return `${versionLabel} · Changes requested`;
  }
  if (profile.moderationStatus === "APPROVED") {
    return `${versionLabel} · Waiting for publication`;
  }
  return `${versionLabel} · ${profile.lifecycleLabel}`;
}

function profilePublicActionHref(profile: ProviderWorkspaceProfile) {
  return profile.publicProfilePath ?? null;
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
  if (action === "VIEW_UNPUBLISHED_PROFILE") {
    return DISCOVER_ROUTES.providerPublicProfileDraft.path
      .replace(":profileReference", encodeURIComponent(profile.publicProfileReference))
      .replace(":section", "overview");
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
  if (profile.publicationStatus === "UNPUBLISHED") {
    return "Review unpublished profile";
  }
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
  return profile.publicationReason || profile.attentionLabel || profile.nextActionLabel || null;
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

  const providerApplications = workspace?.applications ?? [];
  const publishedApplications = workspace?.publishedProfiles ?? [];
  const publicProfiles = workspace?.profiles ?? [];
  const workItems = workspace?.workItems ?? [];
  const supportedProviderTypes = workspace?.supportedProviderTypes ?? [];
  const applicationCards = useMemo(
    () => [...providerApplications, ...publishedApplications].sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()),
    [providerApplications, publishedApplications],
  );
  const applicationAttentionItems = useMemo(
    () => applicationCards
      .filter(isApplicationAttentionRequired)
      .map((application) => ({
        kind: "PROVIDER_APPLICATION" as const,
        updatedAt: application.updatedAt ?? null,
        application,
      })),
    [applicationCards],
  );
  const attentionItems = useMemo(
    () => [
      ...applicationAttentionItems,
      ...publicProfiles
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
    [applicationAttentionItems, publicProfiles, workItems],
  );
  const recentActivity = useMemo(() => applicationCards.slice(0, 5), [applicationCards]);

  const summaryCards = workspace ? [
    { label: "Active Profiles", value: applicationCards.filter(isApplicationActive).length },
    { label: "Ready for Review", value: applicationCards.filter((application) => application.status === "READY_FOR_REVIEW").length },
    { label: "Under Platform Review", value: applicationCards.filter((application) => application.status === "SUBMITTED" || application.status === "UNDER_REVIEW").length },
    { label: "Published", value: applicationCards.filter((application) => application.status === "PUBLISHED").length },
    { label: "Needs Attention", value: attentionItems.length },
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
  const profileCards = applicationCards;

  return (
    <section className="page-section provider-account-page">
      <header className="provider-account-header">
        <div className="provider-account-heading">
          <span className="eyebrow">Provider account</span>
          <h1>Manage your provider profiles.</h1>
          <p>Review what still needs attention, continue the right application workflow, and keep published profiles separate from work in progress.</p>
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
              entry.kind === "PROVIDER_APPLICATION" ? (
                <article className="provider-account-attention-item" key={`${entry.application.referenceNumber}-attention`}>
                  <div className="provider-account-attention-copy">
                    <strong>{entry.application.displayName || providerTypeLabel(entry.application.providerType)}</strong>
                    <p>{providerTypeLabel(entry.application.providerType)} · {entry.application.referenceNumber}</p>
                    <span>{applicationAttentionReason(entry.application) ?? applicationLatestUpdateLabel(entry.application)}</span>
                    <small>Current stage: {applicationStageLabel(entry.application.currentStep)}</small>
                  </div>
                  <div className="provider-account-attention-meta">
                    <span>{applicationStatusPill(entry.application)}</span>
                    <small>Completion: {entry.application.completionPercent}% complete</small>
                    <small>Last updated: {formatDateTime(entry.application.updatedAt)}</small>
                    <small>Reference: {entry.application.referenceNumber}</small>
                    {applicationPrimaryActionHref(entry.application) ? (
                      <div className="cta-row">
                        <Link className="primary-button" to={applicationPrimaryActionHref(entry.application)!}>
                          {applicationPrimaryActionLabel(entry.application) ?? "View status"}
                        </Link>
                        {applicationSecondaryActionHref(entry.application) && applicationSecondaryActionLabel(entry.application) ? (
                          <Link className="secondary-button" to={applicationSecondaryActionHref(entry.application)!}>
                            {applicationSecondaryActionLabel(entry.application)}
                          </Link>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                </article>
              ) :
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
            description="Provider applications that are complete or published will remain visible below."
            variant="compact"
          />
        )}
      </section>

      <section className="provider-account-section">
        <div className="provider-account-section-heading">
          <div>
            <h2>My Provider Profiles</h2>
            <p>Application lifecycle, review, publication, and visibility are shown here.</p>
          </div>
          {supportedProviderTypes.length ? (
            <Link className="secondary-button" to={`${DISCOVER_ROUTES.listPractice.path}?mode=add`}>
              Create another profile
            </Link>
          ) : null}
        </div>
        {profileCards.length ? (
          <div className="provider-account-application-grid">
            {profileCards.map((application) => (
              <article className="provider-account-application-card" key={application.referenceNumber}>
                <div className="provider-account-card-header">
                  <div>
                    <strong>{application.displayName}</strong>
                    <p>{providerTypeLabel(application.providerType)} · {application.referenceNumber}</p>
                  </div>
                  <span className="provider-account-status-pill">{applicationStatusPill(application)}</span>
                </div>
                <dl className="provider-account-detail-list">
                  <div>
                    <dt>Status</dt>
                    <dd>{applicationStatusLabel(application.status)}</dd>
                  </div>
                  <div>
                    <dt>Current stage</dt>
                    <dd>{applicationStageLabel(application.currentStep)}</dd>
                  </div>
                  <div>
                    <dt>Completion</dt>
                    <dd>{application.completionPercent}% complete</dd>
                  </div>
                  <div>
                    <dt>Live profile</dt>
                    <dd>{application.status === "PUBLISHED" ? "Published" : "Private"}</dd>
                  </div>
                  <div>
                    <dt>Last updated</dt>
                    <dd>{formatDateTime(application.updatedAt)}</dd>
                  </div>
                  <div>
                    <dt>Reference</dt>
                    <dd>{application.referenceNumber}</dd>
                  </div>
                </dl>
                <div className="provider-account-card-actions">
                  {applicationPrimaryActionHref(application) ? (
                    <Link className="primary-button" to={applicationPrimaryActionHref(application)!}>
                      {applicationPrimaryActionLabel(application) ?? "View status"}
                    </Link>
                  ) : null}
                  {applicationSecondaryActionHref(application) && applicationSecondaryActionLabel(application) ? (
                    <Link className="secondary-button" to={applicationSecondaryActionHref(application)!}>
                      {applicationSecondaryActionLabel(application)}
                    </Link>
                  ) : null}
                </div>
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
            <p>Recent provider application updates ordered by the latest change.</p>
          </div>
        </div>
        {recentActivity.length ? (
          <div className="provider-account-activity-list">
            {recentActivity.map((application) => (
              <article className="provider-account-activity-item" key={`${application.referenceNumber}-activity`}>
                <strong>{application.displayName || providerTypeLabel(application.providerType)}</strong>
                <p>{application.referenceNumber}</p>
                <span>{applicationLatestUpdateLabel(application)} · {application.completionPercent}% complete</span>
                <small>{formatDateTime(application.updatedAt)}</small>
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
