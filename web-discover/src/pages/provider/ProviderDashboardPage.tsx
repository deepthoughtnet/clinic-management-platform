import { useEffect, useState } from "react";
import {
  CheckCircleOutlineOutlined,
  ContentCopyOutlined,
  DashboardOutlined,
  EditOutlined,
  VisibilityOutlined,
} from "@mui/icons-material";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { createProviderOnboardingAccess, discardWorkspaceApplication, loadProviderApplicationDashboard } from "../../api/providerAuth";
import { providerDocumentContentPath } from "../../api/providerOnboarding";
import type { ProviderDashboard, ProviderStatus, ProviderSubmittedSnapshot, ProviderType } from "../../api/providerOnboarding";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { PublicMediaImage } from "../../components/landing/PublicMediaImage";
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

type ViewMode = "status" | "preview";

type SummaryItem = {
  label: string;
  value: string;
};

type ChecklistItem = {
  label: string;
  complete: boolean;
  detail?: string;
};

function providerTypeLabel(providerType: ProviderType) {
  switch (providerType) {
    case "INDIVIDUAL_DOCTOR":
      return "Doctor";
    case "CLINIC":
      return "Clinic";
    case "HOSPITAL":
      return "Hospital";
    default:
      return String(providerType).replace(/_/g, " ").replace(/\b\w/g, (char: string) => char.toUpperCase());
  }
}

function providerTypeDescriptor(providerType: ProviderType) {
  switch (providerType) {
    case "INDIVIDUAL_DOCTOR":
      return "Doctor Profile";
    case "CLINIC":
      return "Clinic Profile";
    case "HOSPITAL":
      return "Hospital Profile";
    default:
      return `${providerTypeLabel(providerType)} Profile`;
  }
}

function statusLabel(status: ProviderStatus) {
  return status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
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

function formatCurrency(value?: number | null) {
  if (value == null || Number.isNaN(value)) {
    return null;
  }
  return `₹${new Intl.NumberFormat("en-IN").format(value)}`;
}

function readStoredToken(keys: string[]) {
  if (typeof localStorage === "undefined") {
    return "";
  }
  for (const key of keys) {
    const token = localStorage.getItem(key);
    if (token) return token;
  }
  return "";
}

function currentStageLabel(status: ProviderStatus) {
  switch (status) {
    case "DRAFT":
    case "CONTACT_VERIFIED":
    case "PROFILE_INCOMPLETE":
    case "READY_FOR_REVIEW":
      return "Preparation";
    case "SUBMITTED":
    case "UNDER_REVIEW":
      return "Verification";
    case "CHANGES_REQUESTED":
      return "Changes requested";
    case "APPROVED":
      return "Approval";
    case "PUBLISHED":
      return "Published";
    case "DISCARDED":
      return "Discarded";
    case "SUSPENDED":
      return "Suspended";
    case "ARCHIVED":
      return "Archived";
    default:
      return statusLabel(status);
  }
}

function nextStepsMessage(status: ProviderStatus, providerType: ProviderType) {
  const subject = `${providerTypeLabel(providerType).toLowerCase()} profile`;
  switch (status) {
    case "SUBMITTED":
      return `Your ${subject} has been submitted. The Jeevanam verification team will review it next. No action is required right now.`;
    case "UNDER_REVIEW":
      return "Your application is currently being reviewed. No action is required unless the reviewer requests more information.";
    case "CHANGES_REQUESTED":
      return "The reviewer has requested changes. Review the comments, update the highlighted sections, and then resubmit the application.";
    case "APPROVED":
      return "Your application has been approved and is waiting for publication.";
    case "PUBLISHED":
      return "Your profile is live on Jeevanam Discover. You can open the public profile from your dashboard.";
    case "DISCARDED":
      return "This onboarding was discarded and removed from active onboarding.";
    default:
      return "Continue editing the draft until it is ready for submission.";
  }
}

function notificationMessage(workspaceEmail: string | null, workspacePhone: string | null) {
  const email = workspaceEmail?.trim() || null;
  const phone = workspacePhone?.trim() || null;
  if (email && phone) {
    return "Updates will be sent to your registered email and mobile number, and they will also appear in your provider dashboard.";
  }
  if (email) {
    return "Updates will be sent to your registered email address and will also appear in your provider dashboard.";
  }
  if (phone) {
    return "Updates will be sent to your registered mobile number and will also appear in your provider dashboard.";
  }
  return "Updates will appear in your provider dashboard.";
}

function isPostSubmissionStatus(status: ProviderStatus) {
  return [
    "SUBMITTED",
    "UNDER_REVIEW",
    "CHANGES_REQUESTED",
    "APPROVED",
    "PUBLISHED",
    "DISCARDED",
    "SUSPENDED",
    "ARCHIVED",
  ].includes(status);
}

function submittedPreviewReady(snapshot: ProviderSubmittedSnapshot | null) {
  return Boolean(snapshot && snapshot.displayName && snapshot.documentCount > 0 && (snapshot.locationCount > 0 || snapshot.services.length > 0));
}

function summaryItemsFor(snapshot: ProviderSubmittedSnapshot | null, dashboard: ProviderDashboard): SummaryItem[] {
  if (!snapshot) {
    const application = dashboard.application;
    return [
      { label: "Display name", value: application.displayName || "Not provided" },
      { label: "Status", value: statusLabel(application.status) },
      { label: "Completion", value: `${application.completionPercent}% complete` },
      { label: "Current step", value: application.currentStep.replaceAll("_", " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase()) },
    ];
  }

  switch (snapshot.providerType) {
    case "INDIVIDUAL_DOCTOR":
      return [
        { label: "Doctor", value: snapshot.displayName ?? "Not provided" },
        { label: "Primary speciality", value: snapshot.primarySpeciality ?? "Not provided" },
        { label: "Qualifications", value: snapshot.qualification ?? "Not provided" },
        { label: "Experience", value: snapshot.yearsOfExperience == null ? "Not provided" : `${snapshot.yearsOfExperience} years` },
        { label: "Practice locations", value: `${snapshot.locationCount}` },
        { label: "Consultation fee", value: formatCurrency(snapshot.consultationFee) ?? "Not provided" },
        { label: "Languages", value: snapshot.languages.length ? snapshot.languages.join(", ") : "Not provided" },
        { label: "Documents", value: `${snapshot.documentCount}` },
      ];
    case "CLINIC":
      return [
        { label: "Clinic", value: snapshot.displayName ?? "Not provided" },
        { label: "Primary speciality", value: snapshot.primarySpeciality ?? "Not provided" },
        { label: "Services", value: `${snapshot.serviceCount}` },
        { label: "Locations", value: `${snapshot.locationCount}` },
        { label: "Consultation fee", value: formatCurrency(snapshot.consultationFee) ?? "Not provided" },
        { label: "Languages", value: snapshot.languages.length ? snapshot.languages.join(", ") : "Not provided" },
        { label: "Documents", value: `${snapshot.documentCount}` },
      ];
    case "HOSPITAL":
      return [
        { label: "Hospital", value: snapshot.displayName ?? "Not provided" },
        { label: "Hospital type", value: snapshot.hospitalType ?? "Not provided" },
        { label: "Departments", value: `${snapshot.departments.length}` },
        { label: "Services", value: `${snapshot.serviceCount}` },
        { label: "Locations", value: `${snapshot.locationCount}` },
        { label: "Beds", value: snapshot.beds == null ? "Not provided" : `${snapshot.beds}` },
        { label: "Emergency", value: snapshot.emergencyAvailable ? "24×7 available" : "Not provided" },
        { label: "Documents", value: `${snapshot.documentCount}` },
      ];
    default:
      return [
        { label: "Display name", value: snapshot.displayName ?? "Not provided" },
        { label: "Documents", value: `${snapshot.documentCount}` },
      ];
  }
}

function checklistItemsFor(snapshot: ProviderSubmittedSnapshot | null, dashboard: ProviderDashboard): ChecklistItem[] {
  const application = dashboard.application;
  const submitted = isPostSubmissionStatus(application.status);
  const documentsUploaded = snapshot ? snapshot.documentCount > 0 : application.documents.length > 0;
  const locationCount = snapshot ? snapshot.locationCount : application.locations.length;
  const serviceCount = snapshot ? snapshot.serviceCount : application.services.filter((service) => service.enabled !== false).length;
  const hasBranding = snapshot
    ? Boolean(snapshot.logoDocumentId || snapshot.coverImageDocumentId || snapshot.doctorPhotoDocumentId || snapshot.galleryDocumentIds.length)
    : Boolean(application.branding?.logoDocumentId || application.branding?.coverImageDocumentId || application.branding?.doctorPhotoDocumentId);

  if (application.providerType === "INDIVIDUAL_DOCTOR") {
    return [
      { label: "Account and contact verification", complete: application.contactVerified, detail: application.contactVerified ? "Phone or email verification is complete." : "Contact verification is pending." },
      { label: "Personal information", complete: Boolean(snapshot?.displayName && snapshot.biography), detail: snapshot?.displayName ? snapshot.displayName : "Add the doctor profile details." },
      { label: "Professional registration", complete: Boolean(snapshot?.medicalCouncil && snapshot?.qualification), detail: snapshot?.medicalCouncil ?? "Add the registration council." },
      { label: "Qualifications", complete: Boolean(snapshot?.qualification && snapshot?.yearsOfExperience != null), detail: snapshot?.qualification ?? "Add qualification details." },
      { label: "Specialities", complete: Boolean(snapshot?.specialities.length || (application.specialities ?? []).length), detail: snapshot?.primarySpeciality ?? "Add at least one speciality." },
      { label: "Practice locations", complete: locationCount > 0, detail: locationCount ? `${locationCount} location${locationCount === 1 ? "" : "s"} captured.` : "Add at least one practice location." },
      { label: "Profile photo and branding", complete: Boolean(snapshot?.doctorPhotoDocumentId || snapshot?.coverImageDocumentId || snapshot?.logoDocumentId), detail: hasBranding ? "Media assets are ready." : "Add the profile photo and cover art." },
      { label: "Required documents", complete: documentsUploaded, detail: documentsUploaded ? `${snapshot?.documentCount ?? application.documents.length} document${(snapshot?.documentCount ?? application.documents.length) === 1 ? "" : "s"} uploaded.` : "Upload the registration documents." },
      { label: "Public profile preview", complete: submitted ? submittedPreviewReady(snapshot) || dashboard.completion.previewReady : false, detail: submitted ? "Submitted preview is available." : "Preview is available once the profile is ready." },
      { label: "Application submitted", complete: submitted, detail: submitted ? "Submission has been recorded." : "Submit the completed profile." },
    ];
  }

  if (application.providerType === "CLINIC") {
    return [
      { label: "Account and contact verification", complete: application.contactVerified, detail: application.contactVerified ? "Phone or email verification is complete." : "Contact verification is pending." },
      { label: "Organisation information", complete: Boolean(snapshot?.displayName && snapshot?.organisationType && application.registrationNumber), detail: snapshot?.organisationType ?? "Add the organisation type." },
      { label: "Professional details", complete: Boolean(snapshot?.primarySpeciality && snapshot?.languages.length), detail: snapshot?.primarySpeciality ?? "Add professional profile details." },
      { label: "Services", complete: serviceCount > 0, detail: serviceCount ? `${serviceCount} service${serviceCount === 1 ? "" : "s"} selected.` : "Add at least one service." },
      { label: "Locations", complete: locationCount > 0, detail: locationCount ? `${locationCount} location${locationCount === 1 ? "" : "s"} captured.` : "Add a primary location." },
      { label: "Branding and media", complete: Boolean(snapshot?.logoDocumentId && snapshot?.coverImageDocumentId), detail: hasBranding ? "Logo and cover image are ready." : "Upload logo and cover image." },
      { label: "Required documents", complete: documentsUploaded, detail: documentsUploaded ? `${snapshot?.documentCount ?? application.documents.length} document${(snapshot?.documentCount ?? application.documents.length) === 1 ? "" : "s"} uploaded.` : "Upload the registration document." },
      { label: "Public profile preview", complete: submitted ? submittedPreviewReady(snapshot) || dashboard.completion.previewReady : false, detail: submitted ? "Submitted preview is available." : "Preview is available once the profile is ready." },
      { label: "Application submitted", complete: submitted, detail: submitted ? "Submission has been recorded." : "Submit the completed profile." },
    ];
  }

  return [
    { label: "Account and contact verification", complete: application.contactVerified, detail: application.contactVerified ? "Phone or email verification is complete." : "Contact verification is pending." },
    { label: "Organisation information", complete: Boolean(snapshot?.displayName && snapshot?.ownership && snapshot?.hospitalType), detail: snapshot?.hospitalType ?? "Add organisation details." },
    { label: "Specialities / Departments", complete: Boolean(snapshot?.departments.length && snapshot?.specialities.length), detail: snapshot?.departments.length ? `${snapshot.departments.length} departments listed.` : "Add departments and specialities." },
    { label: "Services / Facilities", complete: Boolean(serviceCount > 0 && snapshot?.facilities.length), detail: serviceCount ? `${serviceCount} service${serviceCount === 1 ? "" : "s"} selected.` : "Add services and facilities." },
    { label: "Locations", complete: locationCount > 0, detail: locationCount ? `${locationCount} location${locationCount === 1 ? "" : "s"} captured.` : "Add a primary location." },
    { label: "Branding and gallery", complete: Boolean(snapshot?.logoDocumentId && snapshot.galleryCount > 0), detail: hasBranding ? "Logo and gallery images are ready." : "Upload logo and gallery images." },
    { label: "Required documents", complete: documentsUploaded, detail: documentsUploaded ? `${snapshot?.documentCount ?? application.documents.length} document${(snapshot?.documentCount ?? application.documents.length) === 1 ? "" : "s"} uploaded.` : "Upload the registration document." },
    { label: "Public profile preview", complete: submitted ? submittedPreviewReady(snapshot) || dashboard.completion.previewReady : false, detail: submitted ? "Submitted preview is available." : "Preview is available once the profile is ready." },
    { label: "Application submitted", complete: submitted, detail: submitted ? "Submission has been recorded." : "Submit the completed profile." },
  ];
}

function timelineStages(status: ProviderStatus) {
  return [
    { key: "draft", label: "Draft" },
    { key: "submitted", label: "Submitted" },
    { key: "review", label: "Under Review" },
    { key: "decision", label: status === "CHANGES_REQUESTED" ? "Changes Requested" : status === "APPROVED" || status === "PUBLISHED" ? "Approved" : "Decision" },
    { key: "published", label: "Published" },
  ].map((stage, index, stages) => {
    const currentIndex = (() => {
      switch (status) {
        case "DRAFT":
        case "CONTACT_VERIFIED":
        case "PROFILE_INCOMPLETE":
        case "READY_FOR_REVIEW":
          return 0;
        case "SUBMITTED":
          return 1;
        case "UNDER_REVIEW":
          return 2;
        case "CHANGES_REQUESTED":
        case "APPROVED":
          return 3;
        case "PUBLISHED":
          return 4;
        case "DISCARDED":
        case "SUSPENDED":
        case "ARCHIVED":
        default:
          return 3;
      }
    })();
    return {
      ...stage,
      complete: index < currentIndex || (status === "PUBLISHED" && index <= currentIndex),
      current: index === currentIndex,
      future: index > currentIndex,
      last: index === stages.length - 1,
    };
  });
}

function copyToClipboard(text: string) {
  return navigator.clipboard?.writeText(text);
}

function ProviderApplicationStatusBanner({
  dashboard,
  workspaceEmail,
  workspacePhone,
}: {
  dashboard: ProviderDashboard;
  workspaceEmail: string | null;
  workspacePhone: string | null;
}) {
  const { application, submittedSnapshot } = dashboard;
  const isSubmittedOrLater = isPostSubmissionStatus(application.status);
  const title = application.status === "SUBMITTED"
    ? "Application Submitted Successfully"
    : application.status === "UNDER_REVIEW"
      ? "Application Under Review"
      : application.status === "CHANGES_REQUESTED"
        ? "Changes Requested"
        : application.status === "APPROVED"
          ? "Application Approved"
          : application.status === "PUBLISHED"
            ? "Profile Published"
            : `${providerTypeLabel(application.providerType)} application`;

  return (
    <article className="provider-status-banner" role="status" aria-live="polite">
      <div className="provider-status-banner-copy">
        <span className="eyebrow">{providerTypeDescriptor(application.providerType)}</span>
        <h1>{title}</h1>
        <p>
          {application.status === "SUBMITTED"
            ? `Your ${providerTypeLabel(application.providerType).toLowerCase()} profile has been submitted to Jeevanam for verification.`
            : application.status === "PUBLISHED"
              ? `Your ${providerTypeLabel(application.providerType).toLowerCase()} profile is live on Jeevanam Discover.`
              : nextStepsMessage(application.status, application.providerType)}
        </p>
        {isSubmittedOrLater ? <small>{notificationMessage(workspaceEmail, workspacePhone)}</small> : null}
      </div>
      <div className="provider-status-banner-reference">
        <strong>Reference</strong>
        <div className="provider-status-reference-row">
          <code>{application.referenceNumber}</code>
          <button className="secondary-button" type="button" onClick={() => void copyToClipboard(application.referenceNumber)}>
            Copy
          </button>
        </div>
        <span>Submitted {submittedSnapshot?.submittedAt ? formatDateTime(submittedSnapshot.submittedAt) : formatDateTime(application.submittedAt)}</span>
      </div>
    </article>
  );
}

function ProviderApplicationStatusCard({ dashboard }: { dashboard: ProviderDashboard }) {
  const { application, submittedSnapshot } = dashboard;
  const hasSubmission = Boolean(submittedSnapshot || application.submittedAt);
  return (
    <article className="provider-status-card">
      <div className="provider-status-card-head">
        <div>
          <span className="eyebrow">Application Status</span>
          <h2>{statusLabel(application.status)}</h2>
        </div>
        <span className="provider-account-status-pill">{statusLabel(application.status)}</span>
      </div>
      <dl className="provider-status-definition-list">
        <div>
          <dt>Reference</dt>
          <dd>{application.referenceNumber}</dd>
        </div>
        <div>
          <dt>Submitted</dt>
          <dd>{submittedSnapshot?.submittedAt ? formatDateTime(submittedSnapshot.submittedAt) : hasSubmission ? formatDateTime(application.submittedAt) : "Not yet submitted"}</dd>
        </div>
        <div>
          <dt>Current stage</dt>
          <dd>{currentStageLabel(application.status)}</dd>
        </div>
        <div>
          <dt>Last updated</dt>
          <dd>{formatDateTime(application.lastSavedAt)}</dd>
        </div>
        <div>
          <dt>Application version</dt>
          <dd>{submittedSnapshot ? `v${submittedSnapshot.versionNumber}` : `v${application.version}`}</dd>
        </div>
        <div>
          <dt>Completion</dt>
          <dd>{application.status === "SUBMITTED" || application.status === "UNDER_REVIEW" || application.status === "CHANGES_REQUESTED" || application.status === "APPROVED" || application.status === "PUBLISHED"
            ? "100% complete"
            : `${application.completionPercent}% complete`}</dd>
        </div>
      </dl>
    </article>
  );
}

function ProviderApplicationReviewSummary({ dashboard }: { dashboard: ProviderDashboard }) {
  const items = checklistItemsFor(dashboard.submittedSnapshot, dashboard);
  return (
    <article className="provider-status-panel">
      <div className="provider-status-panel-heading">
        <div>
          <span className="eyebrow">Review summary</span>
          <h2>What has been completed</h2>
        </div>
        <span>{dashboard.application.status === "SUBMITTED" ? "Ready for verification" : `${dashboard.completion.completionPercentage}% complete`}</span>
      </div>
      <div className="provider-status-checklist">
        {items.map((item) => (
          <div key={item.label} className={`provider-status-checklist-item${item.complete ? " is-complete" : ""}`}>
            <CheckCircleOutlineOutlined fontSize="small" aria-hidden="true" />
            <div>
              <strong>{item.label}</strong>
              <p>{item.detail ?? (item.complete ? "Completed." : "Pending.")}</p>
            </div>
          </div>
        ))}
      </div>
    </article>
  );
}

function ProviderApplicationSummary({ dashboard }: { dashboard: ProviderDashboard }) {
  const summary = summaryItemsFor(dashboard.submittedSnapshot, dashboard);
  return (
    <article className="provider-status-panel">
      <div className="provider-status-panel-heading">
        <div>
          <span className="eyebrow">Submitted snapshot</span>
          <h2>{providerTypeDescriptor(dashboard.application.providerType)}</h2>
        </div>
      </div>
      <dl className="provider-status-definition-list provider-status-definition-list--summary">
        {summary.map((item) => (
          <div key={item.label}>
            <dt>{item.label}</dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
    </article>
  );
}

function ProviderApplicationNextSteps({ dashboard }: { dashboard: ProviderDashboard }) {
  const { application } = dashboard;
  return (
    <article className="provider-status-panel">
      <div className="provider-status-panel-heading">
        <div>
          <span className="eyebrow">Next steps</span>
          <h2>What happens now</h2>
        </div>
      </div>
      <p className="provider-status-next-steps">{nextStepsMessage(application.status, application.providerType)}</p>
      {dashboard.changeRequests.length ? (
        <div className="provider-status-notes">
          {dashboard.changeRequests.map((request) => (
            <div className="provider-status-note" key={request.id}>
              <strong>Changes requested</strong>
              <p>{request.reviewerMessage ?? "Review team feedback"}</p>
              {request.requestedSections.length ? <small>{request.requestedSections.join(", ")}</small> : null}
            </div>
          ))}
        </div>
      ) : null}
    </article>
  );
}

function ProviderApplicationTimeline({ dashboard }: { dashboard: ProviderDashboard }) {
  const stages = timelineStages(dashboard.application.status);
  return (
    <article className="provider-status-panel">
      <div className="provider-status-panel-heading">
        <div>
          <span className="eyebrow">Timeline</span>
          <h2>Lifecycle progress</h2>
        </div>
      </div>
      <ol className="provider-status-timeline">
        {stages.map((stage) => (
          <li key={stage.key} className={`provider-status-timeline-stage${stage.complete ? " is-complete" : ""}${stage.current ? " is-current" : ""}${stage.future ? " is-future" : ""}`}>
            <span className="provider-status-timeline-dot" aria-hidden="true">
              {stage.complete ? <CheckCircleOutlineOutlined fontSize="small" /> : null}
            </span>
            <div>
              <strong>{stage.label}</strong>
              <p>
                {stage.current
                  ? "Current stage"
                  : stage.complete
                    ? "Completed"
                    : stage.future && stage.key === "decision" && dashboard.application.status !== "CHANGES_REQUESTED"
                      ? "Conditional"
                      : "Future stage"}
              </p>
            </div>
          </li>
        ))}
      </ol>
      <details className="provider-status-history">
        <summary>Lifecycle history</summary>
        <div className="provider-status-history-list">
          {dashboard.timeline.length ? dashboard.timeline.map((event) => (
            <article key={`${event.label}-${event.timestamp}`} className="provider-status-history-item">
              <div>
                <strong>{event.label}</strong>
                <p>{event.description ?? "No additional details provided."}</p>
              </div>
              <small>
                {formatDateTime(event.timestamp)}
                {event.actorCategory ? ` · ${event.actorCategory}` : ""}
              </small>
            </article>
          )) : <p>No lifecycle history is available yet.</p>}
        </div>
      </details>
    </article>
  );
}

function ProviderApplicationActions({
  dashboard,
  view,
  setView,
  onOpenOnboarding,
  onOpenDashboard,
  onDiscard,
}: {
  dashboard: ProviderDashboard;
  view: ViewMode;
  setView: (view: ViewMode) => void;
  onOpenOnboarding: () => void;
  onOpenDashboard: () => void;
  onDiscard: () => void;
}) {
  const { application } = dashboard;
  const previewEnabled = submittedPreviewReady(dashboard.submittedSnapshot) || dashboard.completion.previewReady;
  const publicProfilePath = dashboard.publicProfilePath;

  if (application.status === "CHANGES_REQUESTED") {
    return (
      <div className="provider-status-actions">
        <button className="primary-button" type="button" onClick={onOpenOnboarding}>
          Review requested changes
        </button>
        <button className="secondary-button" type="button" onClick={onOpenOnboarding}>
          Edit application
        </button>
        <button className="secondary-button" type="button" onClick={() => setView("preview")} disabled={!previewEnabled}>
          View previous submission
        </button>
        <button className="secondary-button" type="button" onClick={onOpenDashboard}>
          Go to dashboard
        </button>
        {!previewEnabled ? <span className="provider-dashboard-helper">Submitted preview is not available yet.</span> : null}
      </div>
    );
  }

  if (application.status === "SUBMITTED" || application.status === "UNDER_REVIEW") {
    return (
      <div className="provider-status-actions">
        <button className="primary-button" type="button" onClick={onOpenDashboard}>
          Go to dashboard
        </button>
        <button className="secondary-button" type="button" onClick={() => setView("preview")} disabled={!previewEnabled}>
          View Submitted Preview
        </button>
        {!previewEnabled ? <span className="provider-dashboard-helper">Submitted preview is not available yet.</span> : null}
      </div>
    );
  }

  if (application.status === "APPROVED") {
    return (
      <div className="provider-status-actions">
        <button className="primary-button" type="button" onClick={() => setView("preview")} disabled={!previewEnabled}>
          View Approved Preview
        </button>
        <button className="secondary-button" type="button" onClick={onOpenDashboard}>
          Go to dashboard
        </button>
      </div>
    );
  }

  if (application.status === "PUBLISHED") {
    return (
      <div className="provider-status-actions">
        {publicProfilePath ? (
          <a className="primary-button" href={publicProfilePath} target="_blank" rel="noreferrer">
            View Public Profile
          </a>
        ) : (
          <button className="primary-button" type="button" disabled>
            View Public Profile
          </button>
        )}
        <button className="secondary-button" type="button" onClick={onOpenDashboard}>
          Go to dashboard
        </button>
      </div>
    );
  }

  if (application.status === "DRAFT" || application.status === "CONTACT_VERIFIED" || application.status === "PROFILE_INCOMPLETE" || application.status === "READY_FOR_REVIEW") {
    return (
      <div className="provider-status-actions">
        <button className="primary-button" type="button" onClick={onOpenOnboarding}>
          Continue registration
        </button>
        <button className="secondary-button" type="button" onClick={() => setView("preview")} disabled={!previewEnabled}>
          View Submitted Preview
        </button>
        {dashboard.application.status === "DRAFT" || dashboard.application.status === "CONTACT_VERIFIED" || dashboard.application.status === "PROFILE_INCOMPLETE" || dashboard.application.status === "READY_FOR_REVIEW" ? (
          <button className="secondary-button" type="button" onClick={onDiscard}>
            Discard onboarding
          </button>
        ) : null}
      </div>
    );
  }

  return (
    <div className="provider-status-actions">
      <button className="primary-button" type="button" onClick={onOpenDashboard}>
        Go to dashboard
      </button>
    </div>
  );
}

function ProviderSubmittedPreview({ dashboard }: { dashboard: ProviderDashboard }) {
  const snapshot = dashboard.submittedSnapshot;
  const token = readStoredToken(TOKEN_KEYS);
  if (!snapshot) {
    return (
      <article className="provider-status-panel">
        <div className="provider-status-panel-heading">
          <div>
            <span className="eyebrow">Submitted preview</span>
            <h2>Preview unavailable</h2>
          </div>
        </div>
        <p>The submitted snapshot is not available yet.</p>
      </article>
    );
  }

  const coverUrl = snapshot.coverImageDocumentId ? providerDocumentContentPath(dashboard.application.id, snapshot.coverImageDocumentId) : null;
  const avatarDocumentId = snapshot.providerType === "INDIVIDUAL_DOCTOR" ? snapshot.doctorPhotoDocumentId : snapshot.logoDocumentId;
  const avatarUrl = avatarDocumentId ? providerDocumentContentPath(dashboard.application.id, avatarDocumentId) : null;
  const heroSummary = [
    snapshot.qualification,
    snapshot.primarySpeciality,
    snapshot.hospitalType,
  ].filter(Boolean).join(" • ");
  const consultationFee = formatCurrency(snapshot.consultationFee);
  const locationSummary = snapshot.locations.length ? `${snapshot.locations.length} location${snapshot.locations.length === 1 ? "" : "s"}` : "No locations captured";

  return (
    <article className="provider-status-panel provider-status-preview-panel">
      <div className="provider-status-panel-heading">
        <div>
          <span className="eyebrow">Submitted preview</span>
          <h2>Immutable snapshot v{snapshot.versionNumber}</h2>
        </div>
        <span>{formatDateTime(snapshot.submittedAt)}</span>
      </div>
      <div className="provider-status-preview">
        <div className="provider-status-preview-media">
          <PublicMediaImage
            src={coverUrl}
            alt={`${snapshot.displayName ?? providerTypeLabel(snapshot.providerType)} cover image`}
            className="provider-status-preview-cover"
            objectFit="cover"
            fallback={<div className="provider-status-preview-cover-fallback" aria-hidden="true" />}
            loading="eager"
            token={token}
          />
          <div className="provider-status-preview-avatar">
            <PublicMediaImage
              src={avatarUrl}
              alt={`${snapshot.displayName ?? providerTypeLabel(snapshot.providerType)} ${snapshot.providerType === "INDIVIDUAL_DOCTOR" ? "photo" : "logo"}`}
              className="provider-status-preview-avatar-image"
              objectFit={snapshot.providerType === "INDIVIDUAL_DOCTOR" ? "cover" : "contain"}
              fallback={<div className="provider-status-preview-avatar-fallback" aria-hidden="true">{providerTypeLabel(snapshot.providerType).slice(0, 2).toUpperCase()}</div>}
              token={token}
            />
          </div>
        </div>
        <div className="provider-status-preview-copy">
          <strong>{snapshot.displayName ?? "Not provided"}</strong>
          {heroSummary ? <p>{heroSummary}</p> : null}
          <dl className="provider-status-preview-facts">
            {snapshot.primarySpeciality ? <div><dt>Primary speciality</dt><dd>{snapshot.primarySpeciality}</dd></div> : null}
            {snapshot.providerType === "INDIVIDUAL_DOCTOR" && snapshot.yearsOfExperience != null ? <div><dt>Experience</dt><dd>{snapshot.yearsOfExperience} years</dd></div> : null}
            {snapshot.providerType === "CLINIC" && snapshot.organisationType ? <div><dt>Organisation type</dt><dd>{snapshot.organisationType}</dd></div> : null}
            {snapshot.providerType === "HOSPITAL" && snapshot.hospitalType ? <div><dt>Hospital type</dt><dd>{snapshot.hospitalType}</dd></div> : null}
            {consultationFee ? <div><dt>Consultation fee</dt><dd>{consultationFee}</dd></div> : null}
            <div><dt>Services</dt><dd>{snapshot.serviceCount}</dd></div>
            <div><dt>Locations</dt><dd>{locationSummary}</dd></div>
            <div><dt>Languages</dt><dd>{snapshot.languages.length ? snapshot.languages.join(", ") : "Not provided"}</dd></div>
            <div><dt>Documents</dt><dd>{snapshot.documentCount}</dd></div>
          </dl>
        </div>
      </div>
    </article>
  );
}

export function ProviderDashboardPage() {
  const navigate = useNavigate();
  const { applicationReference } = useParams<{ applicationReference: string }>();
  const { workspace, refreshSession } = useProviderSession();
  const [searchParams, setSearchParams] = useSearchParams();
  const [dashboard, setDashboard] = useState<ProviderDashboard | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [openingOnboarding, setOpeningOnboarding] = useState(false);
  const [discardOpen, setDiscardOpen] = useState(false);
  const [discardReason, setDiscardReason] = useState("");
  const [discardBusy, setDiscardBusy] = useState(false);
  const [discardError, setDiscardError] = useState<string | null>(null);

  const view = (searchParams.get("view") === "preview" ? "preview" : "status") as ViewMode;
  const workspaceEmail = workspace?.contactEmail ?? null;
  const workspacePhone = workspace?.contactPhone ?? null;

  useEffect(() => {
    if (!applicationReference) {
      setDashboard(null);
      return;
    }
    setLoading(true);
    setError(null);
    setDashboard(null);
    loadProviderApplicationDashboard(applicationReference)
      .then((result) => setDashboard(result))
      .catch((ex) => {
        setError(ex instanceof Error ? ex.message : "Could not load the selected provider application.");
        setDashboard(null);
      })
      .finally(() => setLoading(false));
  }, [applicationReference]);

  async function openOnboarding() {
    if (!applicationReference || !dashboard) return;
    setOpeningOnboarding(true);
    setError(null);
    try {
      const access = await createProviderOnboardingAccess(applicationReference);
      for (const key of TOKEN_KEYS) {
        localStorage.removeItem(key);
      }
      localStorage.setItem(TOKEN_KEY, access.onboardingToken);
      navigate(`/provider/onboarding/${access.applicationId}/${providerOnboardingStepRoute(dashboard.completion.currentStep)}`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not open the selected application.");
    } finally {
      setOpeningOnboarding(false);
    }
  }

  function openDashboard() {
    navigate(DISCOVER_ROUTES.providerWorkspace.path);
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
      <section className="page-section provider-status-page">
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
      <section className="page-section provider-status-page">
        <div className="provider-dashboard-skeleton" role="status" aria-label="Loading provider application status">
          <span />
          <span />
          <span />
        </div>
      </section>
    );
  }

  const previewEnabled = submittedPreviewReady(dashboard.submittedSnapshot) || dashboard.completion.previewReady;

  return (
    <section className="page-section provider-status-page">
      <header className="provider-status-hero">
        <div>
          <span className="eyebrow">{providerTypeDescriptor(dashboard.application.providerType)}</span>
          <h1>{dashboard.application.displayName || providerTypeLabel(dashboard.application.providerType)} application</h1>
          <p>{dashboard.application.referenceNumber}</p>
        </div>
        <aside className="provider-status-hero-card">
          <div className="provider-status-hero-card-row">
            <strong>{statusLabel(dashboard.application.status)}</strong>
            <button className="secondary-button" type="button" onClick={() => void copyToClipboard(dashboard.application.referenceNumber)}>
              <ContentCopyOutlined fontSize="small" aria-hidden="true" />
              Copy reference
            </button>
          </div>
          <div className="provider-status-hero-card-meta">
            <span>Submitted {dashboard.submittedSnapshot ? formatDateTime(dashboard.submittedSnapshot.submittedAt) : formatDateTime(dashboard.application.submittedAt)}</span>
            <span>Current stage: {currentStageLabel(dashboard.application.status)}</span>
            <span>Last updated: {formatDateTime(dashboard.application.lastSavedAt)}</span>
            <span>Version: {dashboard.submittedSnapshot ? `v${dashboard.submittedSnapshot.versionNumber}` : `v${dashboard.application.version}`}</span>
          </div>
        </aside>
      </header>

      <ProviderApplicationStatusBanner dashboard={dashboard} workspaceEmail={workspaceEmail} workspacePhone={workspacePhone} />

      <div className="provider-status-toolbar">
        <button className="primary-button" type="button" onClick={() => setSearchParams({ view: "status" })}>
          <DashboardOutlined fontSize="small" aria-hidden="true" />
          Application status
        </button>
        <button className="secondary-button" type="button" onClick={() => setSearchParams({ view: "preview" })} disabled={!previewEnabled}>
          <VisibilityOutlined fontSize="small" aria-hidden="true" />
          {dashboard.application.status === "PUBLISHED" ? "View Public Profile" : "View Submitted Preview"}
        </button>
        {dashboard.application.status === "CHANGES_REQUESTED" ? (
          <button className="secondary-button" type="button" onClick={openOnboarding}>
            <EditOutlined fontSize="small" aria-hidden="true" />
            Edit application
          </button>
        ) : null}
      </div>

      <div className="provider-status-layout">
        <div className="provider-status-main">
          <ProviderApplicationStatusCard dashboard={dashboard} />
          <ProviderApplicationReviewSummary dashboard={dashboard} />
          <ProviderApplicationSummary dashboard={dashboard} />
          <ProviderApplicationNextSteps dashboard={dashboard} />
          <ProviderApplicationTimeline dashboard={dashboard} />
          {view === "preview" ? <ProviderSubmittedPreview dashboard={dashboard} /> : null}
        </div>
        <aside className="provider-status-side">
          <ProviderApplicationActions
            dashboard={dashboard}
            view={view}
            setView={(nextView) => setSearchParams({ view: nextView })}
            onOpenOnboarding={() => void openOnboarding()}
            onOpenDashboard={openDashboard}
            onDiscard={() => setDiscardOpen(true)}
          />
          <article className="provider-status-panel">
            <div className="provider-status-panel-heading">
              <div>
                <span className="eyebrow">Attention</span>
                <h2>Current state</h2>
              </div>
            </div>
            <p className="provider-status-next-steps">
              {dashboard.application.status === "SUBMITTED" || dashboard.application.status === "UNDER_REVIEW"
                ? "No action is required from you at this time."
                : dashboard.application.status === "CHANGES_REQUESTED"
                  ? "Review the requested changes and update the relevant sections."
                  : dashboard.application.status === "PUBLISHED"
                    ? "Your published profile is live on Jeevanam Discover."
                    : "Continue the draft and submit when it is ready."}
            </p>
          </article>
        </aside>
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

      {(loading || openingOnboarding || discardBusy) ? (
        <p className="autosave-row" role="status">
          {discardBusy ? "Discarding onboarding…" : openingOnboarding ? "Opening application…" : "Loading application status…"}
        </p>
      ) : null}
    </section>
  );
}
