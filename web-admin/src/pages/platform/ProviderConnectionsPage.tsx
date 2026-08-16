import * as React from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import AddLinkRoundedIcon from "@mui/icons-material/AddLinkRounded";
import FactCheckRoundedIcon from "@mui/icons-material/FactCheckRounded";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import LinkRoundedIcon from "@mui/icons-material/LinkRounded";
import HistoryRoundedIcon from "@mui/icons-material/HistoryRounded";
import PlayArrowRoundedIcon from "@mui/icons-material/PlayArrowRounded";
import PublishRoundedIcon from "@mui/icons-material/PublishRounded";
import RateReviewRoundedIcon from "@mui/icons-material/RateReviewRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import UndoRoundedIcon from "@mui/icons-material/UndoRounded";
import VisibilityRoundedIcon from "@mui/icons-material/VisibilityRounded";
import { PlatformPublicProfileReviewPreview } from "../../components/platform-review/PlatformPublicProfileReviewPreview";

import { useAuth } from "../../auth/useAuth";
import {
  activateProviderConnectionLink,
  approveProviderConnectionLink,
  approveProviderConnectionOwnership,
  approveProviderConnectionsPublicProfileReview,
  addFindingProviderConnectionsPublicProfileReview,
  getProviderConnectionsLinkDetail,
  getProviderConnectionsAuditEvents,
  getProviderConnectionsOverview,
  getProviderConnectionsPublicProfileReview,
  listProviderConnectionsConflicts,
  listProviderConnectionsLinks,
  listProviderConnectionsOwnerships,
  listProviderConnectionsPlatformEntities,
  listProviderConnectionsPublicPractices,
  listProviderConnectionsPublicProfiles,
  listProviderConnectionsPublicProfileReviews,
  listProviderConnectionsSuggestions,
  proposeProviderConnectionLink,
  publishProviderConnectionsPublicProfileReview,
  rejectProviderConnectionSuggestion,
  rejectProviderConnectionOwnership,
  rejectProviderConnectionsPublicProfileReview,
  disputeProviderConnectionOwnership,
  revokeProviderConnectionOwnership,
  reconcileProviderConnection,
  rejectProviderConnectionLink,
  relinkProviderConnectionLink,
  resumeProviderConnectionLink,
  requestChangesProviderConnectionsPublicProfileReview,
  unlinkProviderConnectionLink,
  suspendProviderConnectionLink,
  startProviderConnectionsPublicProfileReview,
  type ProviderConnectionsConflictResponse,
  type ProviderConnectionsLinkDetailResponse,
  type ProviderConnectionsLinkProposalRequest,
  type ProviderConnectionsLinkResponse,
  type ProviderConnectionsOverviewResponse,
  type ProviderConnectionsPlatformEntityResponse,
  type ProviderConnectionsPublicProfileResponse,
  type ProviderConnectionsPublicProfileType,
  type ProviderConnectionsMatchConfidence,
  type ProviderConnectionsEvidence,
  type ProviderConnectionsSuggestionResponse,
  type ProviderConnectionsMatchMethod,
  type ProviderConnectionsAuditResponse,
  type ProviderConnectionsOwnershipResponse,
  type ProviderPublicProfileReviewQueueResponse,
  type ProviderPublicProfileReviewResponse,
} from "../../api/clinicApi";

type ConsoleSection =
  | "overview"
  | "public-profiles"
  | "public-profile-reviews"
  | "platform-entities"
  | "suggestions"
  | "links"
  | "ownerships"
  | "conflicts"
  | "audit";

type ProposalKind = "CLINIC" | "DOCTOR";

type OwnershipAction =
  | "APPROVE_OWNERSHIP"
  | "REJECT_OWNERSHIP"
  | "DISPUTE_OWNERSHIP"
  | "REVOKE_CLAIM"
  | "REVOKE_OWNERSHIP"
  | "VIEW_OWNERSHIP"
  | "RESOLVE_DISPUTE";

type ProposalDraft = {
  kind: ProposalKind;
  publicProfile: ProviderConnectionsPublicProfileResponse | null;
  suggestion: ProviderConnectionsSuggestionResponse | null;
  publicReference: string;
  publicPracticeReference: string | null;
  sourceSystem: string;
  sourceEntityReference: string;
  sourceRevision: number;
  sourceUpdatedAt: string | null;
  tenantReference: string;
  platformClinicReference: string;
  tenantDoctorUserReference: string;
  tenantDoctorProfileReference: string;
  platformEntityRevision: number;
  platformSelection: string;
  matchMethod: ProviderConnectionsMatchMethod;
  matchConfidence: ProviderConnectionsMatchConfidence;
  reason: string;
  evidence: ProviderConnectionsEvidence[];
};

type ReviewAction =
  | "START_REVIEW"
  | "ADD_REVIEW_FINDING"
  | "REQUEST_CHANGES"
  | "REJECT_SUBMISSION"
  | "APPROVE_SUBMISSION"
  | "PUBLISH_PROFILE"
  | "VIEW_SUBMISSION"
  | "VIEW_PUBLIC_PROFILE"
  | "VIEW_REVIEW_HISTORY";

type ReviewFindingDraft = {
  section: string;
  field: string;
  category: string;
  severity: string;
  required: boolean;
  blocking: boolean;
  providerActionRequired: boolean;
  reviewerNote: string;
  providerFacingMessage: string;
  internalNote: string;
};

function activateOnKeyboard(event: React.KeyboardEvent, onActivate: () => void) {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    onActivate();
  }
}

function TechnicalDetails({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <Box component="details" sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1.5, px: 1.5, py: 1 }}>
      <Box component="summary" sx={{ cursor: "pointer", fontWeight: 700, color: "text.secondary" }}>
        Technical Details
      </Box>
      <Stack spacing={0.5} sx={{ pt: 1 }}>
        {children}
      </Stack>
    </Box>
  );
}

const BUSINESS_LABELS: Record<string, string> = {
  CALL_TO_BOOK: "Call to Book",
  ONLINE_BOOKING: "Online Booking",
  NOT_CONNECTED: "Not Connected",
  NOT_LINKED: "Not Linked",
  CONNECTION_PENDING: "Connection Pending",
  PENDING_VERIFICATION: "Pending Verification",
  VERIFIED: "Verified",
  APPROVED: "Approved",
  PUBLISHED: "Published",
  UNDER_REVIEW: "Under Review",
  CHANGES_REQUESTED: "Changes Requested",
  REJECTED: "Rejected",
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  ENABLED: "Enabled",
  DISABLED: "Disabled",
  READY: "Ready",
  UNKNOWN: "Unknown",
  UNCLAIMED: "Unclaimed",
  LINKED: "Linked",
  PROPOSED: "Proposed",
  SUSPENDED: "Suspended",
  DISCONNECTED: "Disconnected",
  AVAILABLE: "Available",
  AVAILABLE_TODAY: "Available Today",
  NEXT_AVAILABLE: "Next Available",
  HEALTHCARE_INITIATED_CONNECTION: "Healthcare Initiated",
  "OWNER:ACTIVE": "Owner Active",
  REGISTRATION_EXACT: "Registration Exact",
  VERIFIED_PHONE_EXACT: "Verified Phone Exact",
  VERIFIED_EMAIL_EXACT: "Verified Email Exact",
  VERIFIED_CONTACT_EXACT: "Verified Contact Exact",
  BUSINESS_IDENTITY_MATCH: "Business Identity Match",
  REGISTRATION_AND_CONTACT: "Registration And Contact",
  MANUAL_REFERENCE: "Manual Reference",
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
};

function businessLabel(value: string | null | undefined) {
  if (!value) return "—";
  const normalized = value.trim().toUpperCase();
  if (BUSINESS_LABELS[normalized]) return BUSINESS_LABELS[normalized];
  return value
    .replaceAll("_", " ")
    .replaceAll(":", " ")
    .toLowerCase()
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function fieldHelp(title: string, description: string) {
  return (
    <Stack direction="row" spacing={0.5} alignItems="center">
      <Typography variant="caption" color="text.secondary">{title}</Typography>
      <Tooltip title={description} arrow>
        <InfoOutlinedIcon sx={{ fontSize: 14, color: "text.secondary", cursor: "help" }} />
      </Tooltip>
    </Stack>
  );
}

function EmptyState({
  title,
  description,
  actionLabel,
  onAction,
}: {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 3, textAlign: "center" }}>
      <Stack spacing={1.25} alignItems="center">
        <HistoryRoundedIcon color="action" />
        <Typography variant="h6" sx={{ fontWeight: 900 }}>{title}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 640 }}>{description}</Typography>
        {actionLabel && onAction ? (
          <Button variant="outlined" onClick={onAction}>{actionLabel}</Button>
        ) : null}
      </Stack>
    </Paper>
  );
}

type PublicProfileInspectionPanelProps = {
  loading: boolean;
  error: string | null;
  selectedProfile: ProviderConnectionsPublicProfileResponse | null;
  selectedOwnership: ProviderConnectionsOwnershipResponse | null;
  selectedLink: ProviderConnectionsLinkResponse | null;
  selectedPlatformClinic: ProviderConnectionsPlatformEntityResponse | null;
  onProposeLink: (profile: ProviderConnectionsPublicProfileResponse) => void;
};

function PublicProfileInspectionPanel({
  loading,
  error,
  selectedProfile,
  selectedOwnership,
  selectedLink,
  selectedPlatformClinic,
  onProposeLink,
}: PublicProfileInspectionPanelProps) {
  if (!selectedProfile) {
    if (loading) {
      return (
        <Alert severity="info" variant="outlined">
          Loading public profile details…
        </Alert>
      );
    }
    if (error) {
      return (
        <Alert severity="error" variant="outlined">
          Public profile details could not be loaded.
        </Alert>
      );
    }
    return (
      <Alert severity="info" variant="outlined">
        Pick a row from Public Profiles to inspect a specific profile.
      </Alert>
    );
  }

  return (
    <Stack spacing={1.25}>
      {error ? (
        <Alert severity="error" variant="outlined">
          Public profile details could not be refreshed. Showing the last known selected profile.
        </Alert>
      ) : null}
      {loading ? (
        <Alert severity="info" variant="outlined">
          Refreshing public profile details…
        </Alert>
      ) : null}
      <Chip
        size="small"
        label={`${formatProviderType(selectedProfile.publicProfileType)} · ${businessLabel(selectedProfile.publicationStatus)}`}
        color={actionChipColor(selectedProfile.publicationStatus)}
        variant="outlined"
        sx={{ alignSelf: "flex-start" }}
      />
      <Typography sx={{ fontWeight: 800 }}>
        {selectedProfile.displayName || "Selected public profile"}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {selectedProfile.city || "—"}{selectedProfile.area ? ` · ${selectedProfile.area}` : ""}
      </Typography>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Public URL</Typography>
        <Typography variant="body2">{selectedProfile.publicPath || "No public path"}</Typography>
      </Stack>
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        <Chip size="small" label={`Connection: ${businessLabel(selectedProfile.connectionStatus || "NOT_CONNECTED")}`} variant="outlined" />
        <Chip size="small" label={`Capability: ${businessLabel(selectedProfile.bookingCapability)}`} variant="outlined" />
        {selectedOwnership ? (
          <Chip size="small" label={`Ownership: ${businessLabel(selectedOwnership.ownershipStatus)}`} variant="outlined" />
        ) : null}
      </Stack>
      {selectedOwnership ? (
        <Stack spacing={0.5}>
          {fieldHelp("Ownership status", "Shows the verified ownership lifecycle state for this public profile.")}
          <Typography variant="body2">{businessLabel(selectedOwnership.ownershipStatus)}</Typography>
          {fieldHelp("Connection", "Represents the lifecycle relationship between a published public profile and an operational clinic.")}
          <Typography variant="body2">{businessLabel(selectedOwnership.platformConnectionStatus || selectedProfile.connectionStatus || "NOT_CONNECTED")}</Typography>
          {fieldHelp("Capability", "Shows the public booking capability surfaced to patients.")}
          <Typography variant="body2">{businessLabel(selectedOwnership.bookingCapability || selectedProfile.bookingCapability)}</Typography>
        </Stack>
      ) : null}
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Current platform link</Typography>
        {selectedLink ? (
          <>
            <Typography variant="body2">
              {selectedPlatformClinic?.displayName || selectedPlatformClinic?.tenantName || selectedLink.tenantName || "Linked operational clinic"}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Lifecycle {businessLabel(selectedLink.linkStatus)} · {businessLabel(selectedLink.connectionStatus)}
            </Typography>
          </>
        ) : (
          <Typography variant="body2" color="text.secondary">
            No active platform link is associated with this public profile.
          </Typography>
        )}
      </Stack>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Effective capability</Typography>
        <Typography variant="body2">{businessLabel(selectedProfile.bookingCapability)}</Typography>
      </Stack>
      {selectedProfile.allowedActions.includes("PROPOSE_LINK") ? (
        <Button
          variant="contained"
          startIcon={<LinkRoundedIcon />}
          onClick={() => onProposeLink(selectedProfile)}
        >
          Propose link
        </Button>
      ) : null}
      <TechnicalDetails>
        <Typography variant="body2" color="text.secondary">Business Reference</Typography>
        <Typography variant="body2">{selectedProfile.publicReference || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Public Reference</Typography>
        <Typography variant="body2">{selectedProfile.publicReference || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Public Practice Reference</Typography>
        <Typography variant="body2">{selectedProfile.publicPracticeReference || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Revision</Typography>
        <Typography variant="body2">{selectedProfile.sourceRevision ?? "—"}</Typography>
      </TechnicalDetails>
    </Stack>
  );
}

const SECTIONS: Array<{ key: ConsoleSection; label: string; path: string }> = [
  { key: "overview", label: "Overview", path: "/platform/provider-connections" },
  { key: "public-profiles", label: "Public Profiles", path: "/platform/provider-connections/public-profiles" },
  { key: "public-profile-reviews", label: "Public Profile Reviews", path: "/platform/provider-connections/public-profile-reviews" },
  { key: "platform-entities", label: "Platform Entities", path: "/platform/provider-connections/platform-entities" },
  { key: "suggestions", label: "Suggestions", path: "/platform/provider-connections/suggestions" },
  { key: "links", label: "Links", path: "/platform/provider-connections/links" },
  { key: "ownerships", label: "Ownerships", path: "/platform/provider-connections/ownerships" },
  { key: "conflicts", label: "Conflicts", path: "/platform/provider-connections/conflicts" },
  { key: "audit", label: "Audit", path: "/platform/provider-connections/audit" },
];

const MATCH_METHODS: ProviderConnectionsMatchMethod[] = [
  "REGISTRATION_EXACT",
  "VERIFIED_PHONE_EXACT",
  "VERIFIED_EMAIL_EXACT",
  "PROVIDER_CONFIRMED",
  "TENANT_CONFIRMED",
  "CLINIC_OWNERSHIP_CONFIRMED",
  "PLATFORM_ADMIN_REVIEWED",
  "MANUAL_REFERENCE",
];

function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

function formatProviderType(type: ProviderConnectionsPublicProfileType | string | null | undefined) {
  switch (type) {
    case "DOCTOR":
      return "Doctor";
    case "CLINIC":
      return "Clinic";
    case "HOSPITAL":
      return "Hospital";
    default:
      return "Provider";
  }
}

type PlatformEntityInspectorProps = {
  loading: boolean;
  error: string | null;
  selectedEntity: ProviderConnectionsPlatformEntityResponse | null;
  linkedProfile: ProviderConnectionsPublicProfileResponse | null;
  linkedPlatformLink: ProviderConnectionsLinkResponse | null;
  onReviewMatch: (entity: ProviderConnectionsPlatformEntityResponse) => void;
};

function PlatformEntityInspector({
  loading,
  error,
  selectedEntity,
  linkedProfile,
  linkedPlatformLink,
  onReviewMatch,
}: PlatformEntityInspectorProps) {
  if (!selectedEntity) {
    return (
      <Alert severity={loading ? "info" : error ? "error" : "info"} variant="outlined">
        {loading
          ? "Loading platform entity details…"
          : error
            ? "Platform entity details could not be loaded."
            : "Pick a platform entity to inspect its operational, publication and connection state."}
      </Alert>
    );
  }

  return (
    <Stack spacing={1.25}>
      {error ? <Alert severity="error" variant="outlined">Platform entity details could not be refreshed. Showing the selected entity.</Alert> : null}
      {loading ? <Alert severity="info" variant="outlined">Refreshing platform entity details…</Alert> : null}
      <Chip size="small" label={`${formatProviderType(selectedEntity.entityType)} · ${businessLabel(selectedEntity.connectionStatus || "NOT_CONNECTED")}`} variant="outlined" sx={{ alignSelf: "flex-start" }} />
      <Typography sx={{ fontWeight: 800 }}>{selectedEntity.displayName || "Selected platform entity"}</Typography>
      <Typography variant="body2" color="text.secondary">
        {selectedEntity.tenantName || "—"}{selectedEntity.tenantCode ? ` · ${selectedEntity.tenantCode}` : ""}
      </Typography>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Context</Typography>
        <Typography variant="body2">{selectedEntity.city || "—"}{selectedEntity.area ? ` · ${selectedEntity.area}` : ""}</Typography>
        <Typography variant="caption" color="text.secondary">{selectedEntity.active ? "Active" : "Inactive"} · {selectedEntity.publicListingEnabled ? "Public listing enabled" : "Public listing disabled"}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Connection", "Represents the current link state between the operational clinic and the public profile.")}
        <Typography variant="body2">{businessLabel(selectedEntity.linkStatus || "NOT_LINKED")} · {businessLabel(selectedEntity.connectionStatus || "NOT_CONNECTED")}</Typography>
        <Typography variant="caption" color="text.secondary">{linkedProfile?.displayName || selectedEntity.linkedPublicReference || "No linked public profile"}</Typography>
        <Typography variant="caption" color="text.secondary">{businessLabel(linkedPlatformLink?.bookingCapability || selectedEntity.currentDiscoverCapability || selectedEntity.bookingCapability || "CALL_TO_BOOK")}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Capability", "Shows the effective booking capability that patients can use.")}
        <Typography variant="body2">{businessLabel(selectedEntity.currentDiscoverCapability || selectedEntity.bookingCapability || "CALL_TO_BOOK")}</Typography>
        <Typography variant="caption" color="text.secondary">{selectedEntity.platformBookingSetup || "—"}</Typography>
        {selectedEntity.capabilityReason ? <Typography variant="caption" color="text.secondary">{selectedEntity.capabilityReason}</Typography> : null}
      </Stack>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Availability</Typography>
        <Typography variant="body2">{businessLabel(selectedEntity.currentAvailability || "UNKNOWN")}</Typography>
        <Typography variant="caption" color="text.secondary">Revision {selectedEntity.sourceRevision}</Typography>
        {selectedEntity.sourceUpdatedAt ? <Typography variant="caption" color="text.secondary">{formatDateTime(selectedEntity.sourceUpdatedAt)}</Typography> : null}
      </Stack>
      {selectedEntity.linkStatus === "LINKED" ? (
        <Stack spacing={0.5}>
          <Typography variant="caption" color="text.secondary">Linked public profile</Typography>
          <Typography variant="body2">{linkedProfile?.displayName || linkedEntityLabel(selectedEntity)}</Typography>
        </Stack>
      ) : null}
      <Button size="small" variant="contained" onClick={() => onReviewMatch(selectedEntity)}>
        Review match
      </Button>
      <TechnicalDetails>
        <Typography variant="body2" color="text.secondary">Tenant Reference</Typography>
        <Typography variant="body2">{selectedEntity.tenantReference || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Platform Clinic Reference</Typography>
        <Typography variant="body2">{selectedEntity.platformClinicReference || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Source Revision</Typography>
        <Typography variant="body2">{selectedEntity.sourceRevision}</Typography>
      </TechnicalDetails>
    </Stack>
  );
}

type SuggestionInspectorProps = {
  loading: boolean;
  error: string | null;
  selectedSuggestion: ProviderConnectionsSuggestionResponse | null;
  onReviewMatch: (suggestion: ProviderConnectionsSuggestionResponse) => void;
  onReject: (suggestion: ProviderConnectionsSuggestionResponse) => void;
};

function SuggestionInspector({ loading, error, selectedSuggestion, onReviewMatch, onReject }: SuggestionInspectorProps) {
  if (!selectedSuggestion) {
    return (
      <Alert severity={loading ? "info" : error ? "error" : "info"} variant="outlined">
        {loading
          ? "Loading suggestion details…"
          : error
            ? "Suggestion details could not be loaded."
            : "Select a suggested match to review the candidate, matching evidence and available actions."}
      </Alert>
    );
  }

  return (
    <Stack spacing={1.25}>
      {error ? <Alert severity="error" variant="outlined">Suggestion details could not be refreshed. Showing the selected suggestion.</Alert> : null}
      {loading ? <Alert severity="info" variant="outlined">Refreshing suggestion details…</Alert> : null}
      <Chip size="small" label={`${formatProviderType(selectedSuggestion.publicProfileType)} · ${businessLabel(selectedSuggestion.status || "SUGGESTED")}`} variant="outlined" sx={{ alignSelf: "flex-start" }} />
      <Typography sx={{ fontWeight: 800 }}>{selectedSuggestion.publicDisplayName || "Suggested provider"}</Typography>
      <Typography variant="body2" color="text.secondary">{selectedSuggestion.platformDisplayName || "Suggested platform entity"}</Typography>
      <Stack spacing={0.5}>
        {fieldHelp("Profile and platform", "Shows the public profile and the suggested operational clinic side by side.")}
        <Typography variant="body2">{selectedSuggestion.publicReference || "—"} · {selectedSuggestion.publicPracticeReference || "—"}</Typography>
        <Typography variant="body2">{selectedSuggestion.tenantReference || "—"} · {selectedSuggestion.platformClinicReference || "—"}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Comparison", "Shows the candidate fields used for matching.")}
        <Typography variant="body2">{selectedSuggestion.platformCity || "—"} · {selectedSuggestion.platformArea || "—"}</Typography>
        <Typography variant="body2">{selectedSuggestion.platformPhone || "—"} · {selectedSuggestion.platformEmail || "—"}</Typography>
        <Typography variant="body2">{selectedSuggestion.platformRegistrationNumber || "—"}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Match", "Shows the backend-derived match method and confidence.")}
        <Typography variant="body2">{businessLabel(selectedSuggestion.matchMethod)} · {businessLabel(selectedSuggestion.confidence)}</Typography>
        <Typography variant="caption" color="text.secondary">{selectedSuggestion.lastEvaluatedAt ? formatDateTime(selectedSuggestion.lastEvaluatedAt) : "No evaluation timestamp"}</Typography>
        <Typography variant="caption" color="text.secondary">Source revision {selectedSuggestion.sourceRevision}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Review note</Typography>
        <Typography variant="body2">{selectedSuggestion.reason || "—"}</Typography>
      </Stack>
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        {(selectedSuggestion.evidence || []).map((item) => <Chip key={evidenceSummary(item)} size="small" label={evidenceSummary(item)} color={evidenceTone(item.strength)} variant="outlined" />)}
      </Stack>
      <Stack direction="row" spacing={1}>
        {(selectedSuggestion.allowedActions || []).includes("PROPOSE_LINK") ? (
          <Button variant="contained" onClick={() => onReviewMatch(selectedSuggestion)}>Review match</Button>
        ) : null}
        {(selectedSuggestion.allowedActions || []).includes("REJECT_SUGGESTION") ? (
          <Button variant="outlined" onClick={() => onReject(selectedSuggestion)}>Reject suggestion</Button>
        ) : null}
      </Stack>
      <TechnicalDetails>
        <Typography variant="body2" color="text.secondary">Suggestion Id</Typography>
        <Typography variant="body2">{selectedSuggestion.id}</Typography>
        <Typography variant="body2" color="text.secondary">Public Reference</Typography>
        <Typography variant="body2">{selectedSuggestion.publicReference || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Platform Clinic Reference</Typography>
        <Typography variant="body2">{selectedSuggestion.platformClinicReference || "—"}</Typography>
      </TechnicalDetails>
    </Stack>
  );
}

type LinkInspectorProps = {
  loading: boolean;
  error: string | null;
  selectedLink: ProviderConnectionsLinkResponse | null;
  selectedLinkDetail: ProviderConnectionsLinkDetailResponse | null;
  onRetry: () => void;
  allowedActions: React.ReactNode;
};

function LinkInspector({ loading, error, selectedLink, selectedLinkDetail, onRetry, allowedActions }: LinkInspectorProps) {
  if (!selectedLink) {
    return (
      <Alert severity={loading ? "info" : error ? "error" : "info"} variant="outlined">
        {loading
          ? "Loading link details…"
          : error
            ? "Link details could not be loaded."
            : "Pick a link to inspect its lifecycle, capability, comparison evidence and available actions."}
      </Alert>
    );
  }

  return (
    <Stack spacing={1.25}>
      {error ? (
        <Alert severity="error" variant="outlined">
          Unable to load link details. <Button size="small" onClick={onRetry}>Refresh Details</Button>
        </Alert>
      ) : null}
      {loading ? <Alert severity="info" variant="outlined">Refreshing link details…</Alert> : null}
      <Chip size="small" label={`${formatProviderType(selectedLink.publicProfileType)} · ${businessLabel(selectedLink.linkStatus)}`} color={actionChipColor(selectedLink.linkStatus)} variant="outlined" sx={{ alignSelf: "flex-start" }} />
      <Typography sx={{ fontWeight: 800 }}>{selectedLink.publicDisplayName || selectedLink.publicReference || "Selected link"}</Typography>
      <Typography variant="body2" color="text.secondary">{selectedLink.tenantName || "—"}</Typography>
      <Stack spacing={0.5}>
        {fieldHelp("Public profile", "Shows the business-facing profile linked to this operational clinic.")}
        <Typography variant="body2">{selectedLink.publicDisplayName || selectedLink.publicPath || "—"}</Typography>
        <Typography variant="caption" color="text.secondary">{selectedLink.publicPath || "No public path"}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Connection", "Represents the lifecycle relationship between a published public profile and an operational clinic.")}
        <Typography variant="body2">{businessLabel(selectedLink.connectionStatus)}</Typography>
        <Typography variant="caption" color="text.secondary">Lifecycle {businessLabel(selectedLink.linkStatus)}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Capability", "Shows the patient-facing booking behavior currently in effect.")}
        <Typography variant="body2">{businessLabel(selectedLink.bookingCapability)}</Typography>
        <Typography variant="caption" color="text.secondary">Availability {businessLabel(selectedLink.availabilityState)}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Lifecycle", "Shows the revision and transition history currently used for this link.")}
        <Typography variant="body2">{selectedLink.sourceRevision}</Typography>
        <Typography variant="caption" color="text.secondary">Connection revision {selectedLink.connectionRevision}</Typography>
        <Typography variant="caption" color="text.secondary">{formatDateTime(selectedLink.updatedAt)}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Match", "Shows the matching method and confidence approved for this link.")}
        <Typography variant="body2">{businessLabel(selectedLink.matchMethod)}</Typography>
        <Typography variant="caption" color="text.secondary">{businessLabel(selectedLink.matchConfidence)}</Typography>
      </Stack>
      <Stack spacing={0.75}>
        {fieldHelp("Lifecycle timeline", "Ordered timeline of link lifecycle milestones.")}
        {[
          { label: "Proposed", value: selectedLink.proposedAt },
          { label: "Verified", value: selectedLink.verifiedAt },
          { label: "Activated", value: selectedLink.activatedAt },
        ].map((step, index, items) => (
          <Stack key={step.label} direction="row" spacing={1} alignItems="flex-start">
            <Box sx={{ width: 16, display: "flex", justifyContent: "center", mt: 0.4 }}>
              <Box sx={{ width: 8, height: 8, borderRadius: "50%", bgcolor: "primary.main" }} />
            </Box>
            <Stack spacing={0.2} sx={{ flex: 1, pb: index < items.length - 1 ? 1 : 0, borderLeft: index < items.length - 1 ? "2px solid" : "none", borderColor: "divider", pl: 1.5, ml: "-8px" }}>
              <Typography variant="body2" sx={{ fontWeight: 700 }}>{step.label}</Typography>
              <Typography variant="caption" color="text.secondary">{formatDateTime(step.value)}</Typography>
            </Stack>
          </Stack>
        ))}
      </Stack>
      {selectedLinkDetail?.comparison?.length ? (
        <Stack spacing={1}>
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Comparison</Typography>
          {selectedLinkDetail.comparison.map((row) => (
            <Box key={row.key} sx={{ p: 1.25, border: "1px solid", borderColor: "divider", borderRadius: 1.5 }}>
              <Typography variant="caption" color="text.secondary">{row.label}</Typography>
              <Typography variant="body2">{row.publicValue || "—"} → {row.platformValue || "—"}</Typography>
              <Typography variant="caption" color="text.secondary">{row.status}</Typography>
            </Box>
          ))}
        </Stack>
      ) : null}
      {selectedLinkDetail?.audit?.length ? (
        <Stack spacing={1}>
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Audit</Typography>
          {selectedLinkDetail.audit.slice(0, 4).map((row) => (
            <Paper key={row.id} variant="outlined" sx={{ p: 1.25 }}>
              <Typography variant="body2" sx={{ fontWeight: 700 }}>{businessLabel(row.action)}</Typography>
              <Typography variant="caption" color="text.secondary">{formatDateTime(row.occurredAt)}</Typography>
            </Paper>
          ))}
        </Stack>
      ) : null}
      {allowedActions}
      {error ? <Button size="small" variant="outlined" onClick={onRetry}>Refresh Details</Button> : null}
      <TechnicalDetails>
        <Typography variant="body2" color="text.secondary">Business Reference</Typography>
        <Typography variant="body2">{selectedLink.publicReference || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Internal Link Id</Typography>
        <Typography variant="body2">{selectedLink.id}</Typography>
        <Typography variant="body2" color="text.secondary">Connection revision</Typography>
        <Typography variant="body2">{selectedLink.connectionRevision}</Typography>
      </TechnicalDetails>
    </Stack>
  );
}

type OwnershipInspectorProps = {
  selectedOwnership: ProviderConnectionsOwnershipResponse | null;
};

function OwnershipInspector({ selectedOwnership }: OwnershipInspectorProps) {
  if (!selectedOwnership) {
    return (
      <Alert severity="info" variant="outlined">
        Pick an ownership row to inspect its complete ownership view.
      </Alert>
    );
  }

  return (
    <Stack spacing={1.25}>
      <Alert severity="info" variant="outlined">Ownership actions are controlled by the backend `allowedActions` list for this row.</Alert>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Operational Clinic</Typography>
        <Typography sx={{ fontWeight: 800 }}>{selectedOwnership.displayName || "Unnamed profile"}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Owning Tenant</Typography>
        <Typography variant="body2">See Technical Details</Typography>
      </Stack>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">Location</Typography>
        <Typography variant="body2" color="text.secondary">{selectedOwnership.city || "—"}{selectedOwnership.area ? ` · ${selectedOwnership.area}` : ""}</Typography>
      </Stack>
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        <Chip size="small" label={businessLabel(selectedOwnership.ownershipStatus || "UNCLAIMED")} color={actionChipColor(selectedOwnership.ownershipStatus)} variant="outlined" />
        <Chip size="small" label={businessLabel(selectedOwnership.platformConnectionStatus || "NOT_CONNECTED")} color={actionChipColor(selectedOwnership.platformConnectionStatus)} variant="outlined" />
        <Chip size="small" label={businessLabel(selectedOwnership.bookingCapability || "NOT_AVAILABLE")} color={actionChipColor(selectedOwnership.bookingCapability)} variant="outlined" />
      </Stack>
      <Typography variant="body2" color="text.secondary">{businessLabel(selectedOwnership.ownershipMethod)} · {businessLabel(selectedOwnership.consentState)}</Typography>
      <Typography variant="body2" color="text.secondary">Ownership note: {selectedOwnership.reason || "—"}</Typography>
      {fieldHelp("Membership", "Shows the membership context that supports the verified ownership relationship.")}
      <Typography variant="body2">{(selectedOwnership.membershipRoles || []).join(", ") || "—"}</Typography>
      {fieldHelp("Disputes", "Shows any active or historical dispute states associated with this ownership record.")}
      <Typography variant="body2">{(selectedOwnership.disputeStatuses || []).join(", ") || "—"}</Typography>
      {fieldHelp("Verified on", "Shows when ownership verification was completed.")}
      <Typography variant="body2">{formatDateTime(selectedOwnership.verifiedAt)}</Typography>
      {fieldHelp("Updated", "Shows the most recent ownership record update.")}
      <Typography variant="body2">{formatDateTime(selectedOwnership.updatedAt)}</Typography>
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        {(selectedOwnership.allowedActions || []).map((action) => (
          <Chip key={`selected-ownership-${selectedOwnership.ownershipId}-${action}`} size="small" label={ownershipActionLabel(action as OwnershipAction)} variant="outlined" />
        ))}
      </Stack>
      <TechnicalDetails>
        <Typography variant="body2" color="text.secondary">Ownership Id</Typography>
        <Typography variant="body2">{selectedOwnership.ownershipId}</Typography>
        <Typography variant="body2" color="text.secondary">Public Reference</Typography>
        <Typography variant="body2">{selectedOwnership.publicProfileReference || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Membership Roles</Typography>
        <Typography variant="body2">{(selectedOwnership.membershipRoles || []).join(", ") || "—"}</Typography>
      </TechnicalDetails>
    </Stack>
  );
}

type AuditEventInspectorProps = {
  loading: boolean;
  error: string | null;
  selectedAuditEvent: ProviderConnectionsAuditResponse | null;
};

function AuditEventInspector({ loading, error, selectedAuditEvent }: AuditEventInspectorProps) {
  if (!selectedAuditEvent) {
    return (
      <Alert severity={loading ? "info" : error ? "error" : "info"} variant="outlined">
        {loading
          ? "Loading audit event details…"
          : error
            ? "Audit event details could not be loaded."
            : "Select an audit event to inspect the lifecycle transition, actor and request context."}
      </Alert>
    );
  }

  return (
    <Stack spacing={1.25}>
      {error ? <Alert severity="error" variant="outlined">Audit event details could not be refreshed. Showing the selected event.</Alert> : null}
      {loading ? <Alert severity="info" variant="outlined">Refreshing audit event details…</Alert> : null}
      <Chip size="small" label={businessLabel(selectedAuditEvent.action)} variant="outlined" sx={{ alignSelf: "flex-start" }} />
      <Typography sx={{ fontWeight: 800 }}>{selectedAuditEvent.providerType || "Audit event"}</Typography>
      <Typography variant="body2" color="text.secondary">{selectedAuditEvent.tenantReference || "—"} · {selectedAuditEvent.platformClinicReference || "—"}</Typography>
      <Typography variant="body2" color="text.secondary">{selectedAuditEvent.summary || "Audit event"}</Typography>
      <Stack spacing={0.5}>
        {fieldHelp("Lifecycle", "Shows the previous and new lifecycle state for this audit event.")}
        <Typography variant="body2">{selectedAuditEvent.previousState || "—"} → {selectedAuditEvent.newState || "—"}</Typography>
        <Typography variant="caption" color="text.secondary">{businessLabel(selectedAuditEvent.result)}</Typography>
      </Stack>
      <Stack spacing={0.5}>
        {fieldHelp("Actor / context", "Shows the actor and correlation context returned by the backend.")}
        <Typography variant="body2">{selectedAuditEvent.actorAppUserId || "—"}</Typography>
        <Typography variant="caption" color="text.secondary">{selectedAuditEvent.correlationId || "—"}</Typography>
      </Stack>
      <Typography variant="caption" color="text.secondary">{formatDateTime(selectedAuditEvent.occurredAt)}</Typography>
      <TechnicalDetails>
        <Typography variant="body2" color="text.secondary">Audit Event Id</Typography>
        <Typography variant="body2">{selectedAuditEvent.id}</Typography>
        <Typography variant="body2" color="text.secondary">Correlation Id</Typography>
        <Typography variant="body2">{selectedAuditEvent.correlationId || "—"}</Typography>
        <Typography variant="body2" color="text.secondary">Details</Typography>
        <Typography variant="body2">{selectedAuditEvent.detailsJson || "—"}</Typography>
      </TechnicalDetails>
    </Stack>
  );
}

type ConflictInspectorProps = {
  selectedConflict: ProviderConnectionsConflictResponse | null;
};

function ConflictInspector({ selectedConflict }: ConflictInspectorProps) {
  if (!selectedConflict) {
    return (
      <Alert severity="info" variant="outlined">
        Select a conflict to inspect the specific mismatch or blockage.
      </Alert>
    );
  }

  return (
    <Stack spacing={1.25}>
      <Chip size="small" label={selectedConflict.severity} color="error" variant="outlined" sx={{ alignSelf: "flex-start" }} />
      <Typography sx={{ fontWeight: 800 }}>{selectedConflict.title}</Typography>
      <Typography variant="body2" color="text.secondary">{selectedConflict.details}</Typography>
    </Stack>
  );
}

function linkedEntityLabel(entity: ProviderConnectionsPlatformEntityResponse) {
  return entity.linkedPublicReference || entity.displayName || "Linked profile";
}

function formatModerationStatus(status: string | null | undefined) {
  switch ((status || "").toUpperCase()) {
    case "DRAFT":
    case "NOT_SUBMITTED":
      return "Draft";
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
    case "WITHDRAWN":
      return "Withdrawn";
    default:
      return status ? status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (value) => value.toUpperCase()) : "—";
  }
}

function formatVisibilityStatus(status: string | null | undefined) {
  switch ((status || "").toUpperCase()) {
    case "VISIBLE":
      return "Visible";
    case "NOT_PUBLISHED":
      return "Not published";
    case "HIDDEN_BY_TENANT_CONSENT":
      return "Hidden by tenant consent";
    case "HIDDEN_BY_OWNERSHIP":
      return "Hidden by ownership";
    case "HIDDEN_BY_PLATFORM":
      return "Hidden by platform";
    case "INVALID_PROJECTION":
      return "Invalid projection";
    default:
      return status ? status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (value) => value.toUpperCase()) : "—";
  }
}

function formatReviewAction(action: ReviewAction) {
  switch (action) {
    case "START_REVIEW":
      return "Start review";
    case "ADD_REVIEW_FINDING":
      return "Add finding";
    case "REQUEST_CHANGES":
      return "Request changes";
    case "REJECT_SUBMISSION":
      return "Reject submission";
    case "APPROVE_SUBMISSION":
      return "Approve submission";
    case "PUBLISH_PROFILE":
      return "Publish profile";
    case "VIEW_SUBMISSION":
      return "View submission";
    case "VIEW_PUBLIC_PROFILE":
      return "View public profile";
    case "VIEW_REVIEW_HISTORY":
      return "View review history";
  }
}

function isReviewAction(value: string): value is ReviewAction {
  return [
    "START_REVIEW",
    "ADD_REVIEW_FINDING",
    "REQUEST_CHANGES",
    "REJECT_SUBMISSION",
    "APPROVE_SUBMISSION",
    "PUBLISH_PROFILE",
    "VIEW_SUBMISSION",
    "VIEW_PUBLIC_PROFILE",
    "VIEW_REVIEW_HISTORY",
  ].includes(value);
}

function isReviewModerationAction(value: string): value is ReviewAction {
  return [
    "START_REVIEW",
    "ADD_REVIEW_FINDING",
    "REQUEST_CHANGES",
    "REJECT_SUBMISSION",
    "APPROVE_SUBMISSION",
    "PUBLISH_PROFILE",
    "VIEW_SUBMISSION",
  ].includes(value);
}

function reviewActionPermission(action: ReviewAction) {
  switch (action) {
    case "START_REVIEW":
    case "ADD_REVIEW_FINDING":
    case "REQUEST_CHANGES":
    case "APPROVE_SUBMISSION":
    case "PUBLISH_PROFILE":
      return "platform.provider_connection.approve";
    case "REJECT_SUBMISSION":
      return "platform.provider_connection.reject";
    case "VIEW_SUBMISSION":
    case "VIEW_PUBLIC_PROFILE":
    case "VIEW_REVIEW_HISTORY":
      return "platform.provider_connection.view";
  }
}

function reviewActionVariant(action: ReviewAction) {
  switch (action) {
    case "APPROVE_SUBMISSION":
    case "PUBLISH_PROFILE":
      return "contained" as const;
    case "REJECT_SUBMISSION":
      return "outlined" as const;
    case "REQUEST_CHANGES":
      return "outlined" as const;
    case "START_REVIEW":
    case "ADD_REVIEW_FINDING":
      return "contained" as const;
    default:
      return "outlined" as const;
  }
}

function reviewActionColor(action: ReviewAction) {
  switch (action) {
    case "APPROVE_SUBMISSION":
    case "PUBLISH_PROFILE":
      return "success" as const;
    case "REJECT_SUBMISSION":
      return "error" as const;
    case "REQUEST_CHANGES":
      return "warning" as const;
    case "START_REVIEW":
    case "ADD_REVIEW_FINDING":
      return "primary" as const;
    default:
      return "primary" as const;
  }
}

export function providerConnectionsSectionFromPathname(pathname: string): ConsoleSection {
  const normalized = pathname === "/platform/provider-connections/public-profile-lifecycle"
    ? "/platform/provider-connections/public-profile-reviews"
    : pathname;
  const exact = SECTIONS.find((section) => section.path === normalized);
  if (exact) {
    return exact.key;
  }
  const prefix = [...SECTIONS]
    .sort((left, right) => right.path.length - left.path.length)
    .find((section) => normalized.startsWith(`${section.path}/`));
  return prefix?.key || "overview";
}

function activeSection(pathname: string): ConsoleSection {
  return providerConnectionsSectionFromPathname(pathname);
}

function actionChipColor(status: string | null | undefined) {
  const normalized = (status || "").toUpperCase();
  if (["LINKED", "CONNECTED", "ONLINE_BOOKING", "ENABLED", "READY", "AVAILABLE_TODAY", "NEXT_AVAILABLE", "APPROVED", "PUBLISHED", "VISIBLE"].includes(normalized)) return "success" as const;
  if (["SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUESTED", "PROPOSED", "PENDING_VERIFICATION", "REQUEST_APPOINTMENT"].includes(normalized)) return "warning" as const;
  if (["DISPUTED", "REJECTED", "FAILED", "CONFLICT", "INACTIVE", "UNAVAILABLE", "NOT_LINKED", "NOT_CONNECTED", "DISABLED"].includes(normalized)) return "error" as const;
  return "default" as const;
}

function sectionPath(section: ConsoleSection) {
  return SECTIONS.find((item) => item.key === section)?.path || SECTIONS[0].path;
}

function parseReviewType(value: string | null): ProviderConnectionsPublicProfileType | "" {
  const normalized = (value || "").trim().toUpperCase();
  return normalized === "CLINIC" || normalized === "DOCTOR" || normalized === "HOSPITAL" ? normalized : "";
}

function parseSelection(value: string) {
  const [tenantReference = "", platformClinicReference = "", tenantDoctorUserReference = "", tenantDoctorProfileReference = "", platformEntityRevision = "0"] = value.split("||");
  return { tenantReference, platformClinicReference, tenantDoctorUserReference, tenantDoctorProfileReference, platformEntityRevision: Number(platformEntityRevision || 0) };
}

function toSelectionValue(row: ProviderConnectionsPlatformEntityResponse) {
  return [row.tenantReference || "", row.platformClinicReference || "", row.tenantDoctorUserReference || "", row.tenantDoctorProfileReference || "", String(row.sourceRevision || 0)].join("||");
}

function evidenceSummary(item: ProviderConnectionsEvidence) {
  return `${item.evidenceType}: ${item.result} (${item.strength})`;
}

function evidenceTone(strength: string | null | undefined) {
  switch ((strength || "").toUpperCase()) {
    case "STRONG":
      return "success" as const;
    case "CONFLICT":
      return "error" as const;
    case "SUPPORTING":
      return "info" as const;
    default:
      return "default" as const;
  }
}

function reviewSection(review: ProviderPublicProfileReviewResponse | null, key: string) {
  const value = review?.contentSnapshot?.[key];
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function reviewText(review: ProviderPublicProfileReviewResponse | null, section: string, field: string) {
  const value = reviewSection(review, section)[field];
  if (typeof value === "string") {
    return value;
  }
  if (value == null) {
    return null;
  }
  return String(value);
}

function reviewOwnershipStatus(review: ProviderPublicProfileReviewResponse | null) {
  const ownership = review?.ownershipSnapshot && typeof review.ownershipSnapshot === "object" ? review.ownershipSnapshot as Record<string, unknown> : {};
  const value = ownership.ownershipStatus;
  return typeof value === "string" && value.trim().length ? value : null;
}

function reviewReadinessSnapshot(review: ProviderPublicProfileReviewResponse | null) {
  const readiness = review?.readinessSnapshot && typeof review.readinessSnapshot === "object" ? review.readinessSnapshot as Record<string, unknown> : {};
  const completeness = typeof readiness.completenessPercentage === "number"
    ? readiness.completenessPercentage
    : Number(readiness.completenessPercentage);
  const readinessStatus = typeof readiness.readinessStatus === "string" ? readiness.readinessStatus : null;
  return {
    completenessPercentage: Number.isFinite(completeness) ? completeness : null,
    readinessStatus,
  };
}

function reviewDetailPath(submissionReference: string) {
  return `/platform/provider-connections/public-profile-reviews/${encodeURIComponent(submissionReference)}`;
}

function reviewStatusCounts(rows: ProviderPublicProfileReviewQueueResponse[]) {
  const counts = {
    submitted: 0,
    underReview: 0,
    changesRequested: 0,
    approved: 0,
    published: 0,
  };
  for (const row of rows) {
    switch ((row.moderationStatus || "").toUpperCase()) {
      case "SUBMITTED":
        counts.submitted += 1;
        break;
      case "UNDER_REVIEW":
        counts.underReview += 1;
        break;
      case "CHANGES_REQUESTED":
        counts.changesRequested += 1;
        break;
      case "APPROVED":
        counts.approved += 1;
        break;
      case "PUBLISHED":
        counts.published += 1;
        break;
      default:
        break;
    }
  }
  return counts;
}

function isOwnershipAction(value: string): value is OwnershipAction {
  return [
    "APPROVE_OWNERSHIP",
    "REJECT_OWNERSHIP",
    "DISPUTE_OWNERSHIP",
    "REVOKE_CLAIM",
    "REVOKE_OWNERSHIP",
    "VIEW_OWNERSHIP",
    "RESOLVE_DISPUTE",
  ].includes(value);
}

function ownershipActionLabel(action: OwnershipAction) {
  switch (action) {
    case "APPROVE_OWNERSHIP":
      return "Approve";
    case "REJECT_OWNERSHIP":
      return "Reject";
    case "DISPUTE_OWNERSHIP":
      return "Dispute";
    case "REVOKE_CLAIM":
      return "Revoke claim";
    case "REVOKE_OWNERSHIP":
      return "Revoke ownership";
    case "VIEW_OWNERSHIP":
      return "View ownership";
    case "RESOLVE_DISPUTE":
      return "Resolve dispute";
  }
}

function ownershipActionVariant(action: OwnershipAction) {
  switch (action) {
    case "APPROVE_OWNERSHIP":
      return "contained" as const;
    case "REJECT_OWNERSHIP":
      return "outlined" as const;
    case "DISPUTE_OWNERSHIP":
      return "outlined" as const;
    case "REVOKE_CLAIM":
    case "REVOKE_OWNERSHIP":
      return "text" as const;
    case "VIEW_OWNERSHIP":
      return "outlined" as const;
    case "RESOLVE_DISPUTE":
      return "outlined" as const;
  }
}

function ownershipActionColor(action: OwnershipAction) {
  switch (action) {
    case "APPROVE_OWNERSHIP":
      return "success" as const;
    case "REJECT_OWNERSHIP":
    case "DISPUTE_OWNERSHIP":
    case "REVOKE_CLAIM":
    case "REVOKE_OWNERSHIP":
      return "error" as const;
    case "VIEW_OWNERSHIP":
    case "RESOLVE_DISPUTE":
      return "primary" as const;
  }
}

function ownershipActionPermission(action: OwnershipAction) {
  switch (action) {
    case "APPROVE_OWNERSHIP":
      return "platform.provider_connection.approve";
    case "REJECT_OWNERSHIP":
      return "platform.provider_connection.reject";
    case "DISPUTE_OWNERSHIP":
      return "platform.provider_connection.identity_override";
    case "REVOKE_CLAIM":
    case "REVOKE_OWNERSHIP":
      return "platform.provider_connection.unlink";
    case "VIEW_OWNERSHIP":
    case "RESOLVE_DISPUTE":
      return "platform.provider_connection.view";
  }
}

function buildProposalEvidence(
  draft: ProposalDraft,
  platform: ProviderConnectionsPlatformEntityResponse | null
): ProviderConnectionsEvidence[] {
  if (!platform) {
    return draft.evidence;
  }
  const publicName = draft.publicProfile?.displayName || draft.suggestion?.publicDisplayName || draft.publicReference || "—";
  const publicCity = draft.publicProfile?.city || draft.suggestion?.platformCity || "—";
  const publicArea = draft.publicProfile?.area || draft.suggestion?.platformArea || "—";
  const publicPhone = draft.publicProfile?.publicPhone || draft.suggestion?.platformPhone || "—";
  const publicFee = draft.publicProfile?.publicFee || "—";
  const publicRegistration = draft.publicReference || "—";
  const exact = (left: string | null | undefined, right: string | null | undefined) =>
    Boolean(left && right && left.trim().toLowerCase() === right.trim().toLowerCase());
  const has = (value: string | null | undefined) => Boolean(value && value.trim().length > 0);
  const evidence: ProviderConnectionsEvidence[] = [
    {
      evidenceType: "DISPLAY_NAME_EXACT",
      result: exact(publicName, platform.displayName) ? "MATCH" : "DIFFERS",
      strength: exact(publicName, platform.displayName) ? "STRONG" : "SUPPORTING",
      publicDisplayValue: publicName,
      platformDisplayValue: platform.displayName || "—",
      sourceRevision: draft.sourceRevision,
      recordedAt: new Date().toISOString(),
      explanation: "Compare public and platform display names.",
    },
    {
      evidenceType: "CITY_EXACT",
      result: exact(publicCity, platform.city) ? "MATCH" : "DIFFERS",
      strength: exact(publicCity, platform.city) ? "SUPPORTING" : "WEAK",
      publicDisplayValue: publicCity,
      platformDisplayValue: platform.city || "—",
      sourceRevision: draft.sourceRevision,
      recordedAt: new Date().toISOString(),
      explanation: "Compare the public and platform cities.",
    },
    {
      evidenceType: "PHONE_EXACT",
      result: exact(publicPhone, platform.phone) ? "MATCH" : "DIFFERS",
      strength: exact(publicPhone, platform.phone) ? "STRONG" : "SUPPORTING",
      publicDisplayValue: publicPhone,
      platformDisplayValue: platform.phone || "—",
      sourceRevision: draft.sourceRevision,
      recordedAt: new Date().toISOString(),
      explanation: "Compare the verified contact numbers.",
    },
    {
      evidenceType: "REGISTRATION_EXACT",
      result: exact(publicRegistration, platform.registrationNumber) ? "MATCH" : "DIFFERS",
      strength: exact(publicRegistration, platform.registrationNumber) ? "STRONG" : "SUPPORTING",
      publicDisplayValue: publicRegistration,
      platformDisplayValue: platform.registrationNumber || "—",
      sourceRevision: draft.sourceRevision,
      recordedAt: new Date().toISOString(),
      explanation: "Compare the business or medical registration reference.",
    },
  ];

  if (draft.kind === "DOCTOR") {
    evidence.push(
      {
        evidenceType: "SPECIALTY_EXACT",
        result: exact(publicArea, platform.specialty) ? "MATCH" : "DIFFERS",
        strength: exact(publicArea, platform.specialty) ? "SUPPORTING" : "WEAK",
        publicDisplayValue: publicArea,
        platformDisplayValue: platform.specialty || "—",
        sourceRevision: draft.sourceRevision,
        recordedAt: new Date().toISOString(),
        explanation: "Compare the public practice area with the tenant specialty.",
      },
      {
        evidenceType: "QUALIFICATION_COMPATIBLE",
        result: has(platform.qualification) ? "MATCH" : "MISSING",
        strength: has(platform.qualification) ? "SUPPORTING" : "WEAK",
        publicDisplayValue: publicFee,
        platformDisplayValue: platform.qualification || "—",
        sourceRevision: draft.sourceRevision,
        recordedAt: new Date().toISOString(),
        explanation: "Compare the available professional summary.",
      }
    );
  }

  return evidence;
}

function deriveProposalMatchMethod(evidence: ProviderConnectionsEvidence[]): ProviderConnectionsMatchMethod {
  const hasMatch = (type: string) => evidence.some((item) => item.evidenceType === type && item.result === "MATCH");
  const exactRegistration = hasMatch("REGISTRATION_EXACT");
  const exactContact = hasMatch("PHONE_EXACT") || hasMatch("VERIFIED_PHONE_EXACT") || hasMatch("VERIFIED_EMAIL_EXACT");
  const identity = hasMatch("DISPLAY_NAME_EXACT") && hasMatch("CITY_EXACT");
  if (exactRegistration && exactContact) return "REGISTRATION_AND_CONTACT";
  if (exactRegistration) return "REGISTRATION_EXACT";
  if (exactContact) return "VERIFIED_CONTACT_EXACT";
  if (identity) return "BUSINESS_IDENTITY_MATCH";
  return "MANUAL_REFERENCE";
}

function deriveProposalConfidence(evidence: ProviderConnectionsEvidence[]): ProviderConnectionsMatchConfidence {
  const hasConflict = evidence.some((item) => item.strength === "CONFLICT");
  const strong = evidence.some((item) => item.strength === "STRONG");
  const supporting = evidence.some((item) => item.strength === "SUPPORTING");
  if (hasConflict) return "LOW";
  if (strong && supporting) return "HIGH";
  if (strong) return "HIGH";
  if (supporting) return "MEDIUM";
  return "LOW";
}

function sectionTitle(section: ConsoleSection) {
  switch (section) {
    case "public-profiles":
      return "Public Profiles";
    case "public-profile-reviews":
      return "Public Profile Reviews";
    case "platform-entities":
      return "Platform Entities";
    case "suggestions":
      return "Suggested Matches";
    case "links":
      return "Links";
    case "ownerships":
      return "Ownerships";
    case "conflicts":
      return "Conflicts";
    case "audit":
      return "Audit";
    default:
      return "Overview";
  }
}

function ProposalDialog({
  open,
  draft,
  clinicOptions,
  doctorOptions,
  onClose,
  onSubmit,
}: {
  open: boolean;
  draft: ProposalDraft | null;
  clinicOptions: ProviderConnectionsPlatformEntityResponse[];
  doctorOptions: ProviderConnectionsPlatformEntityResponse[];
  onClose: () => void;
  onSubmit: (draft: ProposalDraft) => void;
}) {
  const [current, setCurrent] = React.useState<ProposalDraft | null>(draft);

  React.useEffect(() => {
    setCurrent(draft);
  }, [draft]);

  React.useEffect(() => {
    if (!current) {
      return;
    }
    const options = current.kind === "DOCTOR" ? doctorOptions : clinicOptions;
    if (!options.length) {
      return;
    }
    const selected = options.find((row) => toSelectionValue(row) === current.platformSelection) || options[0];
    if (!selected) {
      return;
    }
    const nextSelection = toSelectionValue(selected);
    const nextEvidence = buildProposalEvidence(current, selected);
    const nextMatchMethod = deriveProposalMatchMethod(nextEvidence);
    const nextConfidence = deriveProposalConfidence(nextEvidence);
    const nextDraft: ProposalDraft = current.kind === "DOCTOR"
      ? {
          ...current,
          tenantReference: selected.tenantReference || current.tenantReference,
          platformClinicReference: selected.platformClinicReference || current.platformClinicReference,
          tenantDoctorUserReference: selected.tenantDoctorUserReference || current.tenantDoctorUserReference,
          tenantDoctorProfileReference: selected.tenantDoctorProfileReference || current.tenantDoctorProfileReference,
          platformEntityRevision: selected.sourceRevision || current.platformEntityRevision,
          platformSelection: nextSelection,
          matchMethod: nextMatchMethod,
          matchConfidence: nextConfidence,
          evidence: nextEvidence,
        }
      : {
          ...current,
          tenantReference: selected.tenantReference || current.tenantReference,
          platformClinicReference: selected.platformClinicReference || current.platformClinicReference,
          platformEntityRevision: selected.sourceRevision || current.platformEntityRevision,
          platformSelection: nextSelection,
          matchMethod: nextMatchMethod,
          matchConfidence: nextConfidence,
          evidence: nextEvidence,
        };
    if (JSON.stringify(nextDraft) !== JSON.stringify(current)) {
      setCurrent(nextDraft);
    }
  }, [clinicOptions, current, doctorOptions]);

  if (!current) return null;

  const options = current.kind === "DOCTOR" ? doctorOptions : clinicOptions;
  const selectedPlatform = options.find((row) => toSelectionValue(row) === current.platformSelection) || options[0] || null;
  const selectedPublicName = current.publicProfile?.displayName || current.suggestion?.publicDisplayName || current.publicReference || "Selected provider";
  const selectedPublicCity = current.publicProfile?.city || current.suggestion?.platformCity || "—";
  const selectedPublicArea = current.publicProfile?.area || current.suggestion?.platformArea || "—";
  const selectedPublicPhone = current.publicProfile?.publicPhone || current.suggestion?.platformPhone || "—";
  const selectedPublicFee = current.publicProfile?.publicFee || "—";
  const selectedPlatformName = selectedPlatform?.displayName || current.suggestion?.platformDisplayName || "Choose a platform entity";
  const selectedPlatformCity = selectedPlatform?.city || current.suggestion?.platformCity || "—";
  const selectedPlatformArea = selectedPlatform?.area || current.suggestion?.platformArea || "—";
  const selectedPlatformPhone = selectedPlatform?.phone || current.suggestion?.platformPhone || "—";
  const selectedPlatformEmail = selectedPlatform?.email || current.suggestion?.platformEmail || "—";
  const selectedPlatformSpecialty = selectedPlatform?.specialty || current.suggestion?.platformSpecialty || "—";
  const selectedPlatformQualification = selectedPlatform?.qualification || current.suggestion?.platformQualification || "—";
  const selectedPlatformRegistration = selectedPlatform?.registrationNumber || current.suggestion?.platformRegistrationNumber || "—";
  const selectedPlatformYears = selectedPlatform?.yearsOfExperience?.toString() || current.suggestion?.platformYearsOfExperience?.toString() || "—";
  const selectedPlatformStatus = selectedPlatform
    ? `${businessLabel(selectedPlatform.active ? "ACTIVE" : "INACTIVE")} · ${businessLabel(selectedPlatform.publicListingEnabled ? "ENABLED" : "DISABLED")}`
    : businessLabel(current.suggestion?.status || "—");
  const selectedPlatformCapability = businessLabel(selectedPlatform?.currentDiscoverCapability || current.suggestion?.currentDiscoverCapability || "—");
  const selectedPlatformAvailability = businessLabel(selectedPlatform?.currentAvailability || current.suggestion?.currentAvailability || "—");
  const selectedPublicRevision = current.publicProfile?.sourceRevision || current.suggestion?.sourceRevision || 0;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle>Review {formatProviderType(current.kind)} connection</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="info" variant="outlined">
            Select the matching platform record. The references, evidence, match method, and confidence are derived and read-only.
          </Alert>

          <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
            <TextField
              select
              label={current.kind === "DOCTOR" ? "Tenant doctor context" : "Platform clinic"}
              value={current.platformSelection}
              onChange={(event) => setCurrent({ ...current, platformSelection: event.target.value })}
              helperText={current.kind === "DOCTOR" ? "Choose the tenant-scoped doctor context." : "Choose the matching tenant clinic."}
              fullWidth
            >
              {options.map((row) => (
                <MenuItem key={toSelectionValue(row)} value={toSelectionValue(row)}>
                  {row.displayName} · {row.tenantName} · {row.city}
                </MenuItem>
              ))}
            </TextField>
            <TextField label="Source revision" value={selectedPublicRevision || "—"} InputProps={{ readOnly: true }} fullWidth />
          </Stack>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper variant="outlined" sx={{ p: 2, height: "100%" }}>
                <Stack spacing={1.25}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Public {current.kind === "DOCTOR" ? "doctor/practice" : "clinic"}</Typography>
                  <TextField label="Display name" value={selectedPublicName} InputProps={{ readOnly: true }} fullWidth />
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                    <TextField label="City" value={selectedPublicCity} InputProps={{ readOnly: true }} fullWidth />
                    <TextField label="Area" value={selectedPublicArea} InputProps={{ readOnly: true }} fullWidth />
                  </Stack>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                    <TextField label="Phone" value={selectedPublicPhone} InputProps={{ readOnly: true }} fullWidth />
                    <TextField label="Public fee" value={selectedPublicFee} InputProps={{ readOnly: true }} fullWidth />
                  </Stack>
                  <TextField label="Public reference" value={current.publicReference || "—"} InputProps={{ readOnly: true }} fullWidth />
                  {current.publicPracticeReference ? <TextField label="Public practice reference" value={current.publicPracticeReference} InputProps={{ readOnly: true }} fullWidth /> : null}
                </Stack>
              </Paper>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper variant="outlined" sx={{ p: 2, height: "100%" }}>
                <Stack spacing={1.25}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Platform {current.kind === "DOCTOR" ? "doctor context" : "clinic"}</Typography>
                  <TextField label="Display name" value={selectedPlatformName} InputProps={{ readOnly: true }} fullWidth />
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                    <TextField label="City" value={selectedPlatformCity} InputProps={{ readOnly: true }} fullWidth />
                    <TextField label="Area" value={selectedPlatformArea} InputProps={{ readOnly: true }} fullWidth />
                  </Stack>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                    <TextField label="Phone" value={selectedPlatformPhone} InputProps={{ readOnly: true }} fullWidth />
                    <TextField label="Email" value={selectedPlatformEmail} InputProps={{ readOnly: true }} fullWidth />
                  </Stack>
                  {current.kind === "DOCTOR" ? (
                    <>
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                        <TextField label="Specialty" value={selectedPlatformSpecialty} InputProps={{ readOnly: true }} fullWidth />
                        <TextField label="Qualification" value={selectedPlatformQualification} InputProps={{ readOnly: true }} fullWidth />
                      </Stack>
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                        <TextField label="Registration" value={selectedPlatformRegistration} InputProps={{ readOnly: true }} fullWidth />
                        <TextField label="Years of experience" value={selectedPlatformYears} InputProps={{ readOnly: true }} fullWidth />
                      </Stack>
                    </>
                  ) : null}
                  <TextField label="Tenant reference" value={current.tenantReference || "—"} InputProps={{ readOnly: true }} fullWidth />
                  <TextField label="Platform clinic reference" value={current.platformClinicReference || "—"} InputProps={{ readOnly: true }} fullWidth />
                  {current.kind === "DOCTOR" ? (
                    <>
                      <TextField label="Tenant doctor user reference" value={current.tenantDoctorUserReference || "—"} InputProps={{ readOnly: true }} fullWidth />
                      <TextField label="Tenant doctor profile reference" value={current.tenantDoctorProfileReference || "—"} InputProps={{ readOnly: true }} fullWidth />
                    </>
                  ) : null}
                  <TextField label="Platform revision" value={selectedPlatform?.sourceRevision || current.platformEntityRevision || "—"} InputProps={{ readOnly: true }} fullWidth />
                  <TextField label="Platform status" value={selectedPlatformStatus} InputProps={{ readOnly: true }} fullWidth />
                  <TextField label="Capability" value={selectedPlatformCapability} InputProps={{ readOnly: true }} fullWidth />
                  <TextField label="Availability" value={selectedPlatformAvailability} InputProps={{ readOnly: true }} fullWidth />
                </Stack>
              </Paper>
            </Grid>
          </Grid>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={1.25}>
              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Automatic evidence</Typography>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {(current.evidence.length ? current.evidence : selectedPlatform ? buildProposalEvidence(current, selectedPlatform) : []).map((item) => (
                  <Chip key={evidenceSummary(item)} label={evidenceSummary(item)} color={evidenceTone(item.strength)} variant="outlined" />
                ))}
              </Stack>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                <TextField label="Match method" value={businessLabel(current.matchMethod)} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="Confidence" value={businessLabel(current.matchConfidence)} InputProps={{ readOnly: true }} fullWidth />
              </Stack>
            </Stack>
          </Paper>

          <TechnicalDetails>
            <Typography variant="body2" color="text.secondary">Public Reference</Typography>
            <Typography variant="body2">{current.publicReference || "—"}</Typography>
            <Typography variant="body2" color="text.secondary">Public Practice Reference</Typography>
            <Typography variant="body2">{current.publicPracticeReference || "—"}</Typography>
            <Typography variant="body2" color="text.secondary">Tenant Reference</Typography>
            <Typography variant="body2">{current.tenantReference || "—"}</Typography>
            <Typography variant="body2" color="text.secondary">Platform Clinic Reference</Typography>
            <Typography variant="body2">{current.platformClinicReference || "—"}</Typography>
          </TechnicalDetails>

          <TextField
            label="Review note"
            value={current.reason}
            onChange={(event) => setCurrent({ ...current, reason: event.target.value })}
            fullWidth
            multiline
            minRows={3}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => onSubmit(current)}>
          Propose link
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function ReviewCommandDialog({
  open,
  action,
  review,
  reason,
  findings,
  onClose,
  onReasonChange,
  onFindingsChange,
  onAddFinding,
  onRemoveFinding,
  onSubmit,
  saving,
}: {
  open: boolean;
  action: ReviewAction | null;
  review: ProviderPublicProfileReviewResponse | null;
  reason: string;
  findings: ReviewFindingDraft[];
  onClose: () => void;
  onReasonChange: (value: string) => void;
  onFindingsChange: (index: number, patch: Partial<ReviewFindingDraft>) => void;
  onAddFinding: () => void;
  onRemoveFinding: (index: number) => void;
  onSubmit: () => void;
  saving: boolean;
}) {
  const requiresFindings = action === "REQUEST_CHANGES" || action === "ADD_REVIEW_FINDING";
  const isFindingAction = action === "ADD_REVIEW_FINDING";

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{action ? formatReviewAction(action) : "Review action"}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="info" variant="outlined">
            {action === "START_REVIEW"
              ? "Start review locks the selected submission to a reviewer."
              : action === "ADD_REVIEW_FINDING"
                ? "Add finding records a non-decision review observation while the review remains in progress."
              : action === "REQUEST_CHANGES"
                ? "Request changes captures structured findings grouped by editor section."
              : action === "REJECT_SUBMISSION"
                  ? "Reject submission keeps ownership and consent unchanged."
                  : action === "APPROVE_SUBMISSION"
                    ? "Approval marks the submission approved but does not publish it."
                    : action === "PUBLISH_PROFILE"
                      ? "Publish creates or updates the public projection."
                      : "Use the backend-authoritative moderation action."}
          </Alert>
          {!isFindingAction ? (
            <TextField
              label={action === "START_REVIEW" ? "Review note" : action === "REQUEST_CHANGES" ? "Provider-facing message" : action === "APPROVE_SUBMISSION" ? "Approval note" : "Reason"}
              value={reason}
              onChange={(event) => onReasonChange(event.target.value)}
              fullWidth
              multiline
              minRows={3}
              helperText={action === "START_REVIEW"
                ? "Optional internal note recorded when the review begins."
                : action === "APPROVE_SUBMISSION"
                  ? "Optional note recorded with the approval."
                  : action === "REQUEST_CHANGES"
                    ? "Use this message for the Provider-facing correction request."
                    : undefined}
            />
          ) : null}
          {requiresFindings ? (
            <Stack spacing={1.5}>
              <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap">
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{isFindingAction ? "Finding details" : "Structured findings"}</Typography>
                <Button size="small" variant="outlined" onClick={onAddFinding}>
                  Add finding
                </Button>
              </Stack>
              {findings.map((finding, index) => (
                <Paper key={`${finding.section}-${finding.field}-${index}`} variant="outlined" sx={{ p: 1.5 }}>
                  <Stack spacing={1}>
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                      <TextField select label="Section" value={finding.section} onChange={(event) => onFindingsChange(index, { section: event.target.value })} fullWidth>
                        {["Overview", "About", "Contact", "Services", "Specialities", "Facilities", "Timings", "Fees", "Languages", "Media", "SEO", "Other"].map((sectionOption) => (
                          <MenuItem key={sectionOption} value={sectionOption}>{sectionOption}</MenuItem>
                        ))}
                      </TextField>
                      <TextField label="Category" value={finding.category} onChange={(event) => onFindingsChange(index, { category: event.target.value })} fullWidth />
                    </Stack>
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                      <TextField select label="Severity" value={finding.severity} onChange={(event) => onFindingsChange(index, { severity: event.target.value })} fullWidth>
                        {["Info", "Warning", "Critical"].map((severityOption) => (
                          <MenuItem key={severityOption} value={severityOption}>{severityOption}</MenuItem>
                        ))}
                      </TextField>
                      <TextField label="Field" value={finding.field} onChange={(event) => onFindingsChange(index, { field: event.target.value })} fullWidth />
                    </Stack>
                    <TextField
                      label="Provider-facing message"
                      value={finding.providerFacingMessage}
                      onChange={(event) => onFindingsChange(index, { providerFacingMessage: event.target.value })}
                      fullWidth
                      multiline
                      minRows={2}
                    />
                    <TextField
                      label="Internal review note"
                      value={finding.internalNote}
                      onChange={(event) => onFindingsChange(index, { internalNote: event.target.value })}
                      fullWidth
                      multiline
                      minRows={2}
                      helperText="Optional private note for the Platform review record."
                    />
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                      <TextField select label="Blocking" value={finding.blocking ? "true" : "false"} onChange={(event) => onFindingsChange(index, { blocking: event.target.value === "true", required: event.target.value === "true" || finding.providerActionRequired })} fullWidth>
                        <MenuItem value="false">No</MenuItem>
                        <MenuItem value="true">Yes</MenuItem>
                      </TextField>
                      <TextField select label="Provider action required" value={finding.providerActionRequired ? "true" : "false"} onChange={(event) => onFindingsChange(index, { providerActionRequired: event.target.value === "true", required: finding.blocking || event.target.value === "true" })} fullWidth>
                        <MenuItem value="false">No</MenuItem>
                        <MenuItem value="true">Yes</MenuItem>
                      </TextField>
                    </Stack>
                    <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
                      <Button size="small" variant="text" color="error" onClick={() => onRemoveFinding(index)} disabled={findings.length === 1}>
                        Remove finding
                      </Button>
                    </Box>
                  </Stack>
                </Paper>
              ))}
            </Stack>
          ) : null}
          {review ? (
            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Stack spacing={0.75}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Selected submission</Typography>
                <Typography variant="body2" color="text.secondary">{reviewText(review, "about", "displayName") || review.publicProfileReference || "Selected profile"}</Typography>
                <Typography variant="caption" color="text.secondary">
                  Submitted version {review.submittedDraftVersion} · {formatModerationStatus(review.moderationStatus)} · {formatVisibilityStatus(review.effectiveVisibility)}
                </Typography>
              </Stack>
            </Paper>
          ) : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={onSubmit} disabled={saving}>
          {action ? formatReviewAction(action) : "Submit"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function ProviderConnectionsPage() {
  const auth = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const section = activeSection(location.pathname);
  const selectedReviewRouteReference = React.useMemo(() => {
    const match = location.pathname.match(/^\/platform\/provider-connections\/public-profile-reviews\/([^/]+)$/);
    return match ? decodeURIComponent(match[1]) : null;
  }, [location.pathname]);
  const [overview, setOverview] = React.useState<ProviderConnectionsOverviewResponse | null>(null);
  const [publicRows, setPublicRows] = React.useState<ProviderConnectionsPublicProfileResponse[]>([]);
  const [reviewRows, setReviewRows] = React.useState<ProviderPublicProfileReviewQueueResponse[]>([]);
  const [platformClinicRows, setPlatformClinicRows] = React.useState<ProviderConnectionsPlatformEntityResponse[]>([]);
  const [platformDoctorRows, setPlatformDoctorRows] = React.useState<ProviderConnectionsPlatformEntityResponse[]>([]);
  const [linkRows, setLinkRows] = React.useState<ProviderConnectionsLinkResponse[]>([]);
  const [suggestions, setSuggestions] = React.useState<ProviderConnectionsSuggestionResponse[]>([]);
  const [conflicts, setConflicts] = React.useState<ProviderConnectionsConflictResponse[]>([]);
  const [ownershipRows, setOwnershipRows] = React.useState<ProviderConnectionsOwnershipResponse[]>([]);
  const [auditRows, setAuditRows] = React.useState<ProviderConnectionsAuditResponse[]>([]);
  const [selectedPublicProfileReference, setSelectedPublicProfileReference] = React.useState<string | null>(null);
  const [selectedPublicProfileSnapshot, setSelectedPublicProfileSnapshot] = React.useState<ProviderConnectionsPublicProfileResponse | null>(null);
  const [selectedPlatformEntityReference, setSelectedPlatformEntityReference] = React.useState<string | null>(null);
  const [selectedSuggestionKey, setSelectedSuggestionKey] = React.useState<string | null>(null);
  const [selectedLinkReference, setSelectedLinkReference] = React.useState<string | null>(null);
  const [selectedLinkDetail, setSelectedLinkDetail] = React.useState<ProviderConnectionsLinkDetailResponse | null>(null);
  const [selectedLinkDetailError, setSelectedLinkDetailError] = React.useState<string | null>(null);
  const [selectedOwnershipReference, setSelectedOwnershipReference] = React.useState<string | null>(null);
  const [selectedAuditEventReference, setSelectedAuditEventReference] = React.useState<string | null>(null);
  const [selectedConflictReference, setSelectedConflictReference] = React.useState<string | null>(null);
  const [publicProfileType, setPublicProfileType] = React.useState<ProviderConnectionsPublicProfileType>("CLINIC");
  const [publicProfileCity, setPublicProfileCity] = React.useState("");
  const [publicProfileQuery, setPublicProfileQuery] = React.useState("");
  const [entityType, setEntityType] = React.useState("CLINIC");
  const [entityQuery, setEntityQuery] = React.useState("");
  const [linkType, setLinkType] = React.useState("");
  const [linkStatus, setLinkStatus] = React.useState("");
  const [linkQuery, setLinkQuery] = React.useState("");
  const [suggestionQuery, setSuggestionQuery] = React.useState("");
  const [auditAction, setAuditAction] = React.useState("");
  const [auditTenant, setAuditTenant] = React.useState("");
  const [auditProviderType, setAuditProviderType] = React.useState("");
  const [auditResult, setAuditResult] = React.useState("");
  const [auditQuery, setAuditQuery] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [proposalDraft, setProposalDraft] = React.useState<ProposalDraft | null>(null);
  const [selectedReviewReference, setSelectedReviewReference] = React.useState<string | null>(null);
  const [selectedReview, setSelectedReview] = React.useState<ProviderPublicProfileReviewResponse | null>(null);
  const [reviewCommandAction, setReviewCommandAction] = React.useState<ReviewAction | null>(null);
  const [reviewCommandReason, setReviewCommandReason] = React.useState("");
  const [reviewFindings, setReviewFindings] = React.useState<ReviewFindingDraft[]>([
    {
      section: "About",
      field: "description",
      category: "Description",
      severity: "Critical",
      required: true,
      blocking: true,
      providerActionRequired: true,
      reviewerNote: "",
      providerFacingMessage: "",
      internalNote: "",
    },
  ]);
  const [rejectDialogOpen, setRejectDialogOpen] = React.useState(false);
  const [rejectTarget, setRejectTarget] = React.useState<ProviderConnectionsSuggestionResponse | null>(null);
  const [rejectReason, setRejectReason] = React.useState("");
  const [saving, setSaving] = React.useState(false);
  const reviewPublicType = parseReviewType(searchParams.get("type"));
  const reviewPublicQuery = searchParams.get("q") || "";
  const reviewPublicCity = searchParams.get("city") || "";
  const authSignature = React.useMemo(() => [
    auth.initialized ? "initialized" : "booting",
    auth.accessToken ? "token" : "no-token",
    auth.rolesUpper.join(","),
    auth.permissions.join(","),
    auth.selectedTenant?.id || "platform",
  ].join("|"), [auth.initialized, auth.accessToken, auth.permissions, auth.rolesUpper, auth.selectedTenant?.id]);

  React.useEffect(() => {
    if (section !== "public-profile-reviews") {
      if (selectedReviewReference !== null) {
        setSelectedReviewReference(null);
      }
      return;
    }
    if (selectedReviewRouteReference && selectedReviewReference !== selectedReviewRouteReference) {
      setSelectedReviewReference(selectedReviewRouteReference);
      return;
    }
    if (!selectedReviewRouteReference && reviewRows.length && !selectedReviewReference) {
      setSelectedReviewReference(reviewRows[0].submissionReference);
    }
  }, [reviewRows, section, selectedReviewReference, selectedReviewRouteReference]);

  React.useEffect(() => {
    if (!selectedPublicProfileReference) {
      return;
    }
    const current = publicRows.find((row) => row.publicReference === selectedPublicProfileReference);
    if (current) {
      setSelectedPublicProfileSnapshot(current);
    }
  }, [publicRows, selectedPublicProfileReference]);

  React.useEffect(() => {
    if (selectedPlatformEntityReference) {
      const current = platformClinicRows.find((row) => toSelectionValue(row) === selectedPlatformEntityReference)
        || platformDoctorRows.find((row) => toSelectionValue(row) === selectedPlatformEntityReference)
        || null;
      if (current) {
        return;
      }
      setSelectedPlatformEntityReference(null);
    }
  }, [platformClinicRows, platformDoctorRows, selectedPlatformEntityReference]);

  React.useEffect(() => {
    if (selectedSuggestionKey && !suggestions.some((row) => row.id === selectedSuggestionKey)) {
      setSelectedSuggestionKey(null);
    }
  }, [selectedSuggestionKey, suggestions]);

  React.useEffect(() => {
    if (selectedLinkReference && !linkRows.some((row) => row.id === selectedLinkReference)) {
      setSelectedLinkReference(null);
      setSelectedLinkDetail(null);
      setSelectedLinkDetailError(null);
    }
  }, [linkRows, selectedLinkReference]);

  React.useEffect(() => {
    if (selectedOwnershipReference && !ownershipRows.some((row) => row.ownershipId === selectedOwnershipReference)) {
      setSelectedOwnershipReference(null);
    }
  }, [ownershipRows, selectedOwnershipReference]);

  React.useEffect(() => {
    if (selectedAuditEventReference && !auditRows.some((row) => row.id === selectedAuditEventReference)) {
      setSelectedAuditEventReference(null);
    }
  }, [auditRows, selectedAuditEventReference]);

  React.useEffect(() => {
    if (selectedConflictReference && !conflicts.some((row) => row.id === selectedConflictReference)) {
      setSelectedConflictReference(null);
    }
  }, [conflicts, selectedConflictReference]);

  const refresh = React.useCallback(async () => {
    if (!auth.accessToken) return;
    setLoading(true);
    setError(null);
    try {
      const overviewPromise = getProviderConnectionsOverview(auth.accessToken);
      const overviewRes = await overviewPromise.catch((reason) => ({ error: reason }));
      setOverview("error" in overviewRes ? null : overviewRes);

      if ("error" in overviewRes) {
        throw overviewRes.error;
      }

      switch (section) {
        case "public-profile-reviews": {
          const [reviewRes, reviewDetailRes] = await Promise.allSettled([
            listProviderConnectionsPublicProfileReviews(auth.accessToken, {
              type: reviewPublicType || null,
              q: reviewPublicQuery || null,
              city: reviewPublicCity || null,
            }),
            selectedReviewReference ? getProviderConnectionsPublicProfileReview(auth.accessToken, selectedReviewReference) : Promise.resolve(null),
          ]);
          setReviewRows(reviewRes.status === "fulfilled" ? reviewRes.value : []);
          setSelectedReview(reviewDetailRes.status === "fulfilled" ? reviewDetailRes.value : null);
          if (reviewDetailRes.status === "rejected") {
            setError(reviewDetailRes.reason instanceof Error ? reviewDetailRes.reason.message : "Provider connections could not be loaded.");
          } else if (reviewRes.status === "rejected") {
            setError(reviewRes.reason instanceof Error ? reviewRes.reason.message : "Provider connections could not be loaded.");
          }
          break;
        }
        case "public-profiles": {
          const publicRes = await listProviderConnectionsPublicProfiles(auth.accessToken, { type: publicProfileType, q: publicProfileQuery || null, city: publicProfileCity || null }).catch((reason) => ({ error: reason }));
          setPublicRows("error" in publicRes ? [] : publicRes);
          if ("error" in publicRes) {
            throw publicRes.error;
          }
          break;
        }
        case "platform-entities": {
          const [clinicEntitiesRes, doctorEntitiesRes] = await Promise.allSettled([
            listProviderConnectionsPlatformEntities(auth.accessToken, { type: "CLINIC", q: entityQuery || null }),
            listProviderConnectionsPlatformEntities(auth.accessToken, { type: "DOCTOR", q: entityQuery || null }),
          ]);
          setPlatformClinicRows(clinicEntitiesRes.status === "fulfilled" ? clinicEntitiesRes.value : []);
          setPlatformDoctorRows(doctorEntitiesRes.status === "fulfilled" ? doctorEntitiesRes.value : []);
          if (clinicEntitiesRes.status === "rejected" || doctorEntitiesRes.status === "rejected") {
            const reason = clinicEntitiesRes.status === "rejected"
              ? clinicEntitiesRes.reason
              : doctorEntitiesRes.status === "rejected"
                ? doctorEntitiesRes.reason
                : new Error("Provider connections could not be loaded.");
            throw reason;
          }
          break;
        }
        case "links": {
          const [linksRes, detailRes] = await Promise.allSettled([
            listProviderConnectionsLinks(auth.accessToken, { type: linkType || null, status: linkStatus || null, q: linkQuery || null }),
            selectedLinkReference ? getProviderConnectionsLinkDetail(auth.accessToken, selectedLinkReference) : Promise.resolve(null),
          ]);
          setLinkRows(linksRes.status === "fulfilled" ? linksRes.value : []);
          setSelectedLinkDetail(detailRes.status === "fulfilled" ? detailRes.value : null);
          setSelectedLinkDetailError(detailRes.status === "rejected"
            ? (detailRes.reason instanceof Error ? detailRes.reason.message : "Unable to load link details.")
            : null);
          if (linksRes.status === "rejected") {
            throw linksRes.reason;
          }
          break;
        }
        case "suggestions": {
          const suggestionsRes = await listProviderConnectionsSuggestions(auth.accessToken, suggestionQuery || null).catch((reason) => ({ error: reason }));
          setSuggestions("error" in suggestionsRes ? [] : suggestionsRes);
          if ("error" in suggestionsRes) {
            throw suggestionsRes.error;
          }
          break;
        }
        case "ownerships": {
          const ownershipsRes = await listProviderConnectionsOwnerships(auth.accessToken).catch((reason) => ({ error: reason }));
          setOwnershipRows("error" in ownershipsRes ? [] : ownershipsRes);
          if ("error" in ownershipsRes) {
            throw ownershipsRes.error;
          }
          break;
        }
        case "conflicts": {
          const conflictsRes = await listProviderConnectionsConflicts(auth.accessToken).catch((reason) => ({ error: reason }));
          setConflicts("error" in conflictsRes ? [] : conflictsRes);
          if ("error" in conflictsRes) {
            throw conflictsRes.error;
          }
          break;
        }
        case "audit": {
          const auditRes = await getProviderConnectionsAuditEvents(auth.accessToken, { action: auditAction || null, tenantReference: auditTenant || null, providerType: auditProviderType || null, result: auditResult || null, q: auditQuery || null }).catch((reason) => ({ error: reason }));
          setAuditRows("error" in auditRes ? [] : auditRes);
          if ("error" in auditRes) {
            throw auditRes.error;
          }
          break;
        }
        default:
          break;
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Provider connections could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, authSignature, auditAction, auditProviderType, auditQuery, auditResult, auditTenant, entityQuery, linkQuery, linkStatus, linkType, publicProfileCity, publicProfileQuery, publicProfileType, reviewPublicCity, reviewPublicQuery, reviewPublicType, section, selectedLinkReference, selectedReviewReference, suggestionQuery]);

  const openReviewRow = React.useCallback(async (row: ProviderPublicProfileReviewQueueResponse) => {
    if (!row.submissionReference) {
      return;
    }
    setSelectedReview(null);
    setSelectedReviewReference(row.submissionReference);
    const normalizedStatus = (row.moderationStatus || "").toUpperCase();
    if (normalizedStatus === "SUBMITTED" && auth.accessToken) {
      try {
        await startProviderConnectionsPublicProfileReview(auth.accessToken, row.submissionReference, {
          reason: "Open review from queue",
        });
      } catch (err) {
        setError(err instanceof Error ? err.message : "Unable to open the selected review.");
        return;
      }
    }
    navigate(reviewDetailPath(row.submissionReference));
  }, [auth.accessToken, navigate]);

  React.useEffect(() => {
    void refresh();
  }, [refresh]);

  const renderOwnershipAction = React.useCallback((row: ProviderConnectionsOwnershipResponse, action: string) => {
    if (!isOwnershipAction(action)) {
      return null;
    }
    const normalized = action;
    const label = ownershipActionLabel(normalized);
    const variant = ownershipActionVariant(normalized);
    const color = ownershipActionColor(normalized);
    const permission = ownershipActionPermission(normalized);
    const hasPermission = auth.hasPermission(permission);

    const commonProps = {
      size: "small" as const,
      variant,
      color,
      disabled: !hasPermission || saving,
    };

    switch (normalized) {
      case "APPROVE_OWNERSHIP":
        return (
          <Button
            {...commonProps}
            onClick={async () => {
              if (auth.accessToken) {
                await approveProviderConnectionOwnership(auth.accessToken, row.ownershipId, { reason: "Approved from console" });
                await refresh();
              }
            }}
          >
            {label}
          </Button>
        );
      case "REJECT_OWNERSHIP":
        return (
          <Button
            {...commonProps}
            onClick={async () => {
              if (auth.accessToken) {
                await rejectProviderConnectionOwnership(auth.accessToken, row.ownershipId, { reason: "Rejected from console" });
                await refresh();
              }
            }}
          >
            {label}
          </Button>
        );
      case "DISPUTE_OWNERSHIP":
        return (
          <Button
            {...commonProps}
            onClick={async () => {
              if (auth.accessToken) {
                await disputeProviderConnectionOwnership(auth.accessToken, row.ownershipId, { reason: "Marked disputed from console" });
                await refresh();
              }
            }}
          >
            {label}
          </Button>
        );
      case "REVOKE_CLAIM":
      case "REVOKE_OWNERSHIP":
        return (
          <Button
            {...commonProps}
            onClick={async () => {
              if (auth.accessToken) {
                await revokeProviderConnectionOwnership(auth.accessToken, row.ownershipId, { reason: "Revoked from console" });
                await refresh();
              }
            }}
          >
            {label}
          </Button>
        );
      default:
        return null;
    }
  }, [auth, refresh, saving]);

  const onOpenProposal = React.useCallback((row: ProviderConnectionsPublicProfileResponse) => {
    const kind: ProposalKind = row.publicProfileType === "DOCTOR" ? "DOCTOR" : "CLINIC";
    const options = kind === "DOCTOR" ? platformDoctorRows : platformClinicRows;
    const selected = options.find((item) => row.tenantReference && row.platformClinicReference
      ? item.tenantReference === row.tenantReference && item.platformClinicReference === row.platformClinicReference
      : item.displayName === row.displayName && item.city === row.city) || options[0] || null;
    setProposalDraft({
      kind,
      publicProfile: row,
      suggestion: null,
      publicReference: row.publicReference || "",
      publicPracticeReference: row.publicPracticeReference,
      sourceSystem: row.sourceSystem || "PLATFORM_ADMIN",
      sourceEntityReference: row.publicProfileType === "DOCTOR" ? (row.publicPracticeReference || row.publicReference || "") : (row.publicReference || ""),
      sourceRevision: row.sourceRevision,
      sourceUpdatedAt: row.sourceUpdatedAt || null,
      tenantReference: selected?.tenantReference || row.tenantReference || "",
      platformClinicReference: selected?.platformClinicReference || row.platformClinicReference || "",
      tenantDoctorUserReference: selected?.tenantDoctorUserReference || "",
      tenantDoctorProfileReference: selected?.tenantDoctorProfileReference || "",
      platformEntityRevision: selected?.sourceRevision || row.sourceRevision,
      platformSelection: selected ? toSelectionValue(selected) : "",
      matchMethod: "MANUAL_REFERENCE",
      matchConfidence: "LOW",
      reason: "",
      evidence: selected ? buildProposalEvidence({
        kind,
        publicProfile: row,
        suggestion: null,
        publicReference: row.publicReference || "",
        publicPracticeReference: row.publicPracticeReference,
        sourceSystem: row.sourceSystem || "PLATFORM_ADMIN",
        sourceEntityReference: row.publicProfileType === "DOCTOR" ? (row.publicPracticeReference || row.publicReference || "") : (row.publicReference || ""),
        sourceRevision: row.sourceRevision,
        sourceUpdatedAt: row.sourceUpdatedAt || null,
        tenantReference: selected?.tenantReference || row.tenantReference || "",
        platformClinicReference: selected?.platformClinicReference || row.platformClinicReference || "",
        tenantDoctorUserReference: selected?.tenantDoctorUserReference || "",
        tenantDoctorProfileReference: selected?.tenantDoctorProfileReference || "",
        platformEntityRevision: selected?.sourceRevision || row.sourceRevision,
        platformSelection: selected ? toSelectionValue(selected) : "",
        matchMethod: "MANUAL_REFERENCE",
        matchConfidence: "LOW",
        reason: "",
        evidence: [],
      }, selected) : [],
    });
    setDialogOpen(true);
  }, [platformClinicRows, platformDoctorRows]);

  const reviewSuggestion = React.useCallback((row: ProviderConnectionsSuggestionResponse) => {
    const kind: ProposalKind = row.publicProfileType === "DOCTOR" ? "DOCTOR" : "CLINIC";
    const options = kind === "DOCTOR" ? platformDoctorRows : platformClinicRows;
    const selected = options.find((item) => item.tenantReference === row.tenantReference && item.platformClinicReference === row.platformClinicReference) || options[0] || null;
    setSelectedSuggestionKey(row.id);
    setProposalDraft({
      kind,
      publicProfile: null,
      suggestion: row,
      publicReference: row.publicReference || "",
      publicPracticeReference: row.publicPracticeReference,
      sourceSystem: "PLATFORM_ADMIN",
      sourceEntityReference: row.id,
      sourceRevision: row.sourceRevision,
      sourceUpdatedAt: row.lastEvaluatedAt ? row.lastEvaluatedAt.toString() : null,
      tenantReference: row.tenantReference || selected?.tenantReference || "",
      platformClinicReference: row.platformClinicReference || selected?.platformClinicReference || "",
      tenantDoctorUserReference: row.tenantDoctorUserReference || selected?.tenantDoctorUserReference || "",
      tenantDoctorProfileReference: row.tenantDoctorProfileReference || selected?.tenantDoctorProfileReference || "",
      platformEntityRevision: selected?.sourceRevision || row.sourceRevision,
      platformSelection: selected ? toSelectionValue(selected) : "",
      matchMethod: row.matchMethod || "MANUAL_REFERENCE",
      matchConfidence: row.confidence || "LOW",
      reason: row.reason || "",
      evidence: row.evidence || [],
    });
    setDialogOpen(true);
  }, [platformClinicRows, platformDoctorRows]);

  const submitProposal = React.useCallback(async (draft: ProposalDraft) => {
    if (!auth.accessToken) return;
    setSaving(true);
    try {
      const body: ProviderConnectionsLinkProposalRequest = {
        publicProfileType: draft.kind,
        publicReference: draft.publicReference || null,
        publicPracticeReference: draft.publicPracticeReference,
        tenantReference: draft.tenantReference || null,
        platformClinicReference: draft.platformClinicReference || null,
        tenantDoctorUserReference: draft.tenantDoctorUserReference || null,
        tenantDoctorProfileReference: draft.tenantDoctorProfileReference || null,
        platformEntityRevision: draft.platformEntityRevision,
        sourceSystem: draft.sourceSystem || null,
        sourceEntityReference: draft.sourceEntityReference || null,
        sourceRevision: draft.sourceRevision,
        sourceUpdatedAt: draft.sourceUpdatedAt || null,
        linkStatus: "PROPOSED",
        connectionStatus: "CONNECTION_PENDING",
        matchMethod: draft.matchMethod,
        matchConfidence: draft.matchConfidence || null,
        reason: draft.reason || null,
        evidence: (draft.evidence.length ? draft.evidence : buildProposalEvidence(draft, draft.kind === "DOCTOR" ? platformDoctorRows.find((row) => toSelectionValue(row) === draft.platformSelection) || null : platformClinicRows.find((row) => toSelectionValue(row) === draft.platformSelection) || null)).map(evidenceSummary),
      };
      const result = await proposeProviderConnectionLink(auth.accessToken, body);
      setSelectedLinkReference(result.id);
      setSelectedLinkDetail(null);
      setSelectedLinkDetailError(null);
      setDialogOpen(false);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to propose link.");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, platformClinicRows, platformDoctorRows, refresh]);

  const submitSuggestionRejection = React.useCallback(async () => {
    if (!auth.accessToken || !rejectTarget) {
      return;
    }
    setSaving(true);
    try {
      await rejectProviderConnectionSuggestion(auth.accessToken, rejectTarget.id, { reason: rejectReason.trim() || "Rejected from console" });
      setRejectDialogOpen(false);
      setRejectTarget(null);
      setRejectReason("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject suggestion.");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, rejectReason, rejectTarget, refresh]);

  const openReviewCommand = React.useCallback((action: ReviewAction) => {
    setReviewCommandAction(action);
    setReviewCommandReason("");
    setReviewFindings([
      {
        section: "About",
        field: "description",
        category: "Description",
        severity: "Critical",
        required: true,
        blocking: true,
        providerActionRequired: true,
        reviewerNote: "",
        providerFacingMessage: "",
        internalNote: "",
      },
    ]);
  }, []);

  const updateReviewFinding = React.useCallback((index: number, patch: Partial<ReviewFindingDraft>) => {
    setReviewFindings((current) => current.map((finding, currentIndex) => (currentIndex === index ? { ...finding, ...patch } : finding)));
  }, []);

  const addReviewFinding = React.useCallback(() => {
    setReviewFindings((current) => [
      ...current,
      {
        section: "Other",
        field: "",
        category: "Observation",
        severity: "Warning",
        required: false,
        blocking: false,
        providerActionRequired: true,
        reviewerNote: "",
        providerFacingMessage: "",
        internalNote: "",
      },
    ]);
  }, []);

  const removeReviewFinding = React.useCallback((index: number) => {
    setReviewFindings((current) => (current.length <= 1 ? current : current.filter((_, currentIndex) => currentIndex !== index)));
  }, []);

  const closeReviewCommand = React.useCallback(() => {
    setReviewCommandAction(null);
    setReviewCommandReason("");
    setReviewFindings([]);
  }, []);

  const submitReviewCommand = React.useCallback(async () => {
    if (!auth.accessToken || !selectedReviewReference || !reviewCommandAction) {
      return;
    }
    setSaving(true);
    try {
      const reason = reviewCommandReason.trim() || null;
      switch (reviewCommandAction) {
        case "START_REVIEW":
          await startProviderConnectionsPublicProfileReview(auth.accessToken, selectedReviewReference, { reason });
          break;
        case "ADD_REVIEW_FINDING":
          await addFindingProviderConnectionsPublicProfileReview(auth.accessToken, selectedReviewReference, {
            expectedRevision: selectedReview?.moderationRevision,
            section: reviewFindings[0]?.section || "Other",
            category: reviewFindings[0]?.category || "Observation",
            severity: reviewFindings[0]?.severity || "Warning",
            field: reviewFindings[0]?.field || null,
            providerFacingMessage: reviewFindings[0]?.providerFacingMessage || reviewFindings[0]?.reviewerNote || "Review observation",
            internalNote: reviewFindings[0]?.internalNote || null,
            blocking: reviewFindings[0]?.blocking || false,
            providerActionRequired: reviewFindings[0]?.providerActionRequired ?? true,
          });
          break;
        case "REQUEST_CHANGES":
          await requestChangesProviderConnectionsPublicProfileReview(auth.accessToken, selectedReviewReference, {
            reason: reviewCommandReason.trim() || "Changes requested",
            findings: reviewFindings.map((finding) => ({
              section: finding.section,
              field: finding.field,
              category: finding.category,
              severity: finding.severity,
              required: finding.required || finding.blocking || finding.providerActionRequired,
              reviewerNote: finding.reviewerNote || finding.providerFacingMessage || finding.internalNote,
              providerFacingMessage: finding.providerFacingMessage,
              internalNote: finding.internalNote,
            })),
          });
          break;
        case "REJECT_SUBMISSION":
          await rejectProviderConnectionsPublicProfileReview(auth.accessToken, selectedReviewReference, { reason });
          break;
        case "APPROVE_SUBMISSION":
          await approveProviderConnectionsPublicProfileReview(auth.accessToken, selectedReviewReference, { reason });
          break;
        case "PUBLISH_PROFILE":
          await publishProviderConnectionsPublicProfileReview(auth.accessToken, selectedReviewReference, { reason });
          break;
        default:
          break;
      }
      closeReviewCommand();
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update public profile review.");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, closeReviewCommand, refresh, reviewCommandAction, reviewCommandReason, reviewFindings, selectedReview?.moderationRevision, selectedReview?.publicProfileReference, selectedReviewReference]);

  const renderReviewActionButton = React.useCallback((action: string, targetReference: string | null, targetUrl: string | null) => {
    if (!isReviewAction(action)) {
      return null;
    }
    const label = formatReviewAction(action);
    const permission = reviewActionPermission(action);
    if (!auth.hasPermission(permission)) {
      return null;
    }
    if (action === "VIEW_PUBLIC_PROFILE") {
      return targetUrl ? (
        <Button key={`${targetReference}-${action}`} size="small" variant="outlined" component={Link} to={targetUrl} target="_blank" rel="noreferrer">
          {label}
        </Button>
      ) : null;
    }
    if (action === "VIEW_SUBMISSION" || action === "VIEW_REVIEW_HISTORY") {
      return (
        <Button
          key={`${targetReference}-${action}`}
          size="small"
          variant="outlined"
          onClick={() => {
            if (targetReference) {
              setSelectedReview(null);
              setSelectedReviewReference(targetReference);
              navigate(reviewDetailPath(targetReference as string));
            }
          }}
        >
          {label}
        </Button>
      );
    }
    return (
      <Button
        key={`${targetReference}-${action}`}
        size="small"
        variant={reviewActionVariant(action)}
        color={reviewActionColor(action)}
        disabled={saving}
        onClick={() => {
          if (targetReference) {
            setSelectedReview(null);
            setSelectedReviewReference(targetReference);
            openReviewCommand(action);
          }
        }}
      >
        {label}
      </Button>
    );
  }, [auth, openReviewCommand, saving]);

  const selectedPublicProfile = selectedPublicProfileSnapshot || publicRows.find((item) => item.publicReference === selectedPublicProfileReference) || null;
  const selectedPlatformEntity = selectedPlatformEntityReference
    ? platformClinicRows.find((row) => toSelectionValue(row) === selectedPlatformEntityReference)
      || platformDoctorRows.find((row) => toSelectionValue(row) === selectedPlatformEntityReference)
      || null
    : null;
  const selectedSuggestion = selectedSuggestionKey ? suggestions.find((row) => row.id === selectedSuggestionKey) || null : null;
  const selectedLink = selectedLinkDetail?.link || linkRows.find((item) => item.id === selectedLinkReference) || null;
  const selectedPublicProfileOwnership = selectedPublicProfile?.publicReference
    ? ownershipRows.find((row) => row.publicProfileReference === selectedPublicProfile.publicReference) ?? null
    : null;
  const selectedPublicProfileLink = selectedPublicProfile?.publicReference
    ? linkRows.find((row) => row.publicReference === selectedPublicProfile.publicReference) ?? null
    : null;
  const selectedPublicPlatformClinic = selectedPublicProfileLink?.platformClinicReference
    ? platformClinicRows.find((row) => row.platformClinicReference === selectedPublicProfileLink.platformClinicReference) ?? null
    : null;
  const selectedPlatformEntityLink = selectedPlatformEntity
    ? linkRows.find((row) => (
      selectedPlatformEntity.entityType === "DOCTOR"
        ? row.tenantDoctorUserReference === selectedPlatformEntity.tenantDoctorUserReference
        : row.platformClinicReference === selectedPlatformEntity.platformClinicReference
    )) ?? null
    : null;
  const selectedOwnership = selectedOwnershipReference ? ownershipRows.find((row) => row.ownershipId === selectedOwnershipReference) || null : null;
  const selectedAuditEvent = selectedAuditEventReference ? auditRows.find((row) => row.id === selectedAuditEventReference) || null : null;
  const selectedConflict = selectedConflictReference ? conflicts.find((row) => row.id === selectedConflictReference) || null : null;

  const detailActions = selectedLink ? (
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
      {selectedLink.allowedActions.includes("VERIFY_LINK") ? (
        <Button variant="contained" onClick={async () => { if (auth.accessToken) { await approveProviderConnectionLink(auth.accessToken, selectedLink.id, "Approved from console"); await refresh(); } }}>
          Verify link
        </Button>
      ) : null}
      {selectedLink.allowedActions.includes("ACTIVATE_LINK") ? (
        <Button variant="contained" onClick={async () => { if (auth.accessToken) { await activateProviderConnectionLink(auth.accessToken, selectedLink.id, "Activated from console"); await refresh(); } }}>
          Activate
        </Button>
      ) : null}
      {selectedLink.allowedActions.includes("REJECT_LINK") ? (
        <Button color="error" variant="outlined" onClick={async () => { if (auth.accessToken) { await rejectProviderConnectionLink(auth.accessToken, selectedLink.id, "Rejected from console"); await refresh(); } }}>
          Reject
        </Button>
      ) : null}
      {selectedLink.allowedActions.includes("SUSPEND_LINK") ? (
        <Button variant="outlined" onClick={async () => { if (auth.accessToken) { await suspendProviderConnectionLink(auth.accessToken, selectedLink.id, "Suspended from console"); await refresh(); } }}>
          Suspend
        </Button>
      ) : null}
      {selectedLink.allowedActions.includes("RESUME_LINK") ? (
        <Button variant="outlined" onClick={async () => { if (auth.accessToken) { await resumeProviderConnectionLink(auth.accessToken, selectedLink.id, "Resumed from console"); await refresh(); } }}>
          Resume
        </Button>
      ) : null}
      {selectedLink.allowedActions.includes("DISCONNECT_LINK") ? (
        <Button variant="outlined" onClick={async () => { if (auth.accessToken) { await unlinkProviderConnectionLink(auth.accessToken, selectedLink.id, "Unlinked from console"); await refresh(); } }}>
          Disconnect
        </Button>
      ) : null}
      {selectedLink.allowedActions.includes("PROPOSE_LINK") ? (
        <Button variant="outlined" onClick={async () => { if (auth.accessToken) { await relinkProviderConnectionLink(auth.accessToken, selectedLink.id, "Relinked from console"); await refresh(); } }}>
          Propose again
        </Button>
      ) : null}
      {selectedLink.allowedActions.includes("RECONCILE_LINK") ? (
        <Button variant="outlined" onClick={async () => { if (auth.accessToken) { await reconcileProviderConnection(auth.accessToken, { publicProfileType: selectedLink.publicProfileType, linkId: selectedLink.id, tenantReference: selectedLink.tenantReference }); await refresh(); } }}>
          Reconcile
        </Button>
      ) : null}
    </Stack>
  ) : null;

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  return (
    <Stack spacing={2.5}>
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, alignItems: "center", justifyContent: "space-between" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>
            Provider Connections
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 900 }}>
            Review public provider profiles, inspect tenant facts, and manage platform connection lifecycles through the bounded Platform Admin workflow.
          </Typography>
        </Box>
        <Button startIcon={<RefreshRoundedIcon />} variant="outlined" onClick={() => void refresh()}>
          Refresh
        </Button>
      </Box>

      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        {SECTIONS.map((item) => (
          <Button
            key={item.key}
            component={Link}
            to={item.path}
            variant={section === item.key ? "contained" : "outlined"}
            sx={{ textTransform: "none" }}
          >
            {item.label}
          </Button>
        ))}
      </Stack>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Alert severity="info">Loading provider connection data…</Alert> : null}

      {section === "overview" ? (
        <Stack spacing={2.5}>
          <Grid container spacing={2}>
            {(overview?.metrics || []).map((metric) => (
              <Grid key={metric.key} size={{ xs: 12, sm: 6, md: 3 }}>
                <Card variant="outlined" sx={{ height: "100%" }}>
                  <CardContent>
                    <Stack spacing={0.75}>
                      <Chip size="small" color="primary" variant="outlined" label={metric.label} sx={{ alignSelf: "flex-start" }} />
                      <Typography variant="h4" sx={{ fontWeight: 900 }}>{metric.value}</Typography>
                      <Typography variant="body2" color="text.secondary">{metric.helperText}</Typography>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, lg: 8 }}>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack spacing={1.5}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <HistoryRoundedIcon fontSize="small" />
                    <Typography variant="h6" sx={{ fontWeight: 900 }}>Recent Provider Connection Activity</Typography>
                  </Stack>
                  {!auditRows.length ? (
                    <EmptyState
                      title="No recent activity yet"
                      description="Recent provider connection activity will appear here once the platform records new lifecycle events."
                      actionLabel="Refresh"
                      onAction={() => void refresh()}
                    />
                  ) : (
                    <Stack spacing={1}>
                      {auditRows.slice(0, 4).map((entry) => (
                        <Paper key={entry.id} variant="outlined" sx={{ p: 1.5 }}>
                          <Stack spacing={0.5}>
                            <Typography sx={{ fontWeight: 700 }}>{businessLabel(entry.action)}</Typography>
                            <Typography variant="body2" color="text.secondary">{entry.summary || "Activity recorded by the provider connection workflow."}</Typography>
                            <Typography variant="caption" color="text.secondary">{formatDateTime(entry.occurredAt)}</Typography>
                          </Stack>
                        </Paper>
                      ))}
                    </Stack>
                  )}
                </Stack>
              </Paper>
            </Grid>
            <Grid size={{ xs: 12, lg: 4 }}>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack spacing={1.5}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <FactCheckRoundedIcon fontSize="small" />
                    <Typography variant="h6" sx={{ fontWeight: 900 }}>Quick Actions</Typography>
                  </Stack>
                  <Stack spacing={1}>
                    <Button component={Link} to={sectionPath("suggestions")} variant="outlined">Review Suggestions</Button>
                    <Button component={Link} to={sectionPath("ownerships")} variant="outlined">Review Ownerships</Button>
                    <Button component={Link} to={sectionPath("public-profiles")} variant="outlined">Review Public Profiles</Button>
                    <Button component={Link} to={sectionPath("audit")} variant="outlined">Open Audit</Button>
                  </Stack>
                </Stack>
              </Paper>
            </Grid>
          </Grid>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={1} alignItems="center">
                <SearchRoundedIcon fontSize="small" />
                <Typography variant="h6" sx={{ fontWeight: 900 }}>Connection Health</Typography>
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {(overview?.metrics || []).slice(0, 6).map((metric) => (
                  <Chip key={metric.key} color="primary" variant="outlined" label={`${metric.label}: ${metric.value}`} />
                ))}
              </Stack>
            </Stack>
          </Paper>
        </Stack>
      ) : (
        <Paper variant="outlined" sx={{ p: 1.5 }}>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            {(overview?.metrics || []).slice(0, 4).map((metric) => (
              <Chip
                key={metric.key}
                color="primary"
                variant="outlined"
                label={`${metric.label}: ${metric.value}`}
                sx={{ alignSelf: "flex-start" }}
              />
            ))}
          </Stack>
        </Paper>
      )}

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={2}>
                <Stack spacing={0.5}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    {sectionTitle(section)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {section === "overview" ? "Review the highest priority connection queues." : "Search, filter, and select a row to inspect details."}
                  </Typography>
                </Stack>

                {section === "public-profiles" ? (
                  <Stack spacing={1.5}>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                      <TextField select label="Type" value={publicProfileType} onChange={(event) => setPublicProfileType(event.target.value as ProviderConnectionsPublicProfileType)} fullWidth>
                        <MenuItem value="CLINIC">Clinic</MenuItem>
                        <MenuItem value="DOCTOR">Doctor</MenuItem>
                        <MenuItem value="HOSPITAL">Hospital</MenuItem>
                      </TextField>
                      <TextField label="Search" value={publicProfileQuery} onChange={(event) => setPublicProfileQuery(event.target.value)} fullWidth />
                      <TextField label="City" value={publicProfileCity} onChange={(event) => setPublicProfileCity(event.target.value)} fullWidth />
                    </Stack>
                    <TableContainer component={Paper} variant="outlined">
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Profile</TableCell>
                            <TableCell>City</TableCell>
                            <TableCell>Capability</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell align="right">Actions</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {publicRows.map((row) => {
                            const selected = selectedPublicProfileReference === row.publicReference;
                            return (
                            <TableRow
                              key={`${row.publicProfileType}-${row.publicReference}-${row.publicPracticeReference || "profile"}`}
                              hover
                              selected={selected}
                              aria-selected={selected}
                              tabIndex={0}
                              role="button"
                              sx={{
                                cursor: "pointer",
                                ...(selected ? { backgroundColor: "action.selected" } : {}),
                              }}
                              onClick={() => {
                                if (row.publicReference) {
                                  setSelectedPublicProfileReference(row.publicReference);
                                  setSelectedPublicProfileSnapshot(row);
                                }
                              }}
                              onKeyDown={(event) => activateOnKeyboard(event, () => {
                                if (row.publicReference) {
                                  setSelectedPublicProfileReference(row.publicReference);
                                  setSelectedPublicProfileSnapshot(row);
                                }
                              })}
                            >
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.displayName || "Unnamed provider"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{formatProviderType(row.publicProfileType)} · {row.slug || "No slug"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.publicPath || "No public path"}</Typography>
                                  <TechnicalDetails>
                                    <Typography variant="body2" color="text.secondary">Reference</Typography>
                                    <Typography variant="body2">{row.publicReference || "Not assigned"}</Typography>
                                  </TechnicalDetails>
                                </Stack>
                              </TableCell>
                              <TableCell>{row.city || "—"}</TableCell>
                              <TableCell>
                                <Chip size="small" label={businessLabel(row.bookingCapability)} color={actionChipColor(row.bookingCapability)} variant="outlined" />
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={businessLabel(row.publicationStatus)} color={actionChipColor(row.publicationStatus)} variant="outlined" />
                              </TableCell>
                              <TableCell align="right">
                                <Stack direction="row" spacing={1} justifyContent="flex-end">
                                  <Button
                                    size="small"
                                    startIcon={<VisibilityRoundedIcon />}
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      if (row.publicReference) {
                                        setSelectedPublicProfileReference(row.publicReference);
                                        setSelectedPublicProfileSnapshot(row);
                                      }
                                    }}
                                  >
                                    Inspect
                                  </Button>
                                  {row.allowedActions.includes("PROPOSE_LINK") ? (
                                    <Button
                                      size="small"
                                      startIcon={<LinkRoundedIcon />}
                                      variant="contained"
                                      onClick={(event) => {
                                        event.stopPropagation();
                                        onOpenProposal(row);
                                      }}
                                    >
                                      Propose link
                                    </Button>
                                  ) : null}
                                </Stack>
                              </TableCell>
                            </TableRow>
                          );})}
                          {!publicRows.length ? (
                            <TableRow>
                              <TableCell colSpan={5}>
                                <EmptyState
                                  title="No Public Profiles"
                                  description="No public profiles matched the current filters. Public profiles appear here after Discover publishes a clinic, doctor, or hospital profile."
                                  actionLabel="Refresh"
                                  onAction={() => void refresh()}
                                />
                              </TableCell>
                            </TableRow>
                          ) : null}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </Stack>
                ) : null}

                {section === "public-profile-reviews" ? (
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, lg: 5 }}>
                      <Stack spacing={1.5}>
                        <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                          <TextField
                            select
                            label="Type"
                            value={reviewPublicType}
                            onChange={(event) => {
                              const next = new URLSearchParams(searchParams);
                              const value = parseReviewType(event.target.value);
                              if (value) {
                                next.set("type", value);
                              } else {
                                next.delete("type");
                              }
                              setSearchParams(next, { replace: true });
                            }}
                            fullWidth
                          >
                            <MenuItem value="">All</MenuItem>
                            <MenuItem value="CLINIC">Clinic</MenuItem>
                            <MenuItem value="DOCTOR">Doctor</MenuItem>
                            <MenuItem value="HOSPITAL">Hospital</MenuItem>
                          </TextField>
                          <TextField
                            label="Search"
                            value={reviewPublicQuery}
                            onChange={(event) => {
                              const next = new URLSearchParams(searchParams);
                              const value = event.target.value;
                              if (value.trim()) {
                                next.set("q", value);
                              } else {
                                next.delete("q");
                              }
                              setSearchParams(next, { replace: true });
                            }}
                            fullWidth
                          />
                          <TextField
                            label="City"
                            value={reviewPublicCity}
                            onChange={(event) => {
                              const next = new URLSearchParams(searchParams);
                              const value = event.target.value;
                              if (value.trim()) {
                                next.set("city", value);
                              } else {
                                next.delete("city");
                              }
                              setSearchParams(next, { replace: true });
                            }}
                            fullWidth
                          />
                        </Stack>
                        <Paper variant="outlined" sx={{ p: 1.5 }}>
                          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                            {[
                              { label: "Pending Review", count: reviewStatusCounts(reviewRows).submitted },
                              { label: "In Review", count: reviewStatusCounts(reviewRows).underReview },
                              { label: "Changes Requested", count: reviewStatusCounts(reviewRows).changesRequested },
                              { label: "Approved", count: reviewStatusCounts(reviewRows).approved },
                              { label: "Published", count: reviewStatusCounts(reviewRows).published },
                            ].map((item) => (
                              <Chip key={item.label} label={`${item.label}: ${item.count}`} color="primary" variant="outlined" />
                            ))}
                          </Stack>
                        </Paper>
                        <Stack spacing={1.25}>
                          {reviewRows.map((row) => {
                            const submissionReference = row.submissionReference || "";
                            if (!submissionReference) {
                              return null;
                            }
                            const selected = selectedReviewReference === row.submissionReference;
                            const primaryActionLabel = (row.moderationStatus || "").toUpperCase() === "UNDER_REVIEW" ? "Continue review" : "Open review";
                            return (
                              <Paper
                                key={submissionReference}
                                variant="outlined"
                                onClick={() => {
                                  setSelectedReviewReference(submissionReference);
                                  navigate(reviewDetailPath(submissionReference));
                                }}
                                onKeyDown={(event) => activateOnKeyboard(event, () => {
                                  setSelectedReviewReference(submissionReference);
                                  navigate(reviewDetailPath(submissionReference));
                                })}
                                tabIndex={0}
                                role="button"
                                sx={{
                                  p: 1.5,
                                  borderColor: selected ? "primary.main" : "divider",
                                  bgcolor: selected ? "action.selected" : "background.paper",
                                  cursor: "pointer",
                                }}
                              >
                                <Stack spacing={1}>
                                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1} useFlexGap flexWrap="wrap">
                                    <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                                      <Typography sx={{ fontWeight: 800 }}>{row.displayName || "Unnamed profile"}</Typography>
                                      <Typography variant="body2" color="text.secondary">{formatProviderType(row.publicProfileType)} · Version {row.submittedDraftVersion ?? 0}</Typography>
                                      <Typography variant="caption" color="text.secondary">{formatDateTime(row.submittedAt)}</Typography>
                                    </Stack>
                                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" justifyContent="flex-end">
                                      <Chip size="small" label={formatModerationStatus(row.moderationStatus)} color={actionChipColor(row.moderationStatus)} variant="outlined" />
                                      <Chip size="small" label={`${row.completenessPercentage}% Ready`} color={row.readinessStatus === "READY" ? "success" : "warning"} variant="outlined" />
                                    </Stack>
                                  </Stack>
                                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                                    <Chip size="small" label={`Reviewer: ${row.assignedReviewer || "Unassigned"}`} variant="outlined" />
                                    <Chip size="small" label={businessLabel(row.publicationStatus || "UNPUBLISHED")} variant="outlined" />
                                    <Chip size="small" label={businessLabel(row.tenantConsentStatus)} variant="outlined" />
                                    <Chip size="small" label={businessLabel(row.ownershipStatus)} variant="outlined" />
                                  </Stack>
                                  <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1} useFlexGap flexWrap="wrap">
                                    <Box sx={{ minWidth: 0 }}>
                                      <Typography variant="body2" color="text.secondary">{row.publicUrl || "No public URL yet"}</Typography>
                                      <Typography variant="caption" color="text.secondary">{row.publicProfileReference || "No public reference"}</Typography>
                                    </Box>
                                      <Button
                                        size="small"
                                        variant="outlined"
                                        onClick={(event) => {
                                          event.stopPropagation();
                                        void openReviewRow(row);
                                      }}
                                    >
                                      {primaryActionLabel}
                                    </Button>
                                  </Stack>
                                </Stack>
                              </Paper>
                            );
                          })}
                          {!reviewRows.length ? (
                            <Alert severity="info" variant="outlined">No profile review submissions matched the current filters.</Alert>
                          ) : null}
                        </Stack>
                      </Stack>
                    </Grid>
                    <Grid size={{ xs: 12, lg: 7 }}>
                      <Paper variant="outlined" sx={{ p: 2, position: { lg: "sticky" }, top: { lg: 24 } }}>
                        {selectedReview ? (() => {
                          const blockingCount = (selectedReview.findings || []).filter((finding) => (finding.severity || "").toUpperCase() === "BLOCKING" || finding.required).length;
                          const openCount = (selectedReview.findings || []).filter((finding) => (finding.resolutionStatus || "OPEN").toUpperCase() === "OPEN").length;
                          const readiness = reviewReadinessSnapshot(selectedReview);
                          return (
                            <Stack spacing={2}>
                              <Stack spacing={0.5}>
                                <Typography variant="h6" sx={{ fontWeight: 900 }}>{reviewText(selectedReview, "about", "displayName") || selectedReview.publicProfileReference || "Review detail"}</Typography>
                                <Typography variant="body2" color="text.secondary">{formatProviderType(selectedReview.publicProfileType)} · Submission Version {selectedReview.submittedDraftVersion}</Typography>
                                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                                  <Chip size="small" label={`Moderation: ${formatModerationStatus(selectedReview.moderationStatus)}`} color={actionChipColor(selectedReview.moderationStatus)} variant="outlined" />
                                  <Chip size="small" label={`Publication: ${formatModerationStatus(selectedReview.publicationStatusSnapshot)}`} variant="outlined" />
                                  <Chip size="small" label={`Consent: ${selectedReview.tenantConsentStatusSnapshot || "—"}`} variant="outlined" />
                                  <Chip size="small" label={`Ownership: ${reviewOwnershipStatus(selectedReview) || "—"}`} variant="outlined" />
                                  <Chip size="small" label={`Readiness: ${readiness.completenessPercentage == null ? "—" : `${readiness.completenessPercentage}% Ready`}`} color={readiness.readinessStatus === "READY" ? "success" : "warning"} variant="outlined" />
                                  <Chip size="small" label={`Reviewer: ${selectedReview.assignedReviewerDisplayName || selectedReview.assignedReviewerReference || selectedReview.assignedReviewerEmail || selectedReview.assignedReviewerId || "Unassigned"}`} variant="outlined" />
                                  <Chip size="small" label={`Review started: ${formatDateTime(selectedReview.assignedAt)}`} variant="outlined" />
                                </Stack>
                              </Stack>
                              <Alert severity="info" variant="outlined">
                                You are reviewing the exact profile version submitted by the Provider. Later draft changes will not affect this submission.
                              </Alert>
                              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                                <Chip size="small" label={`Open findings: ${openCount}`} variant="outlined" />
                                <Chip size="small" label={`Blocking findings: ${blockingCount}`} color={blockingCount ? "error" : "default"} variant="outlined" />
                              </Stack>
                              <PlatformPublicProfileReviewPreview review={selectedReview} />
                              {selectedReview.publicationHistory?.length ? (
                                <Paper variant="outlined" sx={{ p: 1.5 }}>
                                  <Stack spacing={1}>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Publication history</Typography>
                                    <Stack spacing={1}>
                                      {selectedReview.publicationHistory.map((entry, index) => {
                                        const hasPriorUnpublish = selectedReview.publicationHistory.slice(0, index).some((item) => (item.publicationStatus || "").toUpperCase() === "UNPUBLISHED");
                                        const label = (entry.publicationStatus || "").toUpperCase() === "UNPUBLISHED"
                                          ? "Unpublished"
                                          : hasPriorUnpublish
                                            ? "Republished"
                                            : "Published";
                                        return (
                                          <Paper key={entry.publicationReference} variant="outlined" sx={{ p: 1.25 }}>
                                            <Stack spacing={0.5}>
                                              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                                                <Chip size="small" label={label} variant="outlined" />
                                                <Chip size="small" label={`Status: ${formatModerationStatus(entry.publicationStatus)}`} variant="outlined" />
                                              </Stack>
                                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{entry.reason || "No reason provided"}</Typography>
                                              <Typography variant="body2" color="text.secondary">
                                                Actor: {(entry.publicationStatus || "").toUpperCase() === "UNPUBLISHED"
                                                  ? entry.unpublishedBy || entry.publishedBy || "—"
                                                  : entry.publishedBy || "—"}
                                              </Typography>
                                              <Typography variant="body2" color="text.secondary">Published: {formatDateTime(entry.publishedAt)}</Typography>
                                              {(entry.publicationStatus || "").toUpperCase() === "UNPUBLISHED" ? (
                                                <Typography variant="body2" color="text.secondary">Unpublished: {formatDateTime(entry.unpublishedAt)}</Typography>
                                              ) : null}
                                            </Stack>
                                          </Paper>
                                        );
                                      })}
                                    </Stack>
                                  </Stack>
                                </Paper>
                              ) : null}
                              <Paper variant="outlined" sx={{ p: 1.5 }}>
                                <Stack spacing={1}>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Action panel</Typography>
                                  <Typography variant="body2" color="text.secondary">Actions are rendered only from backend allowedActions.</Typography>
                                  {(() => {
                                    const moderationActions = (selectedReview.allowedActions || []).filter((action) => isReviewModerationAction(action));
                                    return (
                                      <Stack spacing={1.25}>
                                        {moderationActions.length ? (
                                          <Stack spacing={0.75}>
                                            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800, letterSpacing: 0.3, textTransform: "uppercase" }}>
                                              Review actions
                                            </Typography>
                                            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                                              {moderationActions.map((action) => renderReviewActionButton(action, selectedReview.submissionReference, selectedReview.publicUrl))}
                                            </Stack>
                                          </Stack>
                                        ) : null}
                                      </Stack>
                                    );
                                  })()}
                                </Stack>
                              </Paper>
                            </Stack>
                          );
                        })() : (
                          <Alert severity="info" variant="outlined">
                            Select a submission row to inspect the immutable submitted version.
                          </Alert>
                        )}
                      </Paper>
                    </Grid>
                  </Grid>
                ) : null}

                {section === "platform-entities" ? (
                  <Stack spacing={2}>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                      <TextField select label="Type" value={entityType} onChange={(event) => setEntityType(event.target.value)} fullWidth>
                        <MenuItem value="CLINIC">Clinic</MenuItem>
                        <MenuItem value="DOCTOR">Doctor</MenuItem>
                      </TextField>
                      <TextField label="Search" value={entityQuery} onChange={(event) => setEntityQuery(event.target.value)} fullWidth />
                    </Stack>
                    <TableContainer component={Paper} variant="outlined">
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Tenant entity</TableCell>
                            <TableCell>Context</TableCell>
                            <TableCell>Public listing</TableCell>
                            <TableCell>Connection</TableCell>
                            <TableCell>Capability</TableCell>
                            <TableCell>Availability</TableCell>
                            <TableCell align="right">Actions</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {(entityType === "DOCTOR" ? platformDoctorRows : platformClinicRows).map((row) => {
                            const selectionKey = toSelectionValue(row);
                            const selected = selectedPlatformEntityReference === selectionKey;
                            return (
                            <TableRow
                              key={`${row.entityType}-${row.tenantId}-${row.slug}`}
                              hover
                              selected={selected}
                              aria-selected={selected}
                              tabIndex={0}
                              role="button"
                              sx={{
                                cursor: "pointer",
                                ...(selected ? { backgroundColor: "action.selected" } : {}),
                              }}
                              onClick={() => setSelectedPlatformEntityReference(selectionKey)}
                              onKeyDown={(event) => activateOnKeyboard(event, () => setSelectedPlatformEntityReference(selectionKey))}
                            >
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.displayName || "Unnamed entity"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{formatProviderType(row.entityType)} · {row.slug || "No slug"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.publicListingConsent || "Unknown listing state"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Typography variant="body2">{row.tenantName || "—"}</Typography>
                                <Typography variant="caption" color="text.secondary">{row.city || "—"} {row.area ? `· ${row.area}` : ""}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {row.tenantDoctorUserReference ? `Doctor user ${row.tenantDoctorUserReference}` : `Tenant ${row.tenantCode || "—"}`}
                                </Typography>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={businessLabel(row.publicListingEnabled ? "ENABLED" : "DISABLED")} color={row.publicListingEnabled ? "success" : "default"} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{row.publicListingConsent || "—"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={businessLabel(row.linkStatus || "NOT_LINKED")} color={actionChipColor(row.linkStatus)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{businessLabel(row.connectionStatus || "NOT_CONNECTED")}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={businessLabel(row.currentDiscoverCapability || row.bookingCapability || "CALL_TO_BOOK")} color={actionChipColor(row.bookingCapability)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{row.platformBookingSetup || "—"}</Typography>
                                  {row.capabilityReason ? <Typography variant="caption" color="text.secondary">{row.capabilityReason}</Typography> : null}
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={businessLabel(row.currentAvailability || "UNKNOWN")} color={actionChipColor(row.currentAvailability)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">Revision {row.sourceRevision}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell align="right">
                                <Button size="small" onClick={(event) => {
                                  event.stopPropagation();
                                  const selected = row.entityType === "DOCTOR" ? platformDoctorRows.find((item) => item.tenantReference === row.tenantReference && item.platformClinicReference === row.platformClinicReference && item.tenantDoctorUserReference === row.tenantDoctorUserReference) : platformClinicRows.find((item) => item.tenantReference === row.tenantReference && item.platformClinicReference === row.platformClinicReference);
                                  if (selected) {
                                    setSelectedPlatformEntityReference(selectionKey);
                                    setProposalDraft({
                                      kind: row.entityType === "DOCTOR" ? "DOCTOR" : "CLINIC",
                                      publicProfile: null,
                                      suggestion: null,
                                      publicReference: "",
                                      publicPracticeReference: null,
                                      sourceSystem: "PLATFORM_ADMIN",
                                      sourceEntityReference: row.entityType === "DOCTOR" ? (row.tenantDoctorUserReference || "") : (row.platformClinicReference || ""),
                                      sourceRevision: row.sourceRevision,
                                      sourceUpdatedAt: null,
                                      tenantReference: selected.tenantReference || "",
                                      platformClinicReference: selected.platformClinicReference || "",
                                      tenantDoctorUserReference: selected.tenantDoctorUserReference || "",
                                      tenantDoctorProfileReference: selected.tenantDoctorProfileReference || "",
                                      platformEntityRevision: selected.sourceRevision,
                                      platformSelection: toSelectionValue(selected),
                                      matchMethod: "MANUAL_REFERENCE",
                                      matchConfidence: "LOW",
                                      reason: "",
                                      evidence: buildProposalEvidence({
                                        kind: row.entityType === "DOCTOR" ? "DOCTOR" : "CLINIC",
                                        publicProfile: null,
                                        suggestion: null,
                                        publicReference: "",
                                        publicPracticeReference: null,
                                        sourceSystem: "PLATFORM_ADMIN",
                                        sourceEntityReference: "",
                                        sourceRevision: row.sourceRevision,
                                        sourceUpdatedAt: null,
                                        tenantReference: selected.tenantReference || "",
                                        platformClinicReference: selected.platformClinicReference || "",
                                        tenantDoctorUserReference: selected.tenantDoctorUserReference || "",
                                        tenantDoctorProfileReference: selected.tenantDoctorProfileReference || "",
                                        platformEntityRevision: selected.sourceRevision,
                                        platformSelection: toSelectionValue(selected),
                                        matchMethod: "MANUAL_REFERENCE",
                                        matchConfidence: "LOW",
                                        reason: "",
                                        evidence: [],
                                      }, selected),
                                    });
                                    setDialogOpen(true);
                                  }
                                }}>
                                  Review match
                                </Button>
                              </TableCell>
                            </TableRow>
                            );
                          })}
                          {!(entityType === "DOCTOR" ? platformDoctorRows : platformClinicRows).length ? (
                            <TableRow>
                              <TableCell colSpan={7}>
                                <EmptyState
                                  title="No Operational Entities"
                                  description="No operational entities were found for the current filters. Entities appear after tenant provisioning."
                                  actionLabel="Refresh"
                                  onAction={() => void refresh()}
                                />
                              </TableCell>
                            </TableRow>
                          ) : null}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </Stack>
                ) : null}

                {section === "suggestions" ? (
                  <Stack spacing={2}>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                      <TextField label="Search suggestions" value={suggestionQuery} onChange={(event) => setSuggestionQuery(event.target.value)} fullWidth />
                      <Button variant="outlined" onClick={() => void refresh()}>
                        Refresh suggestions
                      </Button>
                    </Stack>
                    {suggestions.map((row) => {
                      const selected = selectedSuggestionKey === row.id;
                      return (
                      <Paper
                        key={row.id}
                        variant="outlined"
                        tabIndex={0}
                        role="button"
                        aria-selected={selected}
                        onClick={() => setSelectedSuggestionKey(row.id)}
                        onKeyDown={(event) => activateOnKeyboard(event, () => setSelectedSuggestionKey(row.id))}
                        sx={{
                          p: 2,
                          cursor: "pointer",
                          borderColor: selected ? "primary.main" : "divider",
                          bgcolor: selected ? "action.selected" : "background.paper",
                        }}
                      >
                        <Stack spacing={1}>
                          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                            <Stack spacing={0.25}>
                              <Typography sx={{ fontWeight: 700 }}>{row.publicDisplayName || "Suggested provider"}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {formatProviderType(row.publicProfileType)} · {row.platformCity || "—"}{row.platformArea ? ` · ${row.platformArea}` : ""}
                              </Typography>
                            </Stack>
                            <Chip size="small" label={businessLabel(row.status || "SUGGESTED")} color="warning" variant="outlined" />
                          </Stack>
                          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                            <Paper variant="outlined" sx={{ p: 1.25, flex: 1 }}>
                              <Typography variant="caption" color="text.secondary">Suggested platform entity</Typography>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.platformDisplayName || "—"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.tenantReference || "—"} · {row.platformClinicReference || "—"}</Typography>
                            </Paper>
                            <Paper variant="outlined" sx={{ p: 1.25, flex: 1 }}>
                              <Typography variant="caption" color="text.secondary">Confidence</Typography>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{businessLabel(row.confidence || "LOW")}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.lastEvaluatedAt ? `Last evaluated ${formatDateTime(row.lastEvaluatedAt.toString())}` : "No evaluation timestamp"}</Typography>
                            </Paper>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">{row.reason || "Suggested by matching evidence."}</Typography>
                          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                            {(row.evidence || []).map((item) => (
                              <Chip key={evidenceSummary(item)} size="small" label={evidenceSummary(item)} color={evidenceTone(item.strength)} variant="outlined" />
                            ))}
                          </Stack>
                          <Stack direction="row" spacing={1}>
                            <Button size="small" variant="contained" onClick={(event) => {
                              event.stopPropagation();
                              reviewSuggestion(row);
                            }}>
                              Review match
                            </Button>
                            <Button size="small" variant="outlined" onClick={(event) => {
                              event.stopPropagation();
                              setSelectedSuggestionKey(row.id);
                              setRejectTarget(row);
                              setRejectReason("");
                              setRejectDialogOpen(true);
                            }}>
                              Reject suggestion
                            </Button>
                          </Stack>
                        </Stack>
                      </Paper>
                      );
                    })}
                    {!suggestions.length ? (
                      <EmptyState
                        title="No Suggested Matches"
                        description="Automatic matching periodically compares public profiles with operational clinics. New suggestions will appear here."
                        actionLabel="Refresh"
                        onAction={() => void refresh()}
                      />
                    ) : null}
                  </Stack>
                ) : null}

                {section === "links" ? (
                  <Stack spacing={2}>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                      <TextField label="Type" value={linkType} onChange={(event) => setLinkType(event.target.value)} fullWidth />
                      <TextField label="Status" value={linkStatus} onChange={(event) => setLinkStatus(event.target.value)} fullWidth />
                      <TextField label="Search" value={linkQuery} onChange={(event) => setLinkQuery(event.target.value)} fullWidth />
                    </Stack>
                    <TableContainer component={Paper} variant="outlined">
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Public</TableCell>
                            <TableCell>Operational Clinic</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Capability</TableCell>
                            <TableCell>Updated</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {linkRows.map((row) => {
                            const selected = selectedLinkReference === row.id;
                            return (
                            <TableRow
                              key={row.id}
                              hover
                              selected={selected}
                              aria-selected={selected}
                              tabIndex={0}
                              role="button"
                              onClick={() => {
                                setSelectedLinkReference(row.id);
                                setSelectedLinkDetail(null);
                                setSelectedLinkDetailError(null);
                              }}
                              onKeyDown={(event) => activateOnKeyboard(event, () => {
                                setSelectedLinkReference(row.id);
                                setSelectedLinkDetail(null);
                                setSelectedLinkDetailError(null);
                              })}
                              sx={{ cursor: "pointer", ...(selected ? { backgroundColor: "action.selected" } : {}) }}
                            >
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.publicDisplayName || row.publicReference || "Unnamed link"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.publicCity || "—"}{row.publicArea ? ` · ${row.publicArea}` : ""}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Typography variant="body2">{row.tenantName || "—"}</Typography>
                                <Typography variant="caption" color="text.secondary">{row.publicCity || "—"}{row.publicArea ? ` · ${row.publicArea}` : ""}</Typography>
                                <TechnicalDetails>
                                  <Typography variant="body2" color="text.secondary">Platform Clinic Reference</Typography>
                                  <Typography variant="body2">{row.platformClinicReference || "—"}</Typography>
                                </TechnicalDetails>
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={businessLabel(row.linkStatus)} color={actionChipColor(row.linkStatus)} variant="outlined" />
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={businessLabel(row.bookingCapability)} color={actionChipColor(row.bookingCapability)} variant="outlined" />
                              </TableCell>
                              <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
                              <TableCell align="right">
                                <Button
                                  size="small"
                                  variant="outlined"
                                  startIcon={<VisibilityRoundedIcon />}
                                  onClick={(event) => {
                                    event.stopPropagation();
                                    setSelectedLinkReference(row.id);
                                    setSelectedLinkDetail(null);
                                    setSelectedLinkDetailError(null);
                                  }}
                                >
                                  Inspect
                                </Button>
                              </TableCell>
                            </TableRow>
                            );
                          })}
                        </TableBody>
                        {!linkRows.length ? (
                          <TableRow>
                            <TableCell colSpan={6}>
                              <EmptyState
                                title="No Links"
                                description="No provider connection links matched the current filters."
                                actionLabel="Refresh"
                                onAction={() => void refresh()}
                              />
                            </TableCell>
                          </TableRow>
                        ) : null}
                      </Table>
                    </TableContainer>
                  </Stack>
                ) : null}

                {section === "ownerships" ? (
                  <Stack spacing={2}>
                    <Typography variant="body2" color="text.secondary">
                      Review provider ownership and platform review state separately from link lifecycle.
                    </Typography>
                    <TableContainer component={Paper} variant="outlined">
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Profile</TableCell>
                            <TableCell>Ownership</TableCell>
                            <TableCell>Connection</TableCell>
                            <TableCell>Memberships</TableCell>
                            <TableCell>Updated</TableCell>
                            <TableCell align="right">Actions</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {ownershipRows.map((row) => {
                            const selected = selectedOwnershipReference === row.ownershipId;
                            return (
                            <TableRow
                              key={row.ownershipId}
                              hover
                              selected={selected}
                              aria-selected={selected}
                              tabIndex={0}
                              role="button"
                              onClick={() => setSelectedOwnershipReference(row.ownershipId)}
                              onKeyDown={(event) => activateOnKeyboard(event, () => setSelectedOwnershipReference(row.ownershipId))}
                              sx={{ cursor: "pointer", ...(selected ? { backgroundColor: "action.selected" } : {}) }}
                            >
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.displayName || "Unnamed profile"}</Typography>
                                <Typography variant="caption" color="text.secondary">{formatProviderType(row.publicProfileType)} · {row.publicProfileReference || "—"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.city || "—"}{row.area ? ` · ${row.area}` : ""}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={businessLabel(row.ownershipStatus || "UNCLAIMED")} color={actionChipColor(row.ownershipStatus)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{businessLabel(row.ownershipMethod)}</Typography>
                                  <Typography variant="caption" color="text.secondary">{businessLabel(row.consentState)}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={businessLabel(row.platformConnectionStatus || "NOT_CONNECTED")} color={actionChipColor(row.platformConnectionStatus)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{businessLabel(row.bookingCapability || "NOT_AVAILABLE")}</Typography>
                                  {row.maskedProviderMobile ? <Typography variant="caption" color="text.secondary">Mobile ending {row.maskedProviderMobile.slice(-4)}</Typography> : null}
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  {(row.membershipRoles || []).map((membership) => (
                                    <Chip key={membership} size="small" label={businessLabel(membership)} variant="outlined" />
                                  ))}
                                </Stack>
                              </TableCell>
                              <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
                              <TableCell align="right">
                                <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                                  {(row.allowedActions || []).map((action) => {
                                    const button = renderOwnershipAction(row, action);
                                    return button ? <React.Fragment key={`${row.ownershipId}-${action}`}>{button}</React.Fragment> : null;
                                  })}
                                </Stack>
                              </TableCell>
                            </TableRow>
                            );
                          })}
                          {!ownershipRows.length ? (
                            <TableRow>
                              <TableCell colSpan={6}>
                                <EmptyState
                                  title="No Ownership Records"
                                  description="Ownership records are created after successful verification."
                                  actionLabel="Refresh"
                                  onAction={() => void refresh()}
                                />
                              </TableCell>
                            </TableRow>
                          ) : null}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </Stack>
                ) : null}

                {section === "conflicts" ? (
                  <Stack spacing={1.5}>
                    {conflicts.map((row) => {
                      const selected = selectedConflictReference === row.id;
                      return (
                      <Paper
                        key={row.id}
                        variant="outlined"
                        tabIndex={0}
                        role="button"
                        aria-selected={selected}
                        onClick={() => setSelectedConflictReference(row.id)}
                        onKeyDown={(event) => activateOnKeyboard(event, () => setSelectedConflictReference(row.id))}
                        sx={{
                          p: 2,
                          cursor: "pointer",
                          borderColor: selected ? "primary.main" : "divider",
                          bgcolor: selected ? "action.selected" : "background.paper",
                        }}
                      >
                        <Stack spacing={0.5}>
                          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                            <Typography sx={{ fontWeight: 700 }}>{row.title}</Typography>
                            <Chip size="small" label={businessLabel(row.severity)} color="error" variant="outlined" />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">{row.details}</Typography>
                        </Stack>
                      </Paper>
                      );
                    })}
                    {!conflicts.length ? (
                      <EmptyState
                        title="No Provider Connection Conflicts"
                        description="All provider connections are currently consistent. Conflicts automatically appear when multiple operational clinics match one public profile, ownership verification fails, duplicate ownership is detected, link lifecycle becomes inconsistent, or manual Platform Admin review is required."
                        actionLabel="Refresh"
                        onAction={() => void refresh()}
                      />
                    ) : null}
                  </Stack>
                ) : null}

                {section === "audit" ? (
                  <Stack spacing={1.5}>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                      <TextField label="Action" value={auditAction} onChange={(event) => setAuditAction(event.target.value)} fullWidth />
                      <TextField label="Tenant" value={auditTenant} onChange={(event) => setAuditTenant(event.target.value)} fullWidth />
                      <TextField label="Provider type" value={auditProviderType} onChange={(event) => setAuditProviderType(event.target.value)} fullWidth />
                      <TextField label="Result" value={auditResult} onChange={(event) => setAuditResult(event.target.value)} fullWidth />
                      <TextField label="Search" value={auditQuery} onChange={(event) => setAuditQuery(event.target.value)} fullWidth />
                    </Stack>
                    <Stack direction="row" spacing={1}>
                      <Button variant="outlined" onClick={() => void refresh()}>Refresh audit</Button>
                    </Stack>
                    {!auditRows.length ? (
                      <EmptyState
                        title={auditAction || auditTenant || auditProviderType || auditResult || auditQuery ? "No Matching Audit Events" : "No Audit Events Today"}
                        description={auditAction || auditTenant || auditProviderType || auditResult || auditQuery
                          ? "No audit events match the current filters. Try adjusting filters or refreshing."
                          : "No provider connection activity has been recorded yet."}
                        actionLabel="Refresh"
                        onAction={() => void refresh()}
                      />
                    ) : (
                      <TableContainer component={Paper} variant="outlined">
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Timestamp</TableCell>
                              <TableCell>Provider</TableCell>
                              <TableCell>Tenant</TableCell>
                              <TableCell>Action</TableCell>
                              <TableCell>State</TableCell>
                              <TableCell>Result</TableCell>
                              <TableCell align="right">Actions</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {auditRows.map((entry) => {
                              const selected = selectedAuditEventReference === entry.id;
                              return (
                              <TableRow
                                key={entry.id}
                                hover
                                selected={selected}
                                aria-selected={selected}
                                tabIndex={0}
                                role="button"
                                onClick={() => setSelectedAuditEventReference(entry.id)}
                                onKeyDown={(event) => activateOnKeyboard(event, () => setSelectedAuditEventReference(entry.id))}
                                sx={{ cursor: "pointer", ...(selected ? { backgroundColor: "action.selected" } : {}) }}
                              >
                                <TableCell>{formatDateTime(entry.occurredAt)}</TableCell>
                                <TableCell>
                                  <Stack spacing={0.25}>
                                    <Typography sx={{ fontWeight: 700 }}>{formatProviderType(entry.providerType)}</Typography>
                                    <Typography variant="caption" color="text.secondary">{entry.platformClinicReference || "—"}</Typography>
                                  </Stack>
                                </TableCell>
                                <TableCell>{entry.tenantReference || "—"}</TableCell>
                                <TableCell>{entry.action}</TableCell>
                                <TableCell>{entry.previousState || "—"} → {entry.newState || "—"}</TableCell>
                                <TableCell>{entry.result || "—"}</TableCell>
                                <TableCell align="right">
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      setSelectedAuditEventReference(entry.id);
                                    }}
                                  >
                                    Inspect
                                  </Button>
                                </TableCell>
                              </TableRow>
                              );
                            })}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                    {selectedLinkDetail?.audit?.length ? (
                      <Stack spacing={1}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Selected link history</Typography>
                        {selectedLinkDetail.audit.map((entry) => (
                          <Paper key={entry.id} variant="outlined" sx={{ p: 2 }}>
                            <Stack spacing={0.5}>
                              <Typography sx={{ fontWeight: 700 }}>{entry.action}</Typography>
                              <Typography variant="body2" color="text.secondary">{entry.summary || "Audit event"}</Typography>
                              <Typography variant="caption" color="text.secondary">{formatDateTime(entry.occurredAt)}</Typography>
                            </Stack>
                          </Paper>
                        ))}
                      </Stack>
                    ) : null}
                  </Stack>
                ) : null}

                {section === "overview" ? (
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <AddLinkRoundedIcon fontSize="small" />
                            <Typography variant="h6" sx={{ fontWeight: 900 }}>Public profiles</Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">Search published clinics, doctors, and hospitals from Discover.</Typography>
                          <Button component={Link} to={sectionPath("public-profiles")} variant="contained">Open workspace</Button>
                        </Stack>
                      </Paper>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <VisibilityRoundedIcon fontSize="small" />
                            <Typography variant="h6" sx={{ fontWeight: 900 }}>Public profile reviews</Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">Review immutable submissions, moderation decisions, and publication state.</Typography>
                          <Button component={Link} to={sectionPath("public-profile-reviews")} variant="contained">Open review workspace</Button>
                        </Stack>
                      </Paper>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <LinkRoundedIcon fontSize="small" />
                            <Typography variant="h6" sx={{ fontWeight: 900 }}>Links</Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">Review proposed, approved, linked, and disconnected provider links.</Typography>
                          <Button component={Link} to={sectionPath("links")} variant="contained">Open workspace</Button>
                        </Stack>
                      </Paper>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <SearchRoundedIcon fontSize="small" />
                            <Typography variant="h6" sx={{ fontWeight: 900 }}>Platform entities</Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">Inspect Healthcare tenant clinics and tenant-scoped doctors in platform context.</Typography>
                          <Button component={Link} to={sectionPath("platform-entities")} variant="contained">Open workspace</Button>
                        </Stack>
                      </Paper>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <VisibilityRoundedIcon fontSize="small" />
                            <Typography variant="h6" sx={{ fontWeight: 900 }}>Conflicts / audit</Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">Review exceptions, disputes, and immutable change history.</Typography>
                          <Button component={Link} to={sectionPath("conflicts")} variant="outlined">Open conflicts</Button>
                        </Stack>
                      </Paper>
                    </Grid>
                  </Grid>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        {section !== "public-profile-reviews" ? (
          <Grid size={{ xs: 12, lg: 4 }}>
            <Paper variant="outlined" sx={{ p: 2.25, position: "sticky", top: 24 }}>
              <Stack spacing={2}>
                <Stack spacing={0.5}>
                  {section === "public-profiles" ? (
                    <>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Public Profile Details</Typography>
                      <Typography variant="body2" color="text.secondary">Pick a public profile to inspect its publication, ownership, connection and capability information.</Typography>
                    </>
                  ) : section === "platform-entities" ? (
                    <>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Platform Entity Details</Typography>
                      <Typography variant="body2" color="text.secondary">Pick a platform entity to inspect its operational, publication and connection state.</Typography>
                    </>
                  ) : section === "suggestions" ? (
                    <>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Suggestion Details</Typography>
                      <Typography variant="body2" color="text.secondary">Select a suggested match to review the candidate, matching evidence and available actions.</Typography>
                    </>
                  ) : section === "links" ? (
                    <>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Link Details</Typography>
                      <Typography variant="body2" color="text.secondary">Pick a link to inspect its lifecycle, capability, comparison evidence and available actions.</Typography>
                    </>
                  ) : section === "ownerships" ? (
                    <>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Ownership Details</Typography>
                      <Typography variant="body2" color="text.secondary">Inspect the selected ownership record and backend-authorized actions.</Typography>
                    </>
                  ) : section === "audit" ? (
                    <>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Audit Event Details</Typography>
                      <Typography variant="body2" color="text.secondary">Select an audit event to inspect the lifecycle transition, actor and request context.</Typography>
                    </>
                  ) : section === "conflicts" ? (
                    <>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Conflict Details</Typography>
                      <Typography variant="body2" color="text.secondary">Inspect the selected mismatch or blockage.</Typography>
                    </>
                  ) : null}
                </Stack>
                {section === "public-profiles" ? (
                  <PublicProfileInspectionPanel
                    loading={loading}
                    error={error}
                    selectedProfile={selectedPublicProfile}
                    selectedOwnership={selectedPublicProfileOwnership}
                    selectedLink={selectedPublicProfileLink}
                    selectedPlatformClinic={selectedPublicPlatformClinic}
                    onProposeLink={onOpenProposal}
                  />
                ) : section === "platform-entities" ? (
                  <PlatformEntityInspector
                    loading={loading}
                    error={error}
                    selectedEntity={selectedPlatformEntity}
                    linkedProfile={selectedPlatformEntityLink?.publicReference ? publicRows.find((row) => row.publicReference === selectedPlatformEntityLink?.publicReference) ?? null : null}
                    linkedPlatformLink={selectedPlatformEntityLink}
                    onReviewMatch={(entity) => {
                      const selected = entity.entityType === "DOCTOR"
                        ? platformDoctorRows.find((item) => item.tenantReference === entity.tenantReference && item.platformClinicReference === entity.platformClinicReference && item.tenantDoctorUserReference === entity.tenantDoctorUserReference)
                        : platformClinicRows.find((item) => item.tenantReference === entity.tenantReference && item.platformClinicReference === entity.platformClinicReference);
                      if (!selected) {
                        return;
                      }
                      setSelectedPlatformEntityReference(toSelectionValue(selected));
                      setProposalDraft({
                        kind: entity.entityType === "DOCTOR" ? "DOCTOR" : "CLINIC",
                        publicProfile: null,
                        suggestion: null,
                        publicReference: "",
                        publicPracticeReference: null,
                        sourceSystem: "PLATFORM_ADMIN",
                        sourceEntityReference: entity.entityType === "DOCTOR" ? (entity.tenantDoctorUserReference || "") : (entity.platformClinicReference || ""),
                        sourceRevision: entity.sourceRevision,
                        sourceUpdatedAt: null,
                        tenantReference: selected.tenantReference || "",
                        platformClinicReference: selected.platformClinicReference || "",
                        tenantDoctorUserReference: selected.tenantDoctorUserReference || "",
                        tenantDoctorProfileReference: selected.tenantDoctorProfileReference || "",
                        platformEntityRevision: selected.sourceRevision,
                        platformSelection: toSelectionValue(selected),
                        matchMethod: "MANUAL_REFERENCE",
                        matchConfidence: "LOW",
                        reason: "",
                        evidence: buildProposalEvidence({
                          kind: entity.entityType === "DOCTOR" ? "DOCTOR" : "CLINIC",
                          publicProfile: null,
                          suggestion: null,
                          publicReference: "",
                          publicPracticeReference: null,
                          sourceSystem: "PLATFORM_ADMIN",
                          sourceEntityReference: "",
                          sourceRevision: entity.sourceRevision,
                          sourceUpdatedAt: null,
                          tenantReference: selected.tenantReference || "",
                          platformClinicReference: selected.platformClinicReference || "",
                          tenantDoctorUserReference: selected.tenantDoctorUserReference || "",
                          tenantDoctorProfileReference: selected.tenantDoctorProfileReference || "",
                          platformEntityRevision: selected.sourceRevision,
                          platformSelection: toSelectionValue(selected),
                          matchMethod: "MANUAL_REFERENCE",
                          matchConfidence: "LOW",
                          reason: "",
                          evidence: [],
                        }, selected),
                      });
                      setDialogOpen(true);
                    }}
                  />
                ) : section === "suggestions" ? (
                  <SuggestionInspector
                    loading={loading}
                    error={error}
                    selectedSuggestion={selectedSuggestion}
                    onReviewMatch={(suggestion) => reviewSuggestion(suggestion)}
                    onReject={(suggestion) => { setRejectTarget(suggestion); setRejectReason(""); setRejectDialogOpen(true); }}
                  />
                ) : section === "links" ? (
                  <LinkInspector
                    loading={loading}
                    error={selectedLinkDetailError || error}
                    selectedLink={selectedLink}
                    selectedLinkDetail={selectedLinkDetail}
                    onRetry={() => void refresh()}
                    allowedActions={detailActions}
                  />
                ) : section === "ownerships" ? (
                  <OwnershipInspector selectedOwnership={selectedOwnership} />
                ) : section === "audit" ? (
                  <AuditEventInspector loading={loading} error={error} selectedAuditEvent={selectedAuditEvent} />
                ) : section === "conflicts" ? (
                  <ConflictInspector selectedConflict={selectedConflict} />
                ) : null}
              </Stack>
            </Paper>
          </Grid>
        ) : null}
      </Grid>

      <ProposalDialog
        open={dialogOpen}
        draft={proposalDraft}
        clinicOptions={platformClinicRows}
        doctorOptions={platformDoctorRows}
        onClose={() => setDialogOpen(false)}
        onSubmit={submitProposal}
      />
      <ReviewCommandDialog
        open={Boolean(reviewCommandAction)}
        action={reviewCommandAction}
        review={selectedReview}
        reason={reviewCommandReason}
        findings={reviewFindings}
        onClose={closeReviewCommand}
        onReasonChange={setReviewCommandReason}
        onFindingsChange={updateReviewFinding}
        onAddFinding={addReviewFinding}
        onRemoveFinding={removeReviewFinding}
        onSubmit={() => void submitReviewCommand()}
        saving={saving}
      />

      <Dialog open={Boolean(selectedOwnership)} onClose={() => setSelectedOwnershipReference(null)} fullWidth maxWidth="sm">
        <DialogTitle>Ownership details</DialogTitle>
        <DialogContent dividers>
          {selectedOwnership ? (
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Alert severity="info" variant="outlined">
                Ownership actions are controlled by the backend `allowedActions` list for this row.
              </Alert>
              <Stack spacing={0.75}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{selectedOwnership.displayName || "Unnamed profile"}</Typography>
                <Typography variant="body2" color="text.secondary">{formatProviderType(selectedOwnership.publicProfileType)} · {selectedOwnership.publicProfileReference || "—"}</Typography>
                <Typography variant="body2" color="text.secondary">{selectedOwnership.city || "—"}{selectedOwnership.area ? ` · ${selectedOwnership.area}` : ""}</Typography>
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Chip size="small" label={businessLabel(selectedOwnership.ownershipStatus || "UNCLAIMED")} color={actionChipColor(selectedOwnership.ownershipStatus)} variant="outlined" />
                <Chip size="small" label={businessLabel(selectedOwnership.platformConnectionStatus || "NOT_CONNECTED")} color={actionChipColor(selectedOwnership.platformConnectionStatus)} variant="outlined" />
                <Chip size="small" label={businessLabel(selectedOwnership.bookingCapability || "NOT_AVAILABLE")} color={actionChipColor(selectedOwnership.bookingCapability)} variant="outlined" />
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {(selectedOwnership.allowedActions || []).map((action) => (
                  <Chip key={`selected-ownership-${selectedOwnership.ownershipId}-${action}`} size="small" label={ownershipActionLabel(action as OwnershipAction)} variant="outlined" />
                ))}
              </Stack>
              <Stack spacing={0.75}>
                <Typography variant="caption" color="text.secondary">Memberships</Typography>
                <Typography variant="body2">{(selectedOwnership.membershipRoles || []).join(", ") || "—"}</Typography>
                <Typography variant="caption" color="text.secondary">Disputes</Typography>
                <Typography variant="body2">{(selectedOwnership.disputeStatuses || []).join(", ") || "—"}</Typography>
                <Typography variant="caption" color="text.secondary">Updated</Typography>
                <Typography variant="body2">{formatDateTime(selectedOwnership.updatedAt)}</Typography>
              </Stack>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelectedOwnershipReference(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Reject suggestion</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="warning" variant="outlined">
              Rejecting a suggestion prevents immediate reuse until source facts change.
            </Alert>
            <Typography variant="body2" color="text.secondary">
              {rejectTarget?.publicDisplayName || "Selected suggestion"} · {rejectTarget?.platformDisplayName || "No platform entity"}
            </Typography>
            <TextField
              label="Reason"
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              fullWidth
              multiline
              minRows={3}
              helperText="Explain why the suggestion should not be reused as-is."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)}>Cancel</Button>
          <Button color="error" variant="contained" onClick={() => void submitSuggestionRejection()} disabled={!rejectTarget}>
            Reject suggestion
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
