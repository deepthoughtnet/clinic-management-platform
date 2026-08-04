import * as React from "react";
import { Link, useLocation } from "react-router-dom";
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
  Typography,
} from "@mui/material";
import AddLinkRoundedIcon from "@mui/icons-material/AddLinkRounded";
import LinkRoundedIcon from "@mui/icons-material/LinkRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import VisibilityRoundedIcon from "@mui/icons-material/VisibilityRounded";

import { useAuth } from "../../auth/useAuth";
import {
  activateProviderConnectionLink,
  approveProviderConnectionLink,
  approveProviderConnectionOwnership,
  getProviderConnectionsLinkDetail,
  getProviderConnectionsAuditEvents,
  getProviderConnectionsOverview,
  listProviderConnectionsConflicts,
  listProviderConnectionsLinks,
  listProviderConnectionsOwnerships,
  listProviderConnectionsPublicProfileLifecycle,
  listProviderConnectionsPlatformEntities,
  listProviderConnectionsPublicPractices,
  listProviderConnectionsPublicProfiles,
  listProviderConnectionsSuggestions,
  proposeProviderConnectionLink,
  rejectProviderConnectionSuggestion,
  rejectProviderConnectionOwnership,
  disputeProviderConnectionOwnership,
  revokeProviderConnectionOwnership,
  reconcileProviderConnection,
  relinkProviderConnectionLink,
  unlinkProviderConnectionLink,
  type ProviderConnectionsConflictResponse,
  type ProviderConnectionsLinkDetailResponse,
  type ProviderConnectionsLinkProposalRequest,
  type ProviderConnectionsLinkResponse,
  type ProviderConnectionsLifecycleResponse,
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
} from "../../api/clinicApi";

type ConsoleSection =
  | "overview"
  | "public-profiles"
  | "public-profile-lifecycle"
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

const SECTIONS: Array<{ key: ConsoleSection; label: string; path: string }> = [
  { key: "overview", label: "Overview", path: "/platform/provider-connections" },
  { key: "public-profiles", label: "Public Profiles", path: "/platform/provider-connections/public-profiles" },
  { key: "public-profile-lifecycle", label: "Public Profile Lifecycle", path: "/platform/provider-connections/public-profile-lifecycle" },
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

function activeSection(pathname: string): ConsoleSection {
  const entry = SECTIONS.find((section) => section.path === pathname);
  return entry?.key || "overview";
}

function actionChipColor(status: string | null | undefined) {
  const normalized = (status || "").toUpperCase();
  if (["LINKED", "CONNECTED", "ONLINE_BOOKING", "ENABLED", "READY", "AVAILABLE_TODAY", "NEXT_AVAILABLE"].includes(normalized)) return "success" as const;
  if (["APPROVED", "PROPOSED", "PENDING_VERIFICATION", "REQUEST_APPOINTMENT"].includes(normalized)) return "warning" as const;
  if (["DISPUTED", "REJECTED", "FAILED", "CONFLICT", "INACTIVE", "UNAVAILABLE", "NOT_LINKED", "NOT_CONNECTED", "DISABLED"].includes(normalized)) return "error" as const;
  return "default" as const;
}

function sectionPath(section: ConsoleSection) {
  return SECTIONS.find((item) => item.key === section)?.path || SECTIONS[0].path;
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
    case "public-profile-lifecycle":
      return "Public Profile Lifecycle";
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
    ? `${selectedPlatform.active ? "Active" : "Inactive"} · ${selectedPlatform.publicListingEnabled ? "Public listing enabled" : "Public listing disabled"}`
    : current.suggestion?.status || "—";
  const selectedPlatformCapability = selectedPlatform?.currentDiscoverCapability || current.suggestion?.currentDiscoverCapability || "—";
  const selectedPlatformAvailability = selectedPlatform?.currentAvailability || current.suggestion?.currentAvailability || "—";
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
                <TextField label="Match method" value={current.matchMethod.replaceAll("_", " ")} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="Confidence" value={current.matchConfidence} InputProps={{ readOnly: true }} fullWidth />
              </Stack>
            </Stack>
          </Paper>

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

export default function ProviderConnectionsPage() {
  const auth = useAuth();
  const location = useLocation();

  const section = activeSection(location.pathname);
  const [overview, setOverview] = React.useState<ProviderConnectionsOverviewResponse | null>(null);
  const [publicRows, setPublicRows] = React.useState<ProviderConnectionsPublicProfileResponse[]>([]);
  const [lifecycleRows, setLifecycleRows] = React.useState<ProviderConnectionsLifecycleResponse[]>([]);
  const [platformClinicRows, setPlatformClinicRows] = React.useState<ProviderConnectionsPlatformEntityResponse[]>([]);
  const [platformDoctorRows, setPlatformDoctorRows] = React.useState<ProviderConnectionsPlatformEntityResponse[]>([]);
  const [linkRows, setLinkRows] = React.useState<ProviderConnectionsLinkResponse[]>([]);
  const [suggestions, setSuggestions] = React.useState<ProviderConnectionsSuggestionResponse[]>([]);
  const [conflicts, setConflicts] = React.useState<ProviderConnectionsConflictResponse[]>([]);
  const [ownershipRows, setOwnershipRows] = React.useState<ProviderConnectionsOwnershipResponse[]>([]);
  const [auditRows, setAuditRows] = React.useState<ProviderConnectionsAuditResponse[]>([]);
  const [selectedOwnership, setSelectedOwnership] = React.useState<ProviderConnectionsOwnershipResponse | null>(null);
  const [selectedLinkDetail, setSelectedLinkDetail] = React.useState<ProviderConnectionsLinkDetailResponse | null>(null);
  const [selectedLinkId, setSelectedLinkId] = React.useState<string | null>(null);
  const [publicType, setPublicType] = React.useState<ProviderConnectionsPublicProfileType>("CLINIC");
  const [publicCity, setPublicCity] = React.useState("");
  const [publicQuery, setPublicQuery] = React.useState("");
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
  const [rejectDialogOpen, setRejectDialogOpen] = React.useState(false);
  const [rejectTarget, setRejectTarget] = React.useState<ProviderConnectionsSuggestionResponse | null>(null);
  const [rejectReason, setRejectReason] = React.useState("");
  const [saving, setSaving] = React.useState(false);

  const refresh = React.useCallback(async () => {
    if (!auth.accessToken) return;
    setLoading(true);
    setError(null);
    try {
      const [overviewRes, publicRes, lifecycleRes, clinicEntitiesRes, doctorEntitiesRes, linksRes, suggestionsRes, ownershipsRes, conflictsRes, auditRes, detailRes] = await Promise.allSettled([
        getProviderConnectionsOverview(auth.accessToken),
        publicType === "DOCTOR"
          ? listProviderConnectionsPublicPractices(auth.accessToken, { q: publicQuery || null, city: publicCity || null })
          : listProviderConnectionsPublicProfiles(auth.accessToken, { type: publicType, q: publicQuery || null, city: publicCity || null }),
        listProviderConnectionsPublicProfileLifecycle(auth.accessToken, { type: publicType, q: publicQuery || null, city: publicCity || null }),
        listProviderConnectionsPlatformEntities(auth.accessToken, { type: "CLINIC", q: entityQuery || null }),
        listProviderConnectionsPlatformEntities(auth.accessToken, { type: "DOCTOR", q: entityQuery || null }),
        listProviderConnectionsLinks(auth.accessToken, { type: linkType || null, status: linkStatus || null, q: linkQuery || null }),
        listProviderConnectionsSuggestions(auth.accessToken, suggestionQuery || null),
        listProviderConnectionsOwnerships(auth.accessToken),
        listProviderConnectionsConflicts(auth.accessToken),
        getProviderConnectionsAuditEvents(auth.accessToken, { action: auditAction || null, tenantReference: auditTenant || null, providerType: auditProviderType || null, result: auditResult || null, q: auditQuery || null }),
        selectedLinkId ? getProviderConnectionsLinkDetail(auth.accessToken, selectedLinkId) : Promise.resolve(null),
      ]);

      setOverview(overviewRes.status === "fulfilled" ? overviewRes.value : null);
      setPublicRows(publicRes.status === "fulfilled" ? publicRes.value : []);
      setLifecycleRows(lifecycleRes.status === "fulfilled" ? lifecycleRes.value : []);
      setPlatformClinicRows(clinicEntitiesRes.status === "fulfilled" ? clinicEntitiesRes.value : []);
      setPlatformDoctorRows(doctorEntitiesRes.status === "fulfilled" ? doctorEntitiesRes.value : []);
      setLinkRows(linksRes.status === "fulfilled" ? linksRes.value : []);
      setSuggestions(suggestionsRes.status === "fulfilled" ? suggestionsRes.value : []);
      setOwnershipRows(ownershipsRes.status === "fulfilled" ? ownershipsRes.value : []);
      setConflicts(conflictsRes.status === "fulfilled" ? conflictsRes.value : []);
      setAuditRows(auditRes.status === "fulfilled" ? auditRes.value : []);
      setSelectedLinkDetail(detailRes.status === "fulfilled" ? detailRes.value : null);

      if (overviewRes.status === "rejected" || publicRes.status === "rejected" || lifecycleRes.status === "rejected" || clinicEntitiesRes.status === "rejected" || doctorEntitiesRes.status === "rejected" || linksRes.status === "rejected" || suggestionsRes.status === "rejected" || ownershipsRes.status === "rejected" || conflictsRes.status === "rejected" || auditRes.status === "rejected") {
        const reason = overviewRes.status === "rejected"
          ? overviewRes.reason
          : publicRes.status === "rejected"
            ? publicRes.reason
            : lifecycleRes.status === "rejected"
              ? lifecycleRes.reason
            : clinicEntitiesRes.status === "rejected"
              ? clinicEntitiesRes.reason
              : doctorEntitiesRes.status === "rejected"
                ? doctorEntitiesRes.reason
                : linksRes.status === "rejected"
                  ? linksRes.reason
                  : suggestionsRes.status === "rejected"
                    ? suggestionsRes.reason
                    : ownershipsRes.status === "rejected"
                      ? ownershipsRes.reason
                    : conflictsRes.status === "rejected"
                      ? conflictsRes.reason
                      : auditRes.status === "rejected"
                        ? auditRes.reason
                      : null;
        setError(reason instanceof Error ? reason.message : "Provider connections could not be loaded.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Provider connections could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, publicCity, publicQuery, publicType, entityQuery, linkQuery, linkStatus, linkType, suggestionQuery, selectedLinkId, auditAction, auditTenant, auditProviderType, auditResult, auditQuery]);

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
      case "VIEW_OWNERSHIP":
        return (
          <Button
            {...commonProps}
            onClick={() => {
              setSelectedOwnership(row);
            }}
          >
            {label}
          </Button>
        );
      case "RESOLVE_DISPUTE":
        return (
          <Button
            {...commonProps}
            onClick={() => {
              setSelectedOwnership(row);
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
      setSelectedLinkId(result.id);
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

  const selectedLink = selectedLinkDetail?.link || linkRows.find((item) => item.id === selectedLinkId) || null;

  const detailActions = selectedLink ? (
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
      {selectedLink.linkStatus === "PROPOSED" || selectedLink.linkStatus === "PENDING_VERIFICATION" ? (
        <Button variant="contained" onClick={async () => { if (auth.accessToken) { await approveProviderConnectionLink(auth.accessToken, selectedLink.id, "Approved from console"); await refresh(); } }}>
          Approve
        </Button>
      ) : null}
      {selectedLink.linkStatus === "APPROVED" ? (
        <Button variant="contained" onClick={async () => { if (auth.accessToken) { await activateProviderConnectionLink(auth.accessToken, selectedLink.id, "Activated from console"); await refresh(); } }}>
          Activate
        </Button>
      ) : null}
      {selectedLink.linkStatus !== "UNLINKED" ? (
        <Button variant="outlined" onClick={async () => { if (auth.accessToken) { await unlinkProviderConnectionLink(auth.accessToken, selectedLink.id, "Unlinked from console"); await refresh(); } }}>
          Unlink
        </Button>
      ) : null}
      <Button variant="outlined" onClick={async () => { if (auth.accessToken) { await relinkProviderConnectionLink(auth.accessToken, selectedLink.id, "Relinked from console"); await refresh(); } }}>
        Relink
      </Button>
      <Button variant="outlined" onClick={async () => { if (auth.accessToken) { await reconcileProviderConnection(auth.accessToken, { publicProfileType: selectedLink.publicProfileType, linkId: selectedLink.id, tenantReference: selectedLink.tenantReference }); await refresh(); } }}>
        Reconcile
      </Button>
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
        <Grid container spacing={2}>
          {(overview?.metrics || []).map((metric) => (
            <Grid key={metric.key} size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined" sx={{ height: "100%" }}>
                <CardContent>
                  <Stack spacing={0.5}>
                    <Chip size="small" color="primary" variant="outlined" label={metric.label} sx={{ alignSelf: "flex-start" }} />
                    <Typography variant="h4" sx={{ fontWeight: 900 }}>{metric.value}</Typography>
                    <Typography variant="body2" color="text.secondary">{metric.helperText}</Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
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
                      <TextField select label="Type" value={publicType} onChange={(event) => setPublicType(event.target.value as ProviderConnectionsPublicProfileType)} fullWidth>
                        <MenuItem value="CLINIC">Clinic</MenuItem>
                        <MenuItem value="DOCTOR">Doctor</MenuItem>
                        <MenuItem value="HOSPITAL">Hospital</MenuItem>
                      </TextField>
                      <TextField label="Search" value={publicQuery} onChange={(event) => setPublicQuery(event.target.value)} fullWidth />
                      <TextField label="City" value={publicCity} onChange={(event) => setPublicCity(event.target.value)} fullWidth />
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
                          {publicRows.map((row) => (
                            <TableRow key={`${row.publicProfileType}-${row.publicReference}-${row.publicPracticeReference || "profile"}`} hover>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.displayName || "Unnamed provider"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{formatProviderType(row.publicProfileType)} · {row.slug || "No slug"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.publicReference || "No public reference"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>{row.city || "—"}</TableCell>
                              <TableCell>
                                <Chip size="small" label={row.bookingCapability.replaceAll("_", " ")} color={actionChipColor(row.bookingCapability)} variant="outlined" />
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={row.publicationStatus.replaceAll("_", " ")} color={actionChipColor(row.publicationStatus)} variant="outlined" />
                              </TableCell>
                              <TableCell align="right">
                                <Stack direction="row" spacing={1} justifyContent="flex-end">
                                  <Button size="small" startIcon={<VisibilityRoundedIcon />} onClick={() => setSelectedLinkId(null)}>
                                    Inspect
                                  </Button>
                                  {row.publicProfileType !== "HOSPITAL" ? (
                                    <Button size="small" startIcon={<LinkRoundedIcon />} variant="contained" onClick={() => onOpenProposal(row)}>
                                      Propose link
                                    </Button>
                                  ) : null}
                                </Stack>
                              </TableCell>
                            </TableRow>
                          ))}
                          {!publicRows.length ? (
                            <TableRow>
                              <TableCell colSpan={5}>
                                <Typography variant="body2" color="text.secondary">
                                  No public profiles matched the current filters.
                                </Typography>
                              </TableCell>
                            </TableRow>
                          ) : null}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </Stack>
                ) : null}

                {section === "public-profile-lifecycle" ? (
                  <Stack spacing={1.5}>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                      <TextField select label="Type" value={publicType} onChange={(event) => setPublicType(event.target.value as ProviderConnectionsPublicProfileType)} fullWidth>
                        <MenuItem value="CLINIC">Clinic</MenuItem>
                        <MenuItem value="DOCTOR">Doctor</MenuItem>
                        <MenuItem value="HOSPITAL">Hospital</MenuItem>
                      </TextField>
                      <TextField label="Search" value={publicQuery} onChange={(event) => setPublicQuery(event.target.value)} fullWidth />
                      <TextField label="City" value={publicCity} onChange={(event) => setPublicCity(event.target.value)} fullWidth />
                    </Stack>
                    <TableContainer component={Paper} variant="outlined">
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Profile</TableCell>
                            <TableCell>Source</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Draft</TableCell>
                            <TableCell>Readiness</TableCell>
                            <TableCell>Saved</TableCell>
                            <TableCell align="right">Actions</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {lifecycleRows.map((row) => (
                            <TableRow key={`${row.publicProfileType}-${row.sourceSystem}-${row.sourceEntityReference}-${row.canonicalSlug || "draft"}`} hover>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.displayName || "Unnamed profile"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{formatProviderType(row.publicProfileType)} · {row.canonicalSlug || "No slug yet"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.publicPath || "No public path"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography variant="body2">{row.sourceSystem || "—"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.sourceEntityReference || "—"}</Typography>
                                  <Typography variant="caption" color="text.secondary">Revision {row.sourceRevision}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={(row.publicationStatus || "UNKNOWN").replaceAll("_", " ")} color={actionChipColor(row.publicationStatus)} variant="outlined" />
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={(row.draftStatus || "NO_DRAFT").replaceAll("_", " ")} color={actionChipColor(row.draftStatus)} variant="outlined" sx={{ alignSelf: "flex-start" }} />
                                  <Typography variant="caption" color="text.secondary">
                                    {row.draftReference || "No draft yet"}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    Version {row.draftVersionNumber || 0}
                                  </Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={row.ready ? "Ready" : "Needs work"} color={row.ready ? "success" : "warning"} variant="outlined" sx={{ alignSelf: "flex-start" }} />
                                  <Typography variant="caption" color="text.secondary">
                                    {row.missingFields.length ? `Missing: ${row.missingFields.join(", ")}` : "No missing fields"}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {row.invalidFields.length ? `Invalid: ${row.invalidFields.join(", ")}` : "No invalid fields"}
                                  </Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography variant="body2">{formatDateTime(row.draftLastSavedAt || row.projectedAt)}</Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {row.draftLastSavedAt ? "Draft last saved" : "Projection updated"} · rev {row.connectionRevision}
                                  </Typography>
                                </Stack>
                              </TableCell>
                              <TableCell align="right">
                                {row.publicPath ? (
                                  <Button size="small" component={Link} to={row.publicPath} target="_blank" rel="noreferrer">
                                    View public profile
                                  </Button>
                                ) : (
                                  <Typography variant="caption" color="text.secondary">No public path</Typography>
                                )}
                              </TableCell>
                            </TableRow>
                          ))}
                          {!lifecycleRows.length ? (
                            <TableRow>
                              <TableCell colSpan={7}>
                                <Typography variant="body2" color="text.secondary">
                                  No lifecycle records matched the current filters.
                                </Typography>
                              </TableCell>
                            </TableRow>
                          ) : null}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </Stack>
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
                          {(entityType === "DOCTOR" ? platformDoctorRows : platformClinicRows).map((row) => (
                            <TableRow key={`${row.entityType}-${row.tenantId}-${row.slug}`} hover>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.displayName || "Unnamed entity"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.entityType} · {row.slug || "No slug"}</Typography>
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
                                  <Chip size="small" label={row.publicListingEnabled ? "Enabled" : "Disabled"} color={row.publicListingEnabled ? "success" : "default"} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{row.publicListingConsent || "—"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={row.linkStatus || "NOT_LINKED"} color={actionChipColor(row.linkStatus)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{row.connectionStatus || "NOT_CONNECTED"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={row.currentDiscoverCapability || row.bookingCapability || "CALL_TO_BOOK"} color={actionChipColor(row.bookingCapability)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{row.platformBookingSetup || "—"}</Typography>
                                  {row.capabilityReason ? <Typography variant="caption" color="text.secondary">{row.capabilityReason}</Typography> : null}
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={row.currentAvailability || "UNKNOWN"} color={actionChipColor(row.currentAvailability)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">Revision {row.sourceRevision}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell align="right">
                                <Button size="small" onClick={() => {
                                  const selected = row.entityType === "DOCTOR" ? platformDoctorRows.find((item) => item.tenantReference === row.tenantReference && item.platformClinicReference === row.platformClinicReference && item.tenantDoctorUserReference === row.tenantDoctorUserReference) : platformClinicRows.find((item) => item.tenantReference === row.tenantReference && item.platformClinicReference === row.platformClinicReference);
                                  if (selected) {
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
                          ))}
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
                    {suggestions.map((row) => (
                      <Paper key={row.id} variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1}>
                          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                            <Stack spacing={0.25}>
                              <Typography sx={{ fontWeight: 700 }}>{row.publicDisplayName || "Suggested provider"}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {formatProviderType(row.publicProfileType)} · {row.platformCity || "—"}{row.platformArea ? ` · ${row.platformArea}` : ""}
                              </Typography>
                            </Stack>
                            <Chip size="small" label={row.status || "SUGGESTED"} color="warning" variant="outlined" />
                          </Stack>
                          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                            <Paper variant="outlined" sx={{ p: 1.25, flex: 1 }}>
                              <Typography variant="caption" color="text.secondary">Suggested platform entity</Typography>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.platformDisplayName || "—"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.tenantReference || "—"} · {row.platformClinicReference || "—"}</Typography>
                            </Paper>
                            <Paper variant="outlined" sx={{ p: 1.25, flex: 1 }}>
                              <Typography variant="caption" color="text.secondary">Confidence</Typography>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.confidence || "LOW"}</Typography>
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
                            <Button size="small" variant="contained" onClick={() => reviewSuggestion(row)}>
                              Review match
                            </Button>
                            <Button size="small" variant="outlined" onClick={() => {
                              setRejectTarget(row);
                              setRejectReason("");
                              setRejectDialogOpen(true);
                            }}>
                              Reject suggestion
                            </Button>
                          </Stack>
                        </Stack>
                      </Paper>
                    ))}
                    {!suggestions.length ? (
                      <Alert severity="info" variant="outlined">
                        No provider matches require review.
                      </Alert>
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
                            <TableCell>Tenant</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Capability</TableCell>
                            <TableCell>Updated</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {linkRows.map((row) => (
                            <TableRow key={row.id} hover onClick={() => setSelectedLinkId(row.id)} sx={{ cursor: "pointer" }}>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.publicDisplayName || row.publicReference || "Unnamed link"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.publicCity || "—"}{row.publicArea ? ` · ${row.publicArea}` : ""}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Typography variant="body2">{row.tenantName || "—"}</Typography>
                                <Typography variant="caption" color="text.secondary">{row.platformClinicReference || "No platform reference"}</Typography>
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={row.linkStatus.replaceAll("_", " ")} color={actionChipColor(row.linkStatus)} variant="outlined" />
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={row.bookingCapability.replaceAll("_", " ")} color={actionChipColor(row.bookingCapability)} variant="outlined" />
                              </TableCell>
                              <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
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
                          {ownershipRows.map((row) => (
                            <TableRow key={row.ownershipId} hover>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography sx={{ fontWeight: 700 }}>{row.displayName || "Unnamed profile"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.publicProfileType} · {row.publicProfileReference || "—"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.city || "—"}{row.area ? ` · ${row.area}` : ""}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={row.ownershipStatus || "UNCLAIMED"} color={actionChipColor(row.ownershipStatus)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{row.ownershipMethod || "—"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.consentState || "—"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={row.platformConnectionStatus || "NOT_CONNECTED"} color={actionChipColor(row.platformConnectionStatus)} variant="outlined" />
                                  <Typography variant="caption" color="text.secondary">{row.bookingCapability || "NOT_AVAILABLE"}</Typography>
                                  {row.maskedProviderMobile ? <Typography variant="caption" color="text.secondary">Mobile ending {row.maskedProviderMobile.slice(-4)}</Typography> : null}
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  {(row.membershipRoles || []).map((membership) => (
                                    <Chip key={membership} size="small" label={membership.replaceAll("_", " ")} variant="outlined" />
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
                          ))}
                          {!ownershipRows.length ? (
                            <TableRow>
                              <TableCell colSpan={6}>
                                <Typography variant="body2" color="text.secondary">
                                  No provider ownership records matched the current filters.
                                </Typography>
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
                    {conflicts.map((row) => (
                      <Paper key={row.id} variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={0.5}>
                          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                            <Typography sx={{ fontWeight: 700 }}>{row.title}</Typography>
                            <Chip size="small" label={row.severity} color="error" variant="outlined" />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">{row.details}</Typography>
                        </Stack>
                      </Paper>
                    ))}
                    {!conflicts.length ? <Typography variant="body2" color="text.secondary">No conflicts currently need review.</Typography> : null}
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
                      <Alert severity="info" variant="outlined">
                        No provider connection activity has been recorded yet.
                      </Alert>
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
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {auditRows.map((entry) => (
                              <TableRow key={entry.id} hover onClick={() => setSelectedLinkId(null)}>
                                <TableCell>{formatDateTime(entry.occurredAt)}</TableCell>
                                <TableCell>
                                  <Stack spacing={0.25}>
                                    <Typography sx={{ fontWeight: 700 }}>{entry.providerType || "—"}</Typography>
                                    <Typography variant="caption" color="text.secondary">{entry.platformClinicReference || "—"}</Typography>
                                  </Stack>
                                </TableCell>
                                <TableCell>{entry.tenantReference || "—"}</TableCell>
                                <TableCell>{entry.action}</TableCell>
                                <TableCell>{entry.previousState || "—"} → {entry.newState || "—"}</TableCell>
                                <TableCell>{entry.result || "—"}</TableCell>
                              </TableRow>
                            ))}
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
                            <Typography variant="h6" sx={{ fontWeight: 900 }}>Public profile lifecycle</Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">Review Discover drafts, publication readiness, and source synchronization state.</Typography>
                          <Button component={Link} to={sectionPath("public-profile-lifecycle")} variant="contained">Open workspace</Button>
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

        <Grid size={{ xs: 12, lg: 4 }}>
          <Paper variant="outlined" sx={{ p: 2.25, position: "sticky", top: 24 }}>
            <Stack spacing={2}>
              <Stack spacing={0.5}>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>Selected link</Typography>
                <Typography variant="body2" color="text.secondary">Inspect the active link and run lifecycle actions.</Typography>
              </Stack>
              {selectedLink ? (
                <Stack spacing={1.25}>
                  <Chip size="small" label={`${selectedLink.publicProfileType} · ${selectedLink.linkStatus.replaceAll("_", " ")}`} color={actionChipColor(selectedLink.linkStatus)} variant="outlined" sx={{ alignSelf: "flex-start" }} />
                  <Typography sx={{ fontWeight: 800 }}>{selectedLink.publicDisplayName || selectedLink.publicReference || "Selected link"}</Typography>
                  <Typography variant="body2" color="text.secondary">{selectedLink.tenantName || "—"}</Typography>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">Public identity</Typography>
                    <Typography variant="body2">{selectedLink.publicReference || "—"}</Typography>
                    {selectedLink.publicPracticeReference ? <Typography variant="caption" color="text.secondary">{selectedLink.publicPracticeReference}</Typography> : null}
                    <Typography variant="caption" color="text.secondary">{selectedLink.publicPath || "No public path"}</Typography>
                  </Stack>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">Connection</Typography>
                    <Typography variant="body2">{selectedLink.connectionStatus.replaceAll("_", " ")}</Typography>
                    <Typography variant="caption" color="text.secondary">Lifecycle {selectedLink.linkStatus.replaceAll("_", " ")}</Typography>
                  </Stack>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">Booking capability</Typography>
                    <Typography variant="body2">{selectedLink.bookingCapability.replaceAll("_", " ")}</Typography>
                    <Typography variant="caption" color="text.secondary">Availability {selectedLink.availabilityState.replaceAll("_", " ")}</Typography>
                  </Stack>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">Opaque booking reference</Typography>
                    <Typography variant="body2">{selectedLink.bookingReferenceMasked || "—"}</Typography>
                  </Stack>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">Source revision</Typography>
                    <Typography variant="body2">{selectedLink.sourceRevision}</Typography>
                  </Stack>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">Match</Typography>
                    <Typography variant="body2">{selectedLink.matchMethod?.replaceAll("_", " ") || "—"}</Typography>
                    <Typography variant="caption" color="text.secondary">{selectedLink.matchConfidence || "—"}</Typography>
                  </Stack>
                  {detailActions}
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
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.action}</Typography>
                          <Typography variant="caption" color="text.secondary">{formatDateTime(row.occurredAt)}</Typography>
                        </Paper>
                      ))}
                    </Stack>
                  ) : null}
                </Stack>
              ) : (
                <Alert severity="info" variant="outlined">
                  Pick a row from Links to inspect a specific link.
                </Alert>
              )}
            </Stack>
          </Paper>
        </Grid>
      </Grid>

      <ProposalDialog
        open={dialogOpen}
        draft={proposalDraft}
        clinicOptions={platformClinicRows}
        doctorOptions={platformDoctorRows}
        onClose={() => setDialogOpen(false)}
        onSubmit={submitProposal}
      />

      <Dialog open={Boolean(selectedOwnership)} onClose={() => setSelectedOwnership(null)} fullWidth maxWidth="sm">
        <DialogTitle>Ownership details</DialogTitle>
        <DialogContent dividers>
          {selectedOwnership ? (
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Alert severity="info" variant="outlined">
                Ownership actions are controlled by the backend `allowedActions` list for this row.
              </Alert>
              <Stack spacing={0.75}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{selectedOwnership.displayName || "Unnamed profile"}</Typography>
                <Typography variant="body2" color="text.secondary">{selectedOwnership.publicProfileType} · {selectedOwnership.publicProfileReference || "—"}</Typography>
                <Typography variant="body2" color="text.secondary">{selectedOwnership.city || "—"}{selectedOwnership.area ? ` · ${selectedOwnership.area}` : ""}</Typography>
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Chip size="small" label={selectedOwnership.ownershipStatus || "UNCLAIMED"} color={actionChipColor(selectedOwnership.ownershipStatus)} variant="outlined" />
                <Chip size="small" label={selectedOwnership.platformConnectionStatus || "NOT_CONNECTED"} color={actionChipColor(selectedOwnership.platformConnectionStatus)} variant="outlined" />
                <Chip size="small" label={selectedOwnership.bookingCapability || "NOT_AVAILABLE"} color={actionChipColor(selectedOwnership.bookingCapability)} variant="outlined" />
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
          <Button onClick={() => setSelectedOwnership(null)}>Close</Button>
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
