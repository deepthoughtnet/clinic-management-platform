import * as React from "react";
import { Avatar, Box, Chip, Divider, Paper, Stack, Typography } from "@mui/material";
import { useAuthenticatedImage } from "../../hooks/useAuthenticatedImage";
import type { ProviderPublicProfileReviewFindingResponse, ProviderPublicProfileReviewResponse } from "../../api/clinicApi";
import { providerPublicProfileReviewMediaContentPath } from "../../api/clinicApi";

type ReviewSection = Record<string, unknown>;

const WEEKDAY_ORDER = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;

function reviewSection(review: ProviderPublicProfileReviewResponse, key: string): ReviewSection {
  const value = review.contentSnapshot?.[key];
  return value && typeof value === "object" && !Array.isArray(value) ? (value as ReviewSection) : {};
}

function reviewMediaSection(review: ProviderPublicProfileReviewResponse): ReviewSection {
  const value = review.mediaSnapshot;
  return value && typeof value === "object" && !Array.isArray(value) ? (value as ReviewSection) : {};
}

function reviewText(section: ReviewSection, key: string) {
  const value = section[key];
  if (typeof value === "string") {
    return value.trim();
  }
  if (value == null) {
    return "";
  }
  return String(value).trim();
}

function reviewList(section: ReviewSection, key: string) {
  const value = section[key];
  if (!Array.isArray(value)) {
    return [] as string[];
  }
  return value.map((entry) => String(entry).trim()).filter((entry) => entry.length > 0);
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "—";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? "—"
    : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

function formatProviderType(value: string | null | undefined) {
  switch ((value || "").toUpperCase()) {
    case "CLINIC":
      return "Clinic";
    case "HOSPITAL":
      return "Hospital";
    case "INDIVIDUAL_DOCTOR":
    case "DOCTOR":
      return "Doctor";
    default:
      return "Provider";
  }
}

function cleanTime(value: string | null | undefined) {
  const text = (value || "").trim();
  if (!text) {
    return "";
  }
  const match = text.match(/^([01]?\d|2[0-3]):([0-5]\d)$/);
  return match ? `${match[1].padStart(2, "0")}:${match[2]}` : text;
}

function normalizeDay(value: string | null | undefined) {
  const normalized = (value || "").trim().toUpperCase();
  return WEEKDAY_ORDER.find((day) => day === normalized) ?? null;
}

function minutes(value: string) {
  const match = value.match(/^(\d{2}):(\d{2})$/);
  if (!match) {
    return -1;
  }
  return Number(match[1]) * 60 + Number(match[2]);
}

function normalizeWeeklySchedule(section: ReviewSection) {
  const timezone = reviewText(section, "timezone") || "Asia/Kolkata";
  const intervals: Array<{ dayOfWeek: (typeof WEEKDAY_ORDER)[number]; startTime: string; endTime: string }> = [];
  const source = Array.isArray(section.intervals)
    ? section.intervals
    : Array.isArray(section.weekly)
      ? section.weekly
      : [];

  for (const item of source) {
    if (!item || typeof item !== "object") {
      continue;
    }
    const record = item as Record<string, unknown>;
    const dayOfWeek = normalizeDay(String(record.dayOfWeek ?? record.day ?? ""));
    const startTime = cleanTime(String(record.startTime ?? record.start ?? record.open ?? ""));
    const endTime = cleanTime(String(record.endTime ?? record.end ?? record.close ?? ""));
    if (!dayOfWeek || !startTime || !endTime) {
      continue;
    }
    const startMinutes = minutes(startTime);
    const endMinutes = minutes(endTime);
    if (startMinutes < 0 || endMinutes <= startMinutes) {
      continue;
    }
    if (intervals.some((current) => current.dayOfWeek === dayOfWeek && current.startTime === startTime && current.endTime === endTime)) {
      continue;
    }
    intervals.push({ dayOfWeek, startTime, endTime });
  }

  intervals.sort((left, right) => {
    const dayOrder = WEEKDAY_ORDER.indexOf(left.dayOfWeek) - WEEKDAY_ORDER.indexOf(right.dayOfWeek);
    if (dayOrder !== 0) {
      return dayOrder;
    }
    return minutes(left.startTime) - minutes(right.startTime);
  });

  return { timezone, intervals };
}

function dayLabel(day: string) {
  return day.charAt(0) + day.slice(1).toLowerCase();
}

function formatSessions(entries: Array<{ startTime: string; endTime: string }>) {
  return entries.map((entry) => `${entry.startTime}–${entry.endTime}`).join(", ");
}

function mediaLoadingFallback() {
  return (
    <Box sx={{ minHeight: 220, display: "grid", placeItems: "center", bgcolor: "grey.50", color: "text.secondary", textAlign: "center", px: 2 }}>
      <Typography variant="body2">Loading media...</Typography>
    </Box>
  );
}

function mediaUrl(review: ProviderPublicProfileReviewResponse, mediaReference: string | null | undefined) {
  if (!mediaReference) {
    return null;
  }
  return providerPublicProfileReviewMediaContentPath(review.submissionReference, mediaReference);
}

function reviewMediaReferences(review: ProviderPublicProfileReviewResponse) {
  const media = reviewMediaSection(review);
  const gallery = Array.isArray(media.gallery)
    ? media.gallery.map((entry) => (typeof entry === "string" ? entry.trim() : "")).filter(Boolean)
    : [];
  return {
    logoDocumentId: reviewText(media, "logoDocumentId") || null,
    coverDocumentId: reviewText(media, "coverDocumentId") || null,
    primaryGalleryDocumentId: reviewText(media, "primaryGalleryDocumentId") || null,
    gallery,
    metadataByDocumentId: media.mediaMetadataByDocumentId && typeof media.mediaMetadataByDocumentId === "object" ? (media.mediaMetadataByDocumentId as Record<string, Record<string, unknown>>) : {},
    galleryAltTextByDocumentId: media.galleryAltTextByDocumentId && typeof media.galleryAltTextByDocumentId === "object" ? (media.galleryAltTextByDocumentId as Record<string, string>) : {},
  };
}

function ReviewImage({
  review,
  mediaReference,
  alt,
  className,
  objectFit,
  fallback,
  loadingFallback,
}: {
  review: ProviderPublicProfileReviewResponse;
  mediaReference: string | null | undefined;
  alt: string;
  className: string;
  objectFit: "cover" | "contain";
  fallback: React.ReactNode;
  loadingFallback: React.ReactNode;
}) {
  const { objectUrl, error, loading } = useAuthenticatedImage(mediaUrl(review, mediaReference));
  if (loading && !objectUrl) {
    return <>{loadingFallback}</>;
  }
  if (!objectUrl || error) {
    return <>{fallback}</>;
  }
  return (
    <img
      className={className}
      src={objectUrl}
      alt={alt}
      loading="lazy"
      decoding="async"
      draggable={false}
      data-object-fit={objectFit}
    />
  );
}

function ReviewLogo({
  review,
  mediaReference,
  alt,
  initials,
  loadingFallback,
}: {
  review: ProviderPublicProfileReviewResponse;
  mediaReference: string | null | undefined;
  alt: string;
  initials: string;
  loadingFallback: React.ReactNode;
}) {
  const { objectUrl, error, loading } = useAuthenticatedImage(mediaUrl(review, mediaReference));

  if (loading && !objectUrl) {
    return <>{loadingFallback}</>;
  }

  return (
    <Avatar
      alt={alt}
      src={objectUrl || undefined}
      imgProps={{ referrerPolicy: "no-referrer", style: { objectFit: "contain", width: "100%", height: "100%", display: "block" } }}
      sx={{
        width: "100%",
        height: "100%",
        bgcolor: "background.paper",
        color: "text.primary",
        fontWeight: 900,
        fontSize: 28,
        "& img": {
          objectFit: "contain",
        },
      }}
    >
      {!objectUrl || error ? initials : null}
    </Avatar>
  );
}

function SectionCard({
  title,
  description,
  children,
}: {
  title: string;
  description?: string | null;
  children: React.ReactNode;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack spacing={0.25}>
          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{title}</Typography>
          {description ? <Typography variant="body2" color="text.secondary">{description}</Typography> : null}
        </Stack>
        {children}
      </Stack>
    </Paper>
  );
}

function FindingsSummary({ findings }: { findings: ProviderPublicProfileReviewFindingResponse[] }) {
  const blocking = findings.filter((finding) => (finding.severity || "").toUpperCase() === "BLOCKING" || finding.required);
  return (
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
      <Chip size="small" label={`Open findings: ${findings.filter((finding) => (finding.resolutionStatus || "OPEN").toUpperCase() === "OPEN").length}`} variant="outlined" />
      <Chip size="small" label={`Blocking findings: ${blocking.length}`} color={blocking.length ? "error" : "default"} variant="outlined" />
    </Stack>
  );
}

function reviewerLabel(review: ProviderPublicProfileReviewResponse) {
  return review.assignedReviewerDisplayName
    || review.assignedReviewerReference
    || review.assignedReviewerEmail
    || review.assignedReviewerId
    || "Unassigned";
}

function imageUnavailableFallback(label: string) {
  return (
    <Box sx={{ minHeight: 220, display: "grid", placeItems: "center", bgcolor: "grey.100", color: "text.secondary", textAlign: "center", px: 2 }}>
      <Typography variant="body2">{label}</Typography>
    </Box>
  );
}

function profileInitials(name: string | null | undefined) {
  const source = name?.trim() || "Clinic";
  const normalized = source.replace(/[^a-zA-Z0-9\s]/g, " ").trim();
  const parts = normalized.split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return "CL";
  }
  return parts
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

export function PlatformPublicProfileReviewPreview({ review }: { review: ProviderPublicProfileReviewResponse }) {
  const about = reviewSection(review, "about");
  const contact = reviewSection(review, "contact");
  const services = reviewList(reviewSection(review, "services"), "items");
  const specialities = reviewList(reviewSection(review, "specialities"), "items");
  const facilities = reviewList(reviewSection(review, "facilities"), "items");
  const languages = reviewList(reviewSection(review, "languages"), "items");
  const fees = reviewSection(review, "fees");
  const seo = reviewSection(review, "seo");
  const timings = normalizeWeeklySchedule(reviewSection(review, "timings"));
  const media = reviewMediaReferences(review);
  const reviewDecision = review.decisionReason;
  const groupedTimings = WEEKDAY_ORDER.map((day) => ({
    day,
    sessions: timings.intervals.filter((interval) => interval.dayOfWeek === day).sort((left, right) => minutes(left.startTime) - minutes(right.startTime)),
  }));

  return (
    <Stack spacing={2.5}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Box sx={{ position: "relative", borderRadius: 3, overflow: "hidden", bgcolor: "grey.100" }}>
              <ReviewImage
                review={review}
                mediaReference={media.coverDocumentId}
                alt={`${reviewText(about, "displayName") || review.publicProfileReference} cover`}
                className="platform-review-cover-image"
                objectFit="cover"
                fallback={imageUnavailableFallback("Image unavailable")}
                loadingFallback={mediaLoadingFallback()}
              />
            <Box sx={{ position: "absolute", left: 16, bottom: 16, display: "flex", alignItems: "flex-end", gap: 2 }}>
              <Box sx={{ width: 96, height: 96, overflow: "hidden", bgcolor: "background.paper", border: "1px solid", borderColor: "divider" }}>
                <ReviewLogo
                  review={review}
                  mediaReference={media.logoDocumentId}
                  alt={`${reviewText(about, "displayName") || review.publicProfileReference} logo`}
                  initials={profileInitials(reviewText(about, "displayName") || review.publicProfileReference)}
                  loadingFallback={mediaLoadingFallback()}
                />
              </Box>
              <Stack spacing={0.5} sx={{ color: "common.white", textShadow: "0 1px 2px rgba(0,0,0,0.35)" }}>
                <Typography variant="h4" sx={{ fontWeight: 900 }}>{reviewText(about, "displayName") || review.publicProfileReference}</Typography>
                <Typography variant="body1">{formatProviderType(review.publicProfileType)}</Typography>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Chip size="small" label={`Submitted version ${review.submittedDraftVersion}`} color="primary" />
                  <Chip size="small" label={`Submitted ${formatDateTime(review.submittedAt)}`} variant="outlined" sx={{ bgcolor: "rgba(255,255,255,0.12)", color: "common.white" }} />
                  <Chip size="small" label={`Moderation: ${review.moderationStatus.replaceAll("_", " ")}`} variant="outlined" sx={{ bgcolor: "rgba(255,255,255,0.12)", color: "common.white" }} />
                </Stack>
              </Stack>
            </Box>
          </Box>

          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            <Chip size="small" label={`Publication: ${review.publicationStatusSnapshot.replaceAll("_", " ")}`} variant="outlined" />
            <Chip size="small" label={`Consent: ${review.tenantConsentStatusSnapshot}`} variant="outlined" />
            <Chip size="small" label={`Ownership: ${(review.ownershipSnapshot as Record<string, unknown>).ownershipStatus ? String((review.ownershipSnapshot as Record<string, unknown>).ownershipStatus) : "—"}`} variant="outlined" />
            <Chip size="small" label={`Readiness: ${(review.readinessSnapshot as Record<string, unknown>).completenessPercentage != null ? `${String((review.readinessSnapshot as Record<string, unknown>).completenessPercentage)}% Ready` : "—"}`} color="success" variant="outlined" />
            <Chip size="small" label={`Reviewer: ${reviewerLabel(review)}`} variant="outlined" />
            <Chip size="small" label={`Review started: ${formatDateTime(review.assignedAt)}`} variant="outlined" />
          </Stack>
        </Stack>
      </Paper>

      <SectionCard title="Immutable submission" description="You are reviewing the exact profile version submitted by the Provider. Later draft changes will not affect this submission.">
        <Typography variant="body2" color="text.secondary">
          {reviewDecision ? `Decision reason: ${reviewDecision}` : "No review note recorded."}
        </Typography>
      </SectionCard>

      <SectionCard title="Overview" description="Submitted profile identity and summary.">
        <Stack spacing={1}>
          <Typography variant="body1" sx={{ fontWeight: 700 }}>{reviewText(about, "shortTagline") || reviewText(about, "description") || "Description not provided."}</Typography>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            {reviewList(about, "highlights").map((item) => <Chip key={item} size="small" label={item} variant="outlined" />)}
          </Stack>
        </Stack>
      </SectionCard>

      <Stack direction={{ xs: "column", lg: "row" }} spacing={2} alignItems="flex-start">
        <Stack spacing={2} sx={{ flex: 1, minWidth: 0 }}>
          <SectionCard title="About">
            <Typography variant="body2" sx={{ whiteSpace: "pre-line" }}>{reviewText(about, "description") || "No about text provided."}</Typography>
          </SectionCard>

          <SectionCard title="Services">
            {services.length ? (
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {services.map((item) => <Chip key={item} size="small" label={item} variant="outlined" />)}
              </Stack>
            ) : <Typography variant="body2" color="text.secondary">No services added yet.</Typography>}
          </SectionCard>

          <SectionCard title="Specialities">
            {specialities.length ? (
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {specialities.map((item) => <Chip key={item} size="small" label={item} variant="outlined" />)}
              </Stack>
            ) : <Typography variant="body2" color="text.secondary">No specialities configured.</Typography>}
          </SectionCard>

          <SectionCard title="Facilities">
            {facilities.length ? (
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {facilities.map((item) => <Chip key={item} size="small" label={item} variant="outlined" />)}
              </Stack>
            ) : <Typography variant="body2" color="text.secondary">No facilities configured.</Typography>}
          </SectionCard>

          <SectionCard title="Timings">
            <Stack spacing={1.5}>
              <Typography variant="body2" color="text.secondary">Timezone: {timings.timezone}</Typography>
              <Stack spacing={1}>
                {groupedTimings.map((day) => (
                  <Paper key={day.day} variant="outlined" sx={{ p: 1.25, bgcolor: day.sessions.length ? "background.paper" : "action.hover" }}>
                    <Stack spacing={0.5}>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{dayLabel(day.day)}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {day.sessions.length ? formatSessions(day.sessions) : "Closed"}
                      </Typography>
                    </Stack>
                  </Paper>
                ))}
              </Stack>
            </Stack>
          </SectionCard>

          <SectionCard title="Gallery" description="Submitted media assets.">
            {media.gallery.length ? (
              <Stack direction="row" spacing={1.5} useFlexGap flexWrap="wrap">
                {media.gallery.map((reference, index) => {
                  const metadata = media.metadataByDocumentId[reference] as Record<string, unknown> | undefined;
                  const originalFilename = typeof metadata?.originalFilename === "string" && metadata.originalFilename.trim()
                    ? metadata.originalFilename.trim()
                    : `Gallery image ${index + 1}`;
                  const caption = media.galleryAltTextByDocumentId[reference] || originalFilename;
                  return (
                    <Paper key={reference} variant="outlined" sx={{ width: { xs: "100%", sm: 220 }, overflow: "hidden" }}>
                      <ReviewImage
                        review={review}
                        mediaReference={reference}
                        alt={caption}
                        className="platform-review-gallery-image"
                        objectFit="cover"
                        fallback={imageUnavailableFallback("Image unavailable")}
                        loadingFallback={mediaLoadingFallback()}
                      />
                      <Box sx={{ p: 1.25 }}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{caption}</Typography>
                        <Typography variant="caption" color="text.secondary">{originalFilename}</Typography>
                      </Box>
                    </Paper>
                  );
                })}
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">No gallery images were submitted.</Typography>
            )}
          </SectionCard>
        </Stack>

        <Stack spacing={2} sx={{ flex: 0.9, minWidth: 0 }}>
          <SectionCard title="Contact and address">
            <Stack spacing={1}>
              <Typography variant="body2"><strong>Phone:</strong> {reviewText(contact, "publicPhone") || "—"}</Typography>
              <Typography variant="body2"><strong>Email:</strong> {reviewText(contact, "publicEmail") || "—"}</Typography>
              <Typography variant="body2"><strong>Website:</strong> {reviewText(contact, "website") || "—"}</Typography>
              <Typography variant="body2" sx={{ whiteSpace: "pre-line" }}>
                <strong>Address:</strong>{" "}
                {[
                  reviewText(contact, "addressLine1"),
                  reviewText(contact, "addressLine2"),
                  reviewText(contact, "area"),
                  reviewText(contact, "city"),
                  reviewText(contact, "state"),
                  reviewText(contact, "country"),
                  reviewText(contact, "postalCode"),
                ].filter(Boolean).join(", ") || "—"}
              </Typography>
            </Stack>
          </SectionCard>

          <SectionCard title="Fees">
            <Stack spacing={1}>
              <Typography variant="body2" color="text.secondary">Currency: {reviewText(fees, "currency") || "—"}</Typography>
              {Array.isArray(fees.rows) && fees.rows.length ? (
                <Stack spacing={1}>
                  {(fees.rows as Array<Record<string, unknown>>).map((row) => (
                    <Paper key={String(row.key || row.label)} variant="outlined" sx={{ p: 1.25 }}>
                      <Stack spacing={0.25}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{String(row.label || row.key || "Fee")}</Typography>
                        <Typography variant="body2" color="text.secondary">{String(row.amount || "—")}</Typography>
                      </Stack>
                    </Paper>
                  ))}
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">No fees captured.</Typography>
              )}
            </Stack>
          </SectionCard>

          <SectionCard title="Languages">
            {languages.length ? (
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {languages.map((item) => <Chip key={item} size="small" label={item} variant="outlined" />)}
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">No languages captured.</Typography>
            )}
          </SectionCard>

          <SectionCard title="Reviewer notes">
            <Stack spacing={1}>
              <FindingsSummary findings={review.findings || []} />
              <Divider />
              {review.findings?.length ? (
                review.findings.map((finding) => (
                  <Paper key={finding.findingReference} variant="outlined" sx={{ p: 1.25 }}>
                    <Stack spacing={0.25}>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{finding.section || "General"} · {finding.category || "Finding"}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {finding.severity || "—"}{finding.required ? " · Provider action required" : ""}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">{finding.providerFacingMessage || finding.reviewerNote || "No provider-facing message recorded."}</Typography>
                      {finding.internalNote ? <Typography variant="caption" color="text.secondary">Internal note: {finding.internalNote}</Typography> : null}
                    </Stack>
                  </Paper>
                ))
              ) : (
                <Typography variant="body2" color="text.secondary">No findings recorded.</Typography>
              )}
            </Stack>
          </SectionCard>

          <SectionCard title="Reviewer-only SEO">
            <Stack spacing={1}>
              <Typography variant="body2"><strong>Slug:</strong> {reviewText(seo, "slug") || "—"}</Typography>
              <Typography variant="body2"><strong>Meta title:</strong> {reviewText(seo, "metaTitle") || "—"}</Typography>
              <Typography variant="body2" sx={{ whiteSpace: "pre-line" }}><strong>Meta description:</strong> {reviewText(seo, "metaDescription") || "—"}</Typography>
            </Stack>
          </SectionCard>
        </Stack>
      </Stack>
    </Stack>
  );
}
