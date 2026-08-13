import * as React from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
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
  Divider,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TableContainer,
  TextField,
  Typography,
} from "@mui/material";

import {
  approveDiscoverProviderApplication,
  getDiscoverProviderApplicationReview,
  listDiscoverProviderApplications,
  publishDiscoverProviderApplication,
  requestChangesDiscoverProviderReview,
  startDiscoverProviderReview,
  getDiscoverProviderReviewDocumentBlob,
  type DiscoverProviderApplicationStatus,
  type DiscoverProviderDocument,
  type DiscoverProviderReviewDetail,
  type DiscoverProviderReviewSummary,
  type DiscoverProviderType,
} from "../../api/clinicApi";
import { ApiClientError } from "../../api/restClient";
import { useAuth } from "../../auth/useAuth";

type ProviderApplicationStatusFilter = "submitted" | "under-review" | "changes-requested" | "approved" | "published" | "all";

const STATUS_TABS: Array<{ value: ProviderApplicationStatusFilter; label: string; statuses: DiscoverProviderApplicationStatus[] | null; emptyState: string }> = [
  { value: "submitted", label: "Submitted", statuses: ["SUBMITTED"], emptyState: "No provider applications are waiting for review." },
  { value: "under-review", label: "Under Review", statuses: ["UNDER_REVIEW"], emptyState: "No provider applications are currently under review." },
  { value: "changes-requested", label: "Changes Requested", statuses: ["CHANGES_REQUESTED"], emptyState: "No provider applications currently need provider corrections." },
  { value: "approved", label: "Approved", statuses: ["APPROVED"], emptyState: "No approved provider applications are awaiting publication." },
  { value: "published", label: "Published", statuses: ["PUBLISHED"], emptyState: "No published provider applications match the current filters." },
  { value: "all", label: "All", statuses: null, emptyState: "No provider applications match the current filters." },
];

const PROVIDER_TYPE_OPTIONS: Array<{ value: "" | DiscoverProviderType; label: string }> = [
  { value: "", label: "All types" },
  { value: "INDIVIDUAL_DOCTOR", label: "Doctor" },
  { value: "CLINIC", label: "Clinic" },
  { value: "HOSPITAL", label: "Hospital" },
];

function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

function formatFileSize(sizeBytes: number | null | undefined) {
  if (sizeBytes == null || !Number.isFinite(sizeBytes)) return "—";
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  if (sizeBytes < 1024 * 1024) return `${Math.round(sizeBytes / 102.4) / 10} KB`;
  return `${Math.round(sizeBytes / 104857.6) / 10} MB`;
}

function statusChipColor(status: DiscoverProviderApplicationStatus) {
  switch (status) {
    case "SUBMITTED":
      return "info";
    case "UNDER_REVIEW":
      return "warning";
    case "CHANGES_REQUESTED":
      return "secondary";
    case "APPROVED":
      return "success";
    case "PUBLISHED":
      return "success";
    default:
      return "default";
  }
}

const STATUS_FILTER_MAP: Record<ProviderApplicationStatusFilter, DiscoverProviderApplicationStatus[] | null> = {
  submitted: ["SUBMITTED"],
  "under-review": ["UNDER_REVIEW"],
  "changes-requested": ["CHANGES_REQUESTED"],
  approved: ["APPROVED"],
  published: ["PUBLISHED"],
  all: null,
};

function currentStatusTab(value: string | null) {
  return STATUS_TABS.find((tab) => tab.value === value) || STATUS_TABS[0];
}

function isKnownStatusFilter(value: string | null): value is ProviderApplicationStatusFilter {
  return value != null && Object.prototype.hasOwnProperty.call(STATUS_FILTER_MAP, value);
}

function resolveLoadError(err: unknown, fallback: string) {
  if (err instanceof ApiClientError) {
    if (err.status === 403) {
      return "You do not have access to provider applications.";
    }
    if (err.status >= 400 && err.status < 500 && err.message.trim().toLowerCase() === "required text is missing") {
      return fallback;
    }
    return err.message || fallback;
  }
  if (err instanceof Error) {
    if (err.message.trim().toLowerCase() === "required text is missing") {
      return fallback;
    }
    return err.message || fallback;
  }
  return fallback;
}

function documentTypeLabel(documentType: string) {
  return documentType
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (value) => value.toUpperCase());
}

function virusScanLabel(status: string | null | undefined) {
  const normalized = (status || "PENDING").trim().toUpperCase();
  switch (normalized) {
    case "CLEAN":
      return "Clean";
    case "INFECTED":
      return "Blocked";
    case "FAILED":
      return "Failed";
    case "NOT_SCANNED":
      return "Not scanned";
    case "PENDING":
    default:
      return "Pending";
  }
}

function virusScanChipColor(status: string | null | undefined): "default" | "success" | "warning" | "error" {
  const normalized = (status || "PENDING").trim().toUpperCase();
  switch (normalized) {
    case "CLEAN":
      return "success";
    case "INFECTED":
      return "error";
    case "FAILED":
      return "error";
    case "NOT_SCANNED":
      return "default";
    case "PENDING":
    default:
      return "warning";
  }
}

function isPreviewSupported(contentType: string | null | undefined) {
  const normalized = (contentType || "").trim().toLowerCase();
  return normalized.startsWith("image/") || normalized === "application/pdf";
}

function isDownloadBlockedByScan(status: string | null | undefined) {
  return (status || "").trim().toUpperCase() === "INFECTED";
}

function resolveDocumentActionError(err: unknown) {
  if (err instanceof ApiClientError) {
    if (err.status === 403) {
      return "You do not have permission to view this document.";
    }
    if (err.status === 404) {
      return "The document is no longer available.";
    }
    return err.message || "The document could not be loaded. Please try again.";
  }
  if (err instanceof Error) {
    return err.message || "The document could not be loaded. Please try again.";
  }
  return "The document could not be loaded. Please try again.";
}

export default function DiscoverProviderApplicationsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const params = useParams<{ referenceNumber?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [queueError, setQueueError] = React.useState<string | null>(null);
  const [detailError, setDetailError] = React.useState<string | null>(null);
  const [toast, setToast] = React.useState<string | null>(null);
  const [rows, setRows] = React.useState<DiscoverProviderReviewSummary[]>([]);
  const [detail, setDetail] = React.useState<DiscoverProviderReviewDetail | null>(null);
  const [changesDialogOpen, setChangesDialogOpen] = React.useState(false);
  const [changesReason, setChangesReason] = React.useState("");
  const [changesSection, setChangesSection] = React.useState("LOCATIONS");
  const [documentPreview, setDocumentPreview] = React.useState<{
    document: DiscoverProviderDocument;
    objectUrl: string | null;
    loading: boolean;
    error: string | null;
  } | null>(null);

  const activeTab = currentStatusTab(searchParams.get("status"));
  const search = searchParams.get("search") || "";
  const providerType = (searchParams.get("providerType") || "") as "" | DiscoverProviderType;
  const referenceNumber = params.referenceNumber || null;
  const authSignature = React.useMemo(() => [
    auth.initialized ? "initialized" : "booting",
    auth.accessToken ? "token" : "no-token",
    auth.rolesUpper.join(","),
    auth.permissions.join(","),
    auth.selectedTenant?.id || "platform",
  ].join("|"), [auth.initialized, auth.accessToken, auth.permissions, auth.rolesUpper, auth.selectedTenant?.id]);

  React.useEffect(() => {
    const current = searchParams.get("status");
    if (isKnownStatusFilter(current)) {
      return;
    }
    const next = new URLSearchParams(searchParams);
    next.set("status", STATUS_TABS[0].value);
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const refresh = React.useCallback(async () => {
    if (!auth.accessToken) return;
    setLoading(true);
    setQueueError(null);
    setDetailError(null);
    try {
      const [listResponse, detailResponse] = await Promise.allSettled([
        listDiscoverProviderApplications(auth.accessToken, {
          status: STATUS_FILTER_MAP[activeTab.value] || undefined,
          providerType: providerType || null,
          search: search || null,
        }),
        referenceNumber ? getDiscoverProviderApplicationReview(auth.accessToken, referenceNumber) : Promise.resolve(null),
      ]);

      if (listResponse.status === "fulfilled") {
        setRows(listResponse.value);
      } else {
        setRows([]);
        setQueueError(resolveLoadError(listResponse.reason, "Provider applications could not be loaded. Please try again."));
      }

      if (detailResponse.status === "fulfilled") {
        setDetail(detailResponse.value);
      } else {
        setDetail(null);
        setDetailError(resolveLoadError(detailResponse.reason, "Provider application details could not be loaded. Please try again."));
      }
    } finally {
      setLoading(false);
    }
  }, [activeTab.value, auth.accessToken, authSignature, providerType, referenceNumber, search]);

  React.useEffect(() => {
    void refresh();
  }, [refresh]);

  const closeDocumentPreview = React.useCallback(() => {
    setDocumentPreview((current) => {
      if (current?.objectUrl?.startsWith("blob:")) {
        URL.revokeObjectURL(current.objectUrl);
      }
      return null;
    });
  }, []);

  React.useEffect(() => () => {
    closeDocumentPreview();
  }, [closeDocumentPreview]);

  const canReview = auth.hasPermission("discover.provider.application.review");
  const canRequestChanges = auth.hasPermission("discover.provider.application.request.changes");
  const canApprove = auth.hasPermission("discover.provider.application.approve");
  const canPublish = auth.hasPermission("discover.provider.application.publish");
  const canViewDocumentHistory = auth.hasPermission("discover.provider.application.history.view");

  async function runAction(action: "start-review" | "approve" | "publish") {
    if (!auth.accessToken || !detail) return;
    setSaving(true);
    setQueueError(null);
    setDetailError(null);
    try {
      if (action === "start-review") {
        await startDiscoverProviderReview(auth.accessToken, detail.application.referenceNumber);
        setToast("Review started.");
      } else if (action === "approve") {
        await approveDiscoverProviderApplication(auth.accessToken, detail.application.referenceNumber);
        setToast("Application approved.");
      } else {
        await publishDiscoverProviderApplication(auth.accessToken, detail.application.referenceNumber);
        setToast("Provider published.");
      }
      await refresh();
    } catch (err) {
      setDetailError(resolveLoadError(err, "Provider application action could not be completed. Please try again."));
    } finally {
      setSaving(false);
    }
  }

  async function submitChangesRequest() {
    if (!auth.accessToken || !detail) return;
    setSaving(true);
    setQueueError(null);
    setDetailError(null);
    try {
      await requestChangesDiscoverProviderReview(auth.accessToken, detail.application.referenceNumber, {
        reason: changesReason.trim(),
        requestedSections: [changesSection],
      });
      setChangesDialogOpen(false);
      setChangesReason("");
      setToast("Changes requested.");
      await refresh();
    } catch (err) {
      setDetailError(resolveLoadError(err, "Changes could not be requested. Please try again."));
    } finally {
      setSaving(false);
    }
  }

  async function openDocumentPreview(targetDocument: DiscoverProviderDocument) {
    if (!auth.accessToken || !detail || !canViewDocumentHistory || isDownloadBlockedByScan(targetDocument.virusScanStatus)) {
      return;
    }
    setDocumentPreview((current) => {
      if (current?.objectUrl?.startsWith("blob:")) {
        URL.revokeObjectURL(current.objectUrl);
      }
      return {
        document: targetDocument,
        objectUrl: null,
        loading: true,
        error: null,
      };
    });
    try {
      const blob = await getDiscoverProviderReviewDocumentBlob(auth.accessToken, detail.application.referenceNumber, targetDocument.id);
      if (!blob.size) {
        throw new Error("The document could not be loaded. Please try again.");
      }
      const objectUrl = URL.createObjectURL(blob);
      setDocumentPreview((current) => {
        if (!current || current.document.id !== targetDocument.id) {
          URL.revokeObjectURL(objectUrl);
          return current;
        }
        return {
          document: targetDocument,
          objectUrl,
          loading: false,
          error: null,
        };
      });
    } catch (err) {
      setDocumentPreview((current) => {
        if (!current || current.document.id !== targetDocument.id) {
          return current;
        }
        return {
          document: targetDocument,
          objectUrl: null,
          loading: false,
          error: resolveDocumentActionError(err),
        };
      });
    }
  }

  async function downloadDocument(targetDocument: DiscoverProviderDocument) {
    if (!auth.accessToken || !detail || !canViewDocumentHistory || isDownloadBlockedByScan(targetDocument.virusScanStatus)) {
      return;
    }
    try {
      const blob = await getDiscoverProviderReviewDocumentBlob(auth.accessToken, detail.application.referenceNumber, targetDocument.id);
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = targetDocument.originalFilename || targetDocument.id;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch (err) {
      setDetailError(resolveDocumentActionError(err));
    }
  }

  return (
    <Stack spacing={2} sx={{ p: 3 }}>
      <Box>
        <Typography variant="h5" sx={{ fontWeight: 900 }}>Discover Provider Applications</Typography>
        <Typography variant="body2" color="text.secondary">
          Review submitted provider onboarding applications in platform mode.
        </Typography>
      </Box>

      {queueError ? <Alert severity="error">{queueError}</Alert> : null}
      {toast ? <Alert severity="success" onClose={() => setToast(null)}>{toast}</Alert> : null}

      <Card variant="outlined">
        <CardContent>
          <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
            <TextField
              select
              label="Status"
              value={activeTab.value}
              onChange={(event) => {
                const next = new URLSearchParams(searchParams);
                next.set("status", event.target.value);
                setSearchParams(next);
              }}
              sx={{ minWidth: 200 }}
            >
              {STATUS_TABS.map((tab) => (
                <MenuItem key={tab.value} value={tab.value}>{tab.label}</MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Provider type"
              value={providerType}
              onChange={(event) => {
                const next = new URLSearchParams(searchParams);
                if (event.target.value) next.set("providerType", event.target.value);
                else next.delete("providerType");
                setSearchParams(next);
              }}
              sx={{ minWidth: 200 }}
            >
              {PROVIDER_TYPE_OPTIONS.map((option) => (
                <MenuItem key={option.label} value={option.value}>{option.label}</MenuItem>
              ))}
            </TextField>
            <TextField
              label="Search"
              value={search}
              onChange={(event) => {
                const next = new URLSearchParams(searchParams);
                if (event.target.value.trim()) next.set("search", event.target.value);
                else next.delete("search");
                setSearchParams(next);
              }}
              placeholder="Reference, provider, registration, email, phone"
              fullWidth
            />
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1.5 }}>Queue</Typography>
              {loading ? <Typography color="text.secondary">Loading provider applications…</Typography> : null}
              {!loading && !queueError && rows.length === 0 ? <Alert severity="info">{activeTab.emptyState}</Alert> : null}
              {!loading && rows.length > 0 ? (
                <TableContainer>
                  <Table size="small" aria-label="Discover provider applications">
                    <TableHead>
                      <TableRow>
                        <TableCell>Reference</TableCell>
                        <TableCell>Provider</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Submitted</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((row) => (
                        <TableRow
                          key={row.id}
                          hover
                          selected={row.referenceNumber === referenceNumber}
                          sx={{ cursor: "pointer" }}
                          onClick={() => navigate(`/platform/discover/provider-applications/${row.referenceNumber}?${searchParams.toString()}`)}
                        >
                          <TableCell>{row.referenceNumber}</TableCell>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.displayName}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {[row.city, row.state, row.country].filter(Boolean).join(", ") || row.email}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell><Chip size="small" label={row.status.replaceAll("_", " ")} color={statusChipColor(row.status)} /></TableCell>
                          <TableCell>{formatDateTime(row.submittedAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              ) : null}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 7 }}>
          <Card variant="outlined">
            <CardContent>
              {!detail ? (
                <Alert severity={detailError ? "error" : "info"}>{detailError || "Select a provider application to review."}</Alert>
              ) : (
                <Stack spacing={2}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, alignItems: "flex-start", flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>{detail.application.referenceNumber}</Typography>
                      <Typography variant="body1">{detail.application.displayName || detail.application.legalName || detail.application.email}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {detail.application.providerType.replaceAll("_", " ")} · Version {detail.application.version} · Submitted {formatDateTime(detail.application.submittedAt)}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      {detail.application.status === "SUBMITTED" && canReview ? <Button variant="contained" disabled={saving} onClick={() => void runAction("start-review")}>Start review</Button> : null}
                      {(detail.application.status === "SUBMITTED" || detail.application.status === "UNDER_REVIEW") && canRequestChanges ? <Button variant="outlined" disabled={saving} onClick={() => setChangesDialogOpen(true)}>Request changes</Button> : null}
                      {detail.application.status === "UNDER_REVIEW" && canApprove ? <Button variant="contained" color="success" disabled={saving} onClick={() => void runAction("approve")}>Approve</Button> : null}
                      {detail.application.status === "APPROVED" && canPublish ? <Button variant="contained" color="success" disabled={saving} onClick={() => void runAction("publish")}>Publish</Button> : null}
                    </Stack>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip size="small" label={detail.application.status.replaceAll("_", " ")} color={statusChipColor(detail.application.status)} />
                    <Chip size="small" label={detail.application.contactVerification.requirementSatisfied ? "Contact verified" : "Contact verification pending"} variant="outlined" />
                    <Chip size="small" label={`${detail.completion.completionPercentage}% complete`} variant="outlined" />
                  </Stack>

                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Preview</Typography>
                          <Typography variant="body2"><b>Name:</b> {detail.preview.displayName}</Typography>
                          <Typography variant="body2"><b>Subtitle:</b> {detail.preview.subtitle || "—"}</Typography>
                          <Typography variant="body2"><b>Location:</b> {detail.preview.locationSummary || "—"}</Typography>
                          <Typography variant="body2"><b>Services:</b> {detail.preview.services.join(", ") || "—"}</Typography>
                          <Typography variant="body2"><b>Specialities:</b> {detail.preview.specialities.join(", ") || "—"}</Typography>
                          <Typography variant="body2"><b>Biography:</b> {detail.preview.biography || "—"}</Typography>
                          {detail.publicProfilePath ? <Button component={Link} to={detail.publicProfilePath} target="_blank" rel="noreferrer" size="small" sx={{ mt: 1 }}>Open public profile</Button> : null}
                        </CardContent>
                      </Card>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Documents</Typography>
                          <Stack spacing={1}>
                            {detail.application.documents.map((document) => (
                              <Box key={document.id} sx={{ border: 1, borderColor: "divider", borderRadius: 1.5, p: 1.5 }}>
                                <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={1.5}>
                                  <Box>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{documentTypeLabel(document.documentType)}</Typography>
                                    <Typography variant="body2">{document.originalFilename}</Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {(document.contentType || "Unknown type").toUpperCase()} · {formatFileSize(document.sizeBytes)} · Uploaded {formatDateTime(document.uploadedAt)}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent={{ xs: "flex-start", sm: "flex-end" }}>
                                    <Chip size="small" label={`Virus scan: ${virusScanLabel(document.virusScanStatus)}`} color={virusScanChipColor(document.virusScanStatus)} />
                                    {canViewDocumentHistory && isPreviewSupported(document.contentType) ? (
                                      <Button
                                        size="small"
                                        variant="outlined"
                                        onClick={() => void openDocumentPreview(document)}
                                        disabled={isDownloadBlockedByScan(document.virusScanStatus)}
                                      >
                                        Preview
                                      </Button>
                                    ) : null}
                                    {canViewDocumentHistory ? (
                                      <Button
                                        size="small"
                                        variant="text"
                                        onClick={() => void downloadDocument(document)}
                                        disabled={isDownloadBlockedByScan(document.virusScanStatus)}
                                      >
                                        Download
                                      </Button>
                                    ) : null}
                                  </Stack>
                                </Stack>
                                {isDownloadBlockedByScan(document.virusScanStatus) ? (
                                  <Alert severity="error" sx={{ mt: 1 }}>This document was blocked by the security scan.</Alert>
                                ) : null}
                                {!isPreviewSupported(document.contentType) ? (
                                  <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 1 }}>
                                    Preview is not available for this file type.
                                  </Typography>
                                ) : null}
                              </Box>
                            ))}
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  </Grid>

                  <Card variant="outlined">
                    <CardContent>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Review History</Typography>
                      <Stack spacing={1}>
                        {detail.timeline.map((entry, index) => (
                          <Box key={`${entry.label}-${entry.timestamp}-${index}`}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{entry.label}</Typography>
                            <Typography variant="caption" color="text.secondary">{formatDateTime(entry.timestamp)} · {entry.description || entry.actorCategory || "Lifecycle update"}</Typography>
                          </Box>
                        ))}
                        {detail.changeRequests.map((request) => (
                          <Box key={request.id} sx={{ pt: 1 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>Change request</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {request.requestedSections.join(", ") || "General"} · {request.reviewerMessage || "Changes requested"} · {formatDateTime(request.requestedAt)}
                            </Typography>
                          </Box>
                        ))}
                      </Stack>
                    </CardContent>
                  </Card>
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Dialog open={changesDialogOpen} onClose={() => setChangesDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Request Changes</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField select label="Affected section" value={changesSection} onChange={(event) => setChangesSection(event.target.value)}>
              {["ACCOUNT", "ORGANISATION", "PROFESSIONAL_DETAILS", "SERVICES", "LOCATIONS", "BRANDING", "DOCUMENTS", "OTHER"].map((section) => (
                <MenuItem key={section} value={section}>{section.replaceAll("_", " ")}</MenuItem>
              ))}
            </TextField>
            <TextField
              label="Reviewer comment"
              multiline
              minRows={4}
              value={changesReason}
              onChange={(event) => setChangesReason(event.target.value)}
              helperText="Explain the required correction clearly."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setChangesDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={saving || !changesReason.trim()} onClick={() => void submitChangesRequest()}>Request changes</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(documentPreview)} onClose={closeDocumentPreview} fullWidth maxWidth="md">
        <DialogTitle>{documentPreview ? documentTypeLabel(documentPreview.document.documentType) : "Document preview"}</DialogTitle>
        <DialogContent dividers>
          {documentPreview ? (
            <Stack spacing={2}>
              <Stack spacing={0.5}>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{documentPreview.document.originalFilename}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {documentTypeLabel(documentPreview.document.documentType)} · {(documentPreview.document.contentType || "Unknown type").toUpperCase()} · {formatFileSize(documentPreview.document.sizeBytes)} · Uploaded {formatDateTime(documentPreview.document.uploadedAt)}
                </Typography>
              </Stack>
              <Divider />
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={`Virus scan: ${virusScanLabel(documentPreview.document.virusScanStatus)}`} color={virusScanChipColor(documentPreview.document.virusScanStatus)} />
              </Stack>
              {documentPreview.loading ? (
                <Typography color="text.secondary">Loading document preview…</Typography>
              ) : documentPreview.error ? (
                <Alert severity="error">{documentPreview.error}</Alert>
              ) : documentPreview.objectUrl && documentPreview.document.contentType.toLowerCase().startsWith("image/") ? (
                <Box
                  component="img"
                  src={documentPreview.objectUrl}
                  alt={`${detail?.application.displayName || detail?.application.legalName || detail?.application.referenceNumber || "Provider"} ${documentTypeLabel(documentPreview.document.documentType)}`}
                  sx={{ maxWidth: "100%", maxHeight: 560, objectFit: "contain", borderRadius: 1 }}
                />
              ) : documentPreview.objectUrl && documentPreview.document.contentType.toLowerCase() === "application/pdf" ? (
                <Box component="iframe" title={documentPreview.document.originalFilename} src={documentPreview.objectUrl} sx={{ width: "100%", height: 560, border: 0, borderRadius: 1 }} />
              ) : (
                <Alert severity="info">Preview is not available for this file type.</Alert>
              )}
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          {documentPreview ? (
            <Button
              onClick={() => void downloadDocument(documentPreview.document)}
              disabled={documentPreview.loading || isDownloadBlockedByScan(documentPreview.document.virusScanStatus)}
            >
              Download
            </Button>
          ) : null}
          <Button onClick={closeDocumentPreview}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
