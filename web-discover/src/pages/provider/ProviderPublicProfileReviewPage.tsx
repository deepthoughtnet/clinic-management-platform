import * as React from "react";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Alert, Button, Chip, Divider, Paper, Stack, Step, StepLabel, Stepper, Typography } from "@mui/material";
import AssignmentTurnedInOutlinedIcon from "@mui/icons-material/AssignmentTurnedInOutlined";
import CheckCircleOutlinedIcon from "@mui/icons-material/CheckCircleOutlined";
import PublishedWithChangesOutlinedIcon from "@mui/icons-material/PublishedWithChangesOutlined";
import RateReviewOutlinedIcon from "@mui/icons-material/RateReviewOutlined";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { DISCOVER_ROUTES } from "../../routes";
import { LandingPageRenderer } from "../../components/landing/LandingPageRenderer";
import { loadProviderPublicProfileReview, reviewToLandingPreview, type ProviderPublicProfileReviewFindingResponse, type ProviderPublicProfileReviewResponse } from "../../api/providerPublicProfileReview";

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "Not yet recorded";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "Not yet recorded" : new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(parsed);
}

function statusLabel(status: string | null | undefined) {
  switch ((status || "").toUpperCase()) {
    case "SUBMITTED":
      return "Pending review";
    case "UNDER_REVIEW":
      return "Review in progress";
    case "CHANGES_REQUESTED":
      return "Changes requested";
    case "REJECTED":
      return "Rejected";
    case "APPROVED":
      return "Approved";
    case "PUBLISHED":
      return "Published";
    case "UNPUBLISHED":
      return "Unpublished";
    default:
      return status ? status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (value) => value.toUpperCase()) : "Not available";
  }
}

function statusTone(status: string | null | undefined) {
  switch ((status || "").toUpperCase()) {
    case "SUBMITTED":
    case "UNDER_REVIEW":
      return "warning" as const;
    case "CHANGES_REQUESTED":
      return "warning" as const;
    case "REJECTED":
      return "error" as const;
    case "APPROVED":
    case "PUBLISHED":
      return "success" as const;
    default:
      return "default" as const;
  }
}

function moderationStep(status: string | null | undefined) {
  switch ((status || "").toUpperCase()) {
    case "SUBMITTED":
      return 0;
    case "UNDER_REVIEW":
      return 1;
    case "CHANGES_REQUESTED":
    case "REJECTED":
      return 2;
    case "APPROVED":
    case "PUBLISHED":
      return 3;
    default:
      return 0;
  }
}

function reviewActionLabel(action: string) {
  switch (action) {
    case "BACK_TO_WORKSPACE":
      return "Back to workspace";
    case "VIEW_SUBMITTED_PROFILE":
      return "View submitted preview";
    case "REVIEW_REQUESTED_CHANGES":
      return "Review requested changes";
    case "OPEN_EDITABLE_DRAFT":
      return "Open editable draft";
    case "VIEW_APPROVAL_STATUS":
      return "View approval status";
    case "VIEW_PUBLIC_PROFILE":
      return "View public profile";
    case "VIEW_REJECTION_STATUS":
      return "View rejection status";
    case "START_NEW_REVISION":
      return "Start new revision";
    default:
      return action.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (value) => value.toUpperCase());
  }
}

function findingTone(finding: ProviderPublicProfileReviewFindingResponse) {
  return ((finding.severity || "").toUpperCase() === "BLOCKING" || finding.required) ? "error" : "warning";
}

function groupFindings(findings: ProviderPublicProfileReviewFindingResponse[]) {
  return findings.reduce<Record<string, ProviderPublicProfileReviewFindingResponse[]>>((groups, finding) => {
    const key = finding.section || "Other";
    groups[key] ||= [];
    groups[key].push(finding);
    return groups;
  }, {});
}

function nextStepLabel(status: string | null | undefined) {
  switch ((status || "").toUpperCase()) {
    case "SUBMITTED":
    case "UNDER_REVIEW":
      return "Platform review is in progress. The submitted version remains locked until the review outcome changes.";
    case "CHANGES_REQUESTED":
      return "Review the findings, open the editable draft, and make the requested corrections.";
    case "REJECTED":
      return "Review the rejection reason and start a new revision if you choose to continue.";
    case "APPROVED":
      return "The submission is approved and waiting for publication.";
    case "PUBLISHED":
      return "The profile is published. You can view the live public profile.";
    default:
      return "Follow the platform moderation outcome for the next required step.";
  }
}

function safeReviewerLabel(review: ProviderPublicProfileReviewResponse) {
  if (review.moderationStatus === "UNDER_REVIEW") {
    return "Platform review team";
  }
  if (review.moderationStatus === "CHANGES_REQUESTED" || review.moderationStatus === "REJECTED") {
    return "Platform review team";
  }
  return "Unassigned";
}

function reviewActionHref(action: string, review: ProviderPublicProfileReviewResponse) {
  const profileReference = review.publicProfileReference;
  switch (action) {
    case "BACK_TO_WORKSPACE":
      return DISCOVER_ROUTES.providerWorkspace.path;
    case "VIEW_SUBMITTED_PROFILE":
      return "#submitted-preview";
    case "REVIEW_REQUESTED_CHANGES":
      return "#findings";
    case "OPEN_EDITABLE_DRAFT":
    case "START_NEW_REVISION":
      return DISCOVER_ROUTES.providerPublicProfileDraft.path
        .replace(":profileReference", encodeURIComponent(profileReference))
        .replace(":section", "overview");
    case "VIEW_APPROVAL_STATUS":
      return "#decision";
    case "VIEW_REJECTION_STATUS":
      return "#decision";
    case "VIEW_PUBLIC_PROFILE":
      return review.publicUrl || DISCOVER_ROUTES.providerWorkspace.path;
    default:
      return null;
  }
}

export function ProviderPublicProfileReviewPage() {
  const params = useParams<{ profileReference?: string }>();
  const profileReference = params.profileReference?.trim() ?? "";
  const [review, setReview] = useState<ProviderPublicProfileReviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!profileReference) {
      setLoading(false);
      setError("Public profile reference missing");
      return;
    }
    let active = true;
    setLoading(true);
    setError(null);
    void loadProviderPublicProfileReview(profileReference)
      .then((next) => {
        if (!active) {
          return;
        }
        setReview(next);
      })
      .catch((loadError: unknown) => {
        if (!active) {
          return;
        }
        setError(loadError instanceof Error ? loadError.message : "Unable to load the review status.");
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [profileReference]);

  const { page, snapshot } = useMemo(() => (review ? reviewToLandingPreview(review) : { page: null, snapshot: null }), [review]);
  const groupedFindings = useMemo(() => (review ? groupFindings(review.findings) : {}), [review]);
  const openFindings = useMemo(() => (review ? review.findings.filter((finding) => (finding.resolutionStatus || "OPEN").toUpperCase() === "OPEN") : []), [review]);
  const blockingFindings = useMemo(() => openFindings.filter((finding) => (finding.severity || "").toUpperCase() === "BLOCKING" || finding.required), [openFindings]);

  useEffect(() => {
    const hash = window.location.hash.replace(/^#/, "");
    if (!hash) {
      return;
    }
    const element = document.getElementById(hash);
    element?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [review]);

  if (!profileReference) {
    return (
      <section className="page-section">
        <DiscoverEmptyState
          icon="!"
          title="Public profile reference missing"
          description="Open the profile from your provider workspace."
          primaryAction="Back to workspace"
          primaryTo={DISCOVER_ROUTES.providerWorkspace.path}
        />
      </section>
    );
  }

  if (error && !review) {
    return (
      <section className="page-section">
        <DiscoverEmptyState
          icon="!"
          title="Review status unavailable"
          description={error}
          primaryAction="Back to workspace"
          primaryTo={DISCOVER_ROUTES.providerWorkspace.path}
        />
      </section>
    );
  }

  if (loading && !review) {
    return (
      <section className="page-section">
        <div className="provider-dashboard-skeleton" role="status" aria-label="Loading review status">
          <span />
          <span />
          <span />
        </div>
      </section>
    );
  }

  if (!review || !page || !snapshot) {
    return null;
  }

  const currentStep = moderationStep(review.moderationStatus);
  const showDecisionReason = Boolean(review.decisionReason);
  const uniqueProviderActions = Array.from(new Set(review.providerAllowedActions || []));
  const reviewerLabel = safeReviewerLabel(review);

  return (
    <section className="page-section">
      <Stack spacing={2}>
        <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
          <Stack spacing={2}>
            <Stack spacing={1}>
              <Typography variant="overline" color="text.secondary">Provider review status</Typography>
              <Typography variant="h4" sx={{ fontWeight: 900 }}>{page.displayName}</Typography>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Chip label="Submitted for Platform Review" color="warning" variant="filled" />
                <Chip label={`Submitted version ${review.submittedDraftVersion}`} variant="outlined" />
                <Chip label={`Submitted ${formatDateTime(review.submittedAt)}`} variant="outlined" />
                <Chip label={`Moderation: ${statusLabel(review.moderationStatus)}`} color={statusTone(review.moderationStatus)} variant="outlined" />
                <Chip label={`Publication: ${statusLabel(review.publicationStatusSnapshot)}`} variant="outlined" />
              </Stack>
            </Stack>
            <Stepper activeStep={currentStep} alternativeLabel>
              <Step>
                <StepLabel icon={<AssignmentTurnedInOutlinedIcon />}>Submitted</StepLabel>
              </Step>
              <Step>
                <StepLabel icon={<RateReviewOutlinedIcon />}>Platform review</StepLabel>
              </Step>
              <Step>
                <StepLabel icon={<CheckCircleOutlinedIcon />}>Decision</StepLabel>
              </Step>
              <Step>
                <StepLabel icon={<PublishedWithChangesOutlinedIcon />}>Publication</StepLabel>
              </Step>
            </Stepper>
          </Stack>
        </Paper>

        <Stack direction={{ xs: "column", lg: "row" }} spacing={2} alignItems="flex-start">
          <Stack spacing={2} sx={{ flex: 1, minWidth: 0 }}>
            <Paper id="submitted-preview" variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
              <Stack spacing={2}>
                <Stack spacing={1}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>Submitted snapshot</Typography>
                  <Alert severity="info" variant="outlined">
                    This is the exact profile version submitted for Platform review. Later draft changes will not affect this submission.
                  </Alert>
                </Stack>
                <LandingPageRenderer page={page} snapshot={snapshot} renderMode="PROVIDER_REVIEW_STATUS" />
              </Stack>
            </Paper>

            <Paper id="findings" variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
              <Stack spacing={2}>
                <Stack spacing={0.5}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>Findings</Typography>
                  <Typography variant="body2" color="text.secondary">Open findings count: {openFindings.length}. Blocking findings count: {blockingFindings.length}.</Typography>
                </Stack>
                {review.findings.length ? (
                  <Stack spacing={2}>
                    {Object.entries(groupedFindings).map(([section, sectionFindings]) => (
                      <Stack key={section} spacing={1}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800, textTransform: "uppercase", letterSpacing: 0.4 }}>{section}</Typography>
                        <Stack spacing={1}>
                          {sectionFindings.map((finding) => (
                            <Paper
                              key={finding.findingReference}
                              variant="outlined"
                              sx={{
                                p: 1.5,
                                borderColor: finding.severity?.toUpperCase() === "BLOCKING" || finding.required ? "error.main" : "divider",
                              }}
                              >
                                <Stack spacing={0.5}>
                                  <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                                    <Chip size="small" label={finding.category || "Finding"} color={findingTone(finding)} variant="outlined" />
                                    <Chip size="small" label={finding.severity || "—"} variant="outlined" />
                                    <Chip size="small" label={finding.required ? "Provider action required" : "Non-blocking"} variant="outlined" />
                                  </Stack>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{finding.fieldKey || "Section review"}</Typography>
                                <Typography variant="body2" color="text.secondary">{finding.providerFacingMessage || finding.reviewerNote || "No provider-facing message recorded."}</Typography>
                                <Button
                                  size="small"
                                  variant="text"
                                  sx={{ alignSelf: "flex-start" }}
                                  onClick={() => {
                                    const target = finding.section ? document.getElementById(`landing-section-${finding.section.toUpperCase()}`) : null;
                                    target?.scrollIntoView({ behavior: "smooth", block: "center" });
                                  }}
                                >
                                  Jump to section
                                </Button>
                              </Stack>
                            </Paper>
                          ))}
                        </Stack>
                      </Stack>
                    ))}
                  </Stack>
                ) : (
                  <Alert severity="info" variant="outlined">No findings yet. Add findings when the review requires a correction or observation.</Alert>
                )}
              </Stack>
            </Paper>

            <Paper id="decision" variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
              <Stack spacing={1.5}>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>Decision and history</Typography>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Chip label={`Reviewer: ${reviewerLabel}`} variant="outlined" />
                  <Chip label={`Review started: ${formatDateTime(review.assignedAt)}`} variant="outlined" />
                  <Chip label={`Decision: ${statusLabel(review.moderationStatus)}`} variant="outlined" />
                </Stack>
                <Typography variant="body2" color="text.secondary">{nextStepLabel(review.moderationStatus)}</Typography>
                <Divider />
                <Stack spacing={0.75}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Moderation history</Typography>
                  <Typography variant="body2">Submitted at: {formatDateTime(review.submittedAt)}</Typography>
                  <Typography variant="body2">Assigned at: {formatDateTime(review.assignedAt)}</Typography>
                  <Typography variant="body2">Decision at: {formatDateTime(review.decisionAt)}</Typography>
                  {showDecisionReason ? <Typography variant="body2">Decision reason: {review.decisionReason || "Not provided"}</Typography> : null}
                </Stack>
              </Stack>
            </Paper>
          </Stack>

          <Stack spacing={2} sx={{ width: { xs: "100%", lg: 360 }, flexShrink: 0 }}>
            <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3, position: { lg: "sticky" }, top: { lg: 24 } }}>
                <Stack spacing={1.5}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>Current review state</Typography>
                  <Stack spacing={0.5}>
                    <Typography variant="body2" color="text.secondary">Moderation status</Typography>
                    <Typography variant="body1" sx={{ fontWeight: 700 }}>{statusLabel(review.moderationStatus)}</Typography>
                  </Stack>
                  <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary">Publication status</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 700 }}>{statusLabel(review.publicationStatusSnapshot)}</Typography>
                </Stack>
                  <Stack spacing={0.5}>
                    <Typography variant="body2" color="text.secondary">Current next step</Typography>
                    <Typography variant="body2">{nextStepLabel(review.moderationStatus)}</Typography>
                  </Stack>
                  {review.moderationStatus === "UNDER_REVIEW" ? (
                    <Alert severity="info" variant="outlined">
                      Platform review is in progress.
                    </Alert>
                  ) : null}
                  <Divider />
                  <Stack spacing={1}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Provider actions</Typography>
                    <Stack spacing={1}>
                    {uniqueProviderActions.map((action) => {
                      const href = reviewActionHref(action, review);
                      if (!href) {
                        return null;
                      }
                      const isHash = href.startsWith("#");
                      return (
                        isHash ? (
                          <Button
                            key={action}
                            type="button"
                            variant={action === "OPEN_EDITABLE_DRAFT" || action === "VIEW_PUBLIC_PROFILE" ? "contained" : "outlined"}
                            sx={{ justifyContent: "flex-start" }}
                            onClick={() => {
                              const target = document.getElementById(href.slice(1));
                              target?.scrollIntoView({ behavior: "smooth", block: "start" });
                            }}
                          >
                            {reviewActionLabel(action)}
                          </Button>
                        ) : (
                          <Button
                            key={action}
                            component={Link}
                            to={href}
                            variant={action === "OPEN_EDITABLE_DRAFT" || action === "VIEW_PUBLIC_PROFILE" ? "contained" : "outlined"}
                            sx={{ justifyContent: "flex-start" }}
                          >
                            {reviewActionLabel(action)}
                          </Button>
                        )
                      );
                    })}
                  </Stack>
                </Stack>
                <Alert severity="info" variant="outlined">
                  {review.providerAllowedActions.includes("VIEW_PUBLIC_PROFILE") ? "The live public profile is available." : "The submission remains under moderation."}
                </Alert>
              </Stack>
            </Paper>
          </Stack>
        </Stack>
      </Stack>
    </section>
  );
}

export default ProviderPublicProfileReviewPage;
