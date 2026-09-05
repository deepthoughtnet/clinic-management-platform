import * as React from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Divider, Paper, Stack, Tab, Tabs, TextField, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  completeClinicalDocumentFindingReview,
  decideClinicalDocumentFinding,
  getClinicalDocumentFindingReview,
  getConsultation,
  getPatientDocuments,
  getPatientDocumentViewUrl,
  type ClinicalDocument,
  type ClinicalDocumentFindingReview,
} from "../../api/clinicApi";

type Review = ClinicalDocumentFindingReview;
type Finding = Review["findings"][number];
type ReviewTab = "PENDING" | "COMPLETED" | "ALL";

type ReviewEntry = {
  document: ClinicalDocument;
  review: Review;
};

function isPending(finding: Finding) {
  const status = (finding.verificationStatus || "").trim().toUpperCase().replace(/[\s-]+/g, "_");
  return ["PENDING_REVIEW", "PENDING_VERIFICATION", "PENDING", "REVIEW_REQUIRED", "AI_REVIEW_REQUIRED"].includes(status)
    && Boolean(finding.id)
    && !finding.decision;
}

function reportLabel(document: ClinicalDocument) {
  return `${document.title}${document.reportDate ? ` · ${document.reportDate}` : ""}`;
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return null;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

function normalizeStatus(status: string | null | undefined) {
  return (status || "").trim().toUpperCase().replace(/[\s-]+/g, "_");
}

function tabMatches(entry: ReviewEntry, tab: ReviewTab) {
  if (tab === "ALL") return true;
  if (tab === "PENDING") return entry.review.pendingCount > 0;
  return Boolean(entry.review.reviewCompleted || entry.document.aiExtractionReviewedAt);
}

function tabLabel(tab: ReviewTab, counts: { pendingReports: number; pendingFindings: number; completedReports: number; completedFindings: number; allReports: number; allFindings: number; }) {
  if (tab === "PENDING") return `Pending (${counts.pendingFindings} findings across ${counts.pendingReports} reports)`;
  if (tab === "COMPLETED") return `Completed (${counts.completedReports} reports${counts.completedFindings ? ` · ${counts.completedFindings} findings` : ""})`;
  return `All (${counts.allReports} reports · ${counts.allFindings} findings)`;
}

function findingDisplayValue(finding: Finding) {
  const value = finding.value || "Not recorded";
  return finding.unit ? `${value} ${finding.unit}` : value;
}

function findingDecisionLabel(finding: Finding, completed: boolean) {
  if (finding.decision) return finding.decision;
  if (completed) return normalizeStatus(finding.verificationStatus) || "COMPLETED";
  return "Pending Verification";
}

export default function ClinicalDocumentFindingReviewPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const { id: consultationId = "" } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedId = searchParams.get("documentId");
  const [consultation, setConsultation] = React.useState<Awaited<ReturnType<typeof getConsultation>> | null>(null);
  const [entries, setEntries] = React.useState<ReviewEntry[]>([]);
  const [selectedReportId, setSelectedReportId] = React.useState<string | null>(selectedId);
  const [activeTab, setActiveTab] = React.useState<ReviewTab>("PENDING");
  const [viewUrl, setViewUrl] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [busyId, setBusyId] = React.useState<string | null>(null);
  const [editing, setEditing] = React.useState<string | null>(null);
  const [draft, setDraft] = React.useState<Record<string, { value: string; unit: string; referenceRange: string; flag: string }>>({});
  const initialSelectionApplied = React.useRef(false);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !consultationId) return;
    setLoading(true);
    setError(null);
    try {
      const currentConsultation = await getConsultation(auth.accessToken, auth.tenantId, consultationId);
      const rows = await getPatientDocuments(auth.accessToken, auth.tenantId, currentConsultation.patientId);
      const results = await Promise.all(rows.map(async (document) => {
        try {
          const review = await getClinicalDocumentFindingReview(auth.accessToken!, auth.tenantId!, document.id);
          return { document, review } as const;
        } catch {
          return null;
        }
      }));
      const nextEntries = results
        .filter((entry): entry is { document: ClinicalDocument; review: Review } => entry !== null && entry.review.findings.length > 0)
        .map((entry) => ({ document: entry.document, review: entry.review }));
      setConsultation(currentConsultation);
      setEntries(nextEntries);
      initialSelectionApplied.current = false;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Findings review could not be loaded");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, consultationId]);

  React.useEffect(() => {
    void load();
    return () => {
      if (viewUrl) {
        URL.revokeObjectURL(viewUrl);
      }
    };
  }, [load]);

  const counts = React.useMemo(() => {
    const pendingEntries = entries.filter((entry) => entry.review.pendingCount > 0);
    const completedEntries = entries.filter((entry) => entry.review.reviewCompleted || entry.document.aiExtractionReviewedAt);
    return {
      pendingReports: pendingEntries.length,
      pendingFindings: pendingEntries.reduce((sum, entry) => sum + entry.review.pendingCount, 0),
      completedReports: completedEntries.length,
      completedFindings: completedEntries.reduce((sum, entry) => sum + entry.review.findings.length, 0),
      allReports: entries.length,
      allFindings: entries.reduce((sum, entry) => sum + entry.review.findings.length, 0),
    };
  }, [entries]);

  const visibleEntries = React.useMemo(() => entries.filter((entry) => tabMatches(entry, activeTab)), [entries, activeTab]);
  const selectedEntry = React.useMemo(() => {
    if (!visibleEntries.length) {
      return null;
    }
    return visibleEntries.find((entry) => entry.document.id === selectedReportId) || visibleEntries[0] || null;
  }, [visibleEntries, selectedReportId]);
  const review = selectedEntry?.review || null;
  const reviewCompleted = Boolean(review?.reviewCompleted || selectedEntry?.document.aiExtractionReviewedAt);
  const pendingCount = review?.pendingCount || 0;
  const patientName = consultation?.patientName || consultation?.patientId || "Patient";

  React.useEffect(() => {
    if (!entries.length) {
      return;
    }
    if (!initialSelectionApplied.current) {
      const queryMatch = selectedId ? entries.find((entry) => entry.document.id === selectedId) : null;
      const defaultEntry = queryMatch
        || entries.find((entry) => entry.review.pendingCount > 0)
        || entries.find((entry) => entry.review.reviewCompleted || entry.document.aiExtractionReviewedAt)
        || entries[0];
      if (defaultEntry) {
        const nextTab: ReviewTab = defaultEntry.review.pendingCount > 0 ? "PENDING" : (defaultEntry.review.reviewCompleted || defaultEntry.document.aiExtractionReviewedAt ? "COMPLETED" : "ALL");
        setActiveTab(nextTab);
        setSelectedReportId(defaultEntry.document.id);
        setSearchParams({ documentId: defaultEntry.document.id }, { replace: true });
      }
      initialSelectionApplied.current = true;
      return;
    }
    if (selectedEntry && selectedEntry.document.id !== selectedReportId) {
      setSelectedReportId(selectedEntry.document.id);
      setSearchParams({ documentId: selectedEntry.document.id }, { replace: true });
    }
  }, [entries, selectedId, selectedEntry, selectedReportId, setSearchParams]);

  React.useEffect(() => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !selectedEntry) {
      if (viewUrl) {
        URL.revokeObjectURL(viewUrl);
        setViewUrl(null);
      }
      return;
    }
    const accessToken = auth.accessToken;
    const tenantId = auth.tenantId;
    const patientId = consultation.patientId;
    const documentId = selectedEntry.document.id;
    let cancelled = false;
    let objectUrl: string | null = null;
    setViewUrl(null);
    void (async () => {
      const blob = await getPatientDocumentViewUrl(accessToken, tenantId, patientId, documentId);
      objectUrl = URL.createObjectURL(blob.blob);
      if (!cancelled) {
        setViewUrl(objectUrl);
      }
    })().catch((err) => {
      if (!cancelled) {
        setError(err instanceof Error ? err.message : "Source report could not be loaded");
      }
    });
    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [auth.accessToken, auth.tenantId, consultation?.patientId, selectedEntry?.document.id]);

  const complete = async () => {
    if (!auth.accessToken || !auth.tenantId || !review || busyId || pendingCount > 0 || reviewCompleted) return;
    setBusyId("complete");
    setError(null);
    try {
      const updated = await completeClinicalDocumentFindingReview(auth.accessToken, auth.tenantId, review.documentId);
      const refreshedEntries = entries.map((entry) => entry.document.id === updated.documentId ? { ...entry, review: updated } : entry);
      setEntries(refreshedEntries);
      setSelectedReportId(updated.documentId);
      setSearchParams({ documentId: updated.documentId }, { replace: true });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Complete Review failed");
    } finally {
      setBusyId(null);
    }
  };

  const decide = async (finding: Finding, decision: "CONFIRMED" | "EDITED" | "REJECTED") => {
    if (!auth.accessToken || !auth.tenantId || !review || busyId) return;
    setBusyId(finding.id);
    setError(null);
    try {
      const values = draft[finding.id] || { value: finding.value || "", unit: finding.unit || "", referenceRange: finding.referenceRange || "", flag: finding.flag || "" };
      const updated = await decideClinicalDocumentFinding(auth.accessToken, auth.tenantId, review.documentId, finding.id, { decision, ...values });
      setEntries((current) => current.map((entry) => entry.document.id === updated.documentId ? { ...entry, review: updated } : entry));
      setSelectedReportId(updated.documentId);
      setEditing(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Finding review could not be saved");
    } finally {
      setBusyId(null);
    }
  };

  const pendingInfo = reviewCompleted
    ? "Review completed. All findings are now read-only."
    : pendingCount > 0
      ? `${pendingCount} findings still require review.`
      : "All findings have an explicit decision.";

  const selectedReviewedAt = selectedEntry?.review.reviewedAt || selectedEntry?.document.aiExtractionReviewedAt || null;
  const selectedReviewedBy = selectedEntry?.review.reviewedByDisplayName || selectedEntry?.document.aiExtractionReviewedByAppUserId || null;
  const selectedReviewedById = selectedEntry?.review.reviewedBy || null;

  return (
    <Stack spacing={2} sx={{ minHeight: "calc(100vh - 112px)" }}>
      <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" alignItems={{ xs: "stretch", md: "center" }} gap={1}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Review AI-extracted findings</Typography>
          <Typography variant="body2" color="text.secondary">
            {patientName}{selectedEntry ? ` · ${selectedEntry.document.title}${selectedEntry.document.reportDate ? ` · ${selectedEntry.document.reportDate}` : ""}` : ""}
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => navigate(`/consultations/${consultationId}`)}>Back to consultation</Button>
      </Stack>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading && !entries.length ? <Box sx={{ display: "grid", placeItems: "center", flex: 1, minHeight: 360 }}><CircularProgress /></Box> : null}
      {!loading && !entries.length ? <Alert severity="info">No AI findings history was found for this patient.</Alert> : null}

      {entries.length ? (
        <>
          <Paper variant="outlined" sx={{ p: 1 }}>
            <Stack spacing={1}>
              <Tabs
                value={activeTab}
                onChange={(_, next) => setActiveTab(next)}
                variant="scrollable"
                scrollButtons="auto"
              >
                <Tab value="PENDING" label="Pending" />
                <Tab value="COMPLETED" label="Completed" />
                <Tab value="ALL" label="All" />
              </Tabs>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>{tabLabel(activeTab, counts)}</Typography>
                {activeTab === "PENDING" ? <Chip size="small" color="warning" label={`${counts.pendingReports} reports`} /> : null}
                {activeTab === "COMPLETED" ? <Chip size="small" color="success" label={`${counts.completedReports} reports`} /> : null}
                {activeTab === "ALL" ? <Chip size="small" label={`${counts.allReports} reports`} /> : null}
              </Stack>
            </Stack>
          </Paper>

          {visibleEntries.length ? (
            <>
              <Paper variant="outlined" sx={{ p: 1 }}>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                    {activeTab === "PENDING" ? "Pending reports" : activeTab === "COMPLETED" ? "Completed reports" : "All reports"}
                  </Typography>
                  {visibleEntries.map((entry) => {
                    const isSelected = entry.document.id === selectedEntry?.document.id;
                    const isCompletedEntry = Boolean(entry.review.reviewCompleted || entry.document.aiExtractionReviewedAt);
                    const label = reportLabel(entry.document);
                    const suffix = activeTab === "PENDING"
                      ? `${entry.review.pendingCount} pending`
                      : isCompletedEntry
                        ? `${entry.review.findings.length} reviewed`
                        : `${entry.review.findings.length} findings`;
                    return (
                      <Button
                        key={entry.document.id}
                        size="small"
                        variant={isSelected ? "contained" : "outlined"}
                        onClick={() => {
                          setSelectedReportId(entry.document.id);
                          setSearchParams({ documentId: entry.document.id }, { replace: true });
                        }}
                      >
                        {label} · {suffix}
                      </Button>
                    );
                  })}
                </Stack>
              </Paper>

              {selectedEntry && review ? (
                <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "minmax(0, 1fr) minmax(0, 1fr)" }, gap: 1.5, flex: 1, minHeight: 0 }}>
                  <Card variant="outlined" sx={{ minHeight: 0, display: "flex", flexDirection: "column" }}>
                    <CardContent sx={{ display: "flex", flexDirection: "column", minHeight: 0, flex: 1 }}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Source report</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {selectedEntry.document.title}{selectedEntry.document.reportDate ? ` · ${selectedEntry.document.reportDate}` : ""} · {selectedEntry.document.mediaType}
                      </Typography>
                      <Box sx={{ flex: 1, minHeight: 0, mt: 1, overflow: "auto", bgcolor: "grey.100" }}>
                        {viewUrl ? (
                          <Box component="iframe" title="Source report" src={viewUrl} sx={{ width: "100%", height: "100%", minHeight: 560, border: 0 }} />
                        ) : (
                          <Typography p={2}>Source preview unavailable.</Typography>
                        )}
                      </Box>
                    </CardContent>
                  </Card>

                  <Card variant="outlined" sx={{ minHeight: 0, display: "flex", flexDirection: "column" }}>
                    <CardContent sx={{ pb: 1, flex: "0 0 auto" }}>
                      <Stack spacing={1}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center" gap={1}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Extracted findings</Typography>
                          <Chip color={reviewCompleted ? "success" : "warning"} label={reviewCompleted ? "Completed" : `${pendingCount} pending`} />
                        </Stack>
                        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                          {reviewCompleted ? (
                            <Chip size="small" color="success" label={`Review completed${selectedReviewedBy ? ` by ${selectedReviewedBy}` : ""}${selectedReviewedAt ? ` · ${formatDateTime(selectedReviewedAt)}` : ""}`} />
                          ) : (
                            <Chip size="small" color="warning" label={pendingInfo} />
                          )}
                          {reviewCompleted && selectedReviewedById ? <Chip size="small" variant="outlined" label={`Reviewer ID: ${selectedReviewedById}`} /> : null}
                        </Stack>
                      </Stack>
                    </CardContent>
                    <Divider />
                    <Box sx={{ flex: 1, minHeight: 0, overflowY: "auto", p: 1.5 }}>
                      <Stack spacing={1}>
                        {!reviewCompleted && pendingCount > 0 ? <Alert severity="info">{pendingInfo}</Alert> : null}
                        {reviewCompleted ? <Alert severity="success">{pendingInfo}</Alert> : null}
                        {review.findings.map((finding) => {
                          const pending = !reviewCompleted && isPending(finding);
                          const values = draft[finding.id] || { value: finding.value || "", unit: finding.unit || "", referenceRange: finding.referenceRange || "", flag: finding.flag || "" };
                          const decision = findingDecisionLabel(finding, reviewCompleted);
                          return (
                            <Card key={finding.id} variant="outlined">
                              <CardContent>
                                <Stack spacing={1}>
                                  <Stack direction="row" justifyContent="space-between" gap={1} alignItems="flex-start">
                                    <Box>
                                      <Typography sx={{ fontWeight: 800 }}>{finding.findingName}</Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        {reviewCompleted && finding.decision === "REJECTED" ? "Rejected finding excluded from trusted patient history." : pending ? "Pending Verification" : decision}
                                      </Typography>
                                    </Box>
                                    <Chip size="small" color={pending ? "warning" : reviewCompleted ? "success" : "default"} label={decision} />
                                  </Stack>

                                  {reviewCompleted ? (
                                    <Stack spacing={0.5}>
                                      <Typography variant="body2">
                                        Original extracted: {finding.originalValue || "Not recorded"}{finding.originalUnit ? ` ${finding.originalUnit}` : ""}
                                        {finding.originalReferenceRange ? ` · Ref: ${finding.originalReferenceRange}` : ""}
                                      </Typography>
                                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                        Doctor verified: {finding.value || "Not recorded"}{finding.unit ? ` ${finding.unit}` : ""}
                                        {finding.referenceRange ? ` · Ref: ${finding.referenceRange}` : ""}
                                        {finding.flag ? ` · ${finding.flag}` : ""}
                                      </Typography>
                                      {finding.decision === "EDITED" ? (
                                        <Alert severity="info" sx={{ py: 0.5 }}>
                                          AI extracted: {finding.originalValue || "Not recorded"}{finding.originalUnit ? ` ${finding.originalUnit}` : ""} · Doctor verified: {finding.value || "Not recorded"}{finding.unit ? ` ${finding.unit}` : ""}
                                        </Alert>
                                      ) : null}
                                    </Stack>
                                  ) : editing === finding.id ? (
                                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                                      {(["value", "unit", "referenceRange", "flag"] as const).map((key) => (
                                        <TextField
                                          key={key}
                                          size="small"
                                          label={key === "referenceRange" ? "Reference range" : key}
                                          value={values[key]}
                                          onChange={(event) => setDraft((current) => ({ ...current, [finding.id]: { ...values, [key]: event.target.value } }))}
                                        />
                                      ))}
                                    </Stack>
                                  ) : (
                                    <Typography variant="body2">
                                      {findingDisplayValue(finding)}
                                      {finding.referenceRange ? ` · Ref: ${finding.referenceRange}` : ""}
                                      {finding.flag ? ` · ${finding.flag}` : ""}
                                    </Typography>
                                  )}

                                  {finding.evidenceText ? <Typography variant="caption" color="text.secondary">Evidence: {finding.evidenceText}</Typography> : null}

                                  {pending ? (
                                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" justifyContent="flex-end">
                                      <Button size="small" onClick={() => setEditing(editing === finding.id ? null : finding.id)}>
                                        {editing === finding.id ? "Cancel" : "Edit"}
                                      </Button>
                                      <Button size="small" variant="outlined" disabled={Boolean(busyId)} onClick={() => void decide(finding, "REJECTED")}>Reject</Button>
                                      <Button size="small" variant="contained" disabled={Boolean(busyId)} onClick={() => void decide(finding, editing === finding.id ? "EDITED" : "CONFIRMED")}>
                                        {editing === finding.id ? "Save & verify" : "Confirm"}
                                      </Button>
                                    </Stack>
                                  ) : null}
                                </Stack>
                              </CardContent>
                            </Card>
                          );
                        })}
                      </Stack>
                    </Box>
                  </Card>
                </Box>
              ) : (
                <Alert severity="info">No reports are available for the selected tab.</Alert>
              )}

              <Paper variant="outlined" sx={{ position: "sticky", bottom: 0, zIndex: 2, p: 1, bgcolor: "background.paper" }}>
                <Stack direction="row" justifyContent="space-between" gap={1}>
                  <Button onClick={() => navigate(`/consultations/${consultationId}`)}>Back to consultation</Button>
                  <Button variant="contained" disabled={!review || pendingCount > 0 || reviewCompleted || Boolean(busyId)} onClick={() => void complete()}>
                    Complete Review
                  </Button>
                </Stack>
              </Paper>
            </>
          ) : (
            <Alert severity="info">
              {activeTab === "PENDING"
                ? "No pending AI-extracted findings were found for this consultation."
                : activeTab === "COMPLETED"
                  ? "No completed AI findings review history has been found for this consultation."
                  : "No AI findings history was found for this consultation."}
            </Alert>
          )}
        </>
      ) : null}
    </Stack>
  );
}
