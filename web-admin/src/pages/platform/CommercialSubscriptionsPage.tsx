import * as React from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
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

import { useAuth } from "../../auth/useAuth";
import {
  activateCommercialSubscription,
  cancelCommercialSubscription,
  getCommercialSubscription,
  getCommercialSubscriptionStatusCounts,
  getPlatformTenants,
  listCommercialPlanTemplates,
  listCommercialSubscriptions,
  pauseCommercialSubscription,
  replaceCommercialSubscription,
  resumeCommercialSubscription,
  createCommercialSubscription,
  type CommercialPlanTemplateSummary,
  type CommercialSubscriptionDetail,
  type CommercialSubscriptionStatus,
  type CommercialSubscriptionSummary,
  type CommercialSubscriptionStatusCounts,
  type PlatformTenant,
} from "../../api/clinicApi";
import CommercialSubscriptionAssignmentDialog from "./CommercialSubscriptionAssignmentDialog";

type FilterState = {
  search: string;
  tenantId: string;
  planTemplateId: string;
  status: CommercialSubscriptionStatus | "";
};

type LifecycleAction = "activate" | "pause" | "resume" | "cancel";

function formatDate(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(parsed);
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

function statusColor(status: CommercialSubscriptionStatus) {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "SCHEDULED":
      return "info";
    case "PAUSED":
      return "warning";
    case "EXPIRED":
    case "CANCELLED":
    case "SUPERSEDED":
      return "default";
    default:
      return "secondary";
  }
}

function emptyCounts(): CommercialSubscriptionStatusCounts {
  return { activeCount: 0, scheduledCount: 0, pausedCount: 0, expiredCount: 0, cancelledCount: 0 };
}

function parseSubscriptionStatus(value: string | null): CommercialSubscriptionStatus | "" {
  switch (value) {
    case "DRAFT":
    case "ACTIVE":
    case "SCHEDULED":
    case "PAUSED":
    case "EXPIRED":
    case "CANCELLED":
    case "SUPERSEDED":
      return value;
    default:
      return "";
  }
}

export default function CommercialSubscriptionsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const params = useParams<{ subscriptionId?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [statusCounts, setStatusCounts] = React.useState<CommercialSubscriptionStatusCounts>(emptyCounts());
  const [subscriptions, setSubscriptions] = React.useState<CommercialSubscriptionSummary[]>([]);
  const [subscriptionContext, setSubscriptionContext] = React.useState<CommercialSubscriptionSummary[]>([]);
  const [subscriptionDetail, setSubscriptionDetail] = React.useState<CommercialSubscriptionDetail | null>(null);
  const [tenants, setTenants] = React.useState<PlatformTenant[]>([]);
  const [templates, setTemplates] = React.useState<CommercialPlanTemplateSummary[]>([]);
  const [assignmentOpen, setAssignmentOpen] = React.useState(false);
  const [assignmentMode, setAssignmentMode] = React.useState<"create" | "replace">("create");
  const [assignmentTarget, setAssignmentTarget] = React.useState<CommercialSubscriptionDetail | null>(null);
  const [saving, setSaving] = React.useState(false);
  const [toast, setToast] = React.useState<string | null>(null);
  const [actionDialog, setActionDialog] = React.useState<{ open: boolean; action: LifecycleAction | null; target: CommercialSubscriptionSummary | null }>({ open: false, action: null, target: null });
  const canManageSubscriptions = auth.hasPermission("commercial.subscriptions.manage");

  const filters: FilterState = {
    search: searchParams.get("search") || "",
    tenantId: searchParams.get("tenantId") || "",
    planTemplateId: searchParams.get("planTemplateId") || "",
    status: parseSubscriptionStatus(searchParams.get("status")),
  };
  const subscriptionId = params.subscriptionId || null;

  const refresh = React.useCallback(async () => {
    if (!auth.accessToken) return;
    setLoading(true);
    setError(null);
    try {
      const [counts, tenantRows, templatePage, subscriptionPage, subscriptionContextPage, detail] = await Promise.all([
        getCommercialSubscriptionStatusCounts(auth.accessToken),
        getPlatformTenants(auth.accessToken),
        listCommercialPlanTemplates(auth.accessToken, { size: 200 }),
        listCommercialSubscriptions(auth.accessToken, {
          search: filters.search || undefined,
          tenantId: filters.tenantId || null,
          planTemplateId: filters.planTemplateId || null,
          status: filters.status || null,
          page: 0,
          size: 50,
        }),
        listCommercialSubscriptions(auth.accessToken, { page: 0, size: 1000 }),
        subscriptionId ? getCommercialSubscription(auth.accessToken, subscriptionId) : Promise.resolve(null),
      ]);
      setStatusCounts(counts);
      setTenants(tenantRows);
      setTemplates(templatePage.items.filter((template) => template.latestPublishedVersionNumber != null));
      setSubscriptions(subscriptionPage.items);
      setSubscriptionContext(subscriptionContextPage.items);
      setSubscriptionDetail(detail);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load commercial subscriptions");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, filters.planTemplateId, filters.search, filters.status, filters.tenantId, subscriptionId]);

  React.useEffect(() => {
    void refresh();
  }, [refresh]);

  const selectedTenant = subscriptionDetail ? tenants.find((tenant) => tenant.id === subscriptionDetail.tenantId) || null : null;
  const selectedTemplate = subscriptionDetail ? templates.find((template) => template.id === subscriptionDetail.planTemplateId) || null : null;

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  async function runLifecycle(action: LifecycleAction, target: CommercialSubscriptionSummary) {
    if (!auth.accessToken) return;
    setSaving(true);
    try {
      const request = { remarks: `${action} subscription` };
      if (action === "activate") {
        await activateCommercialSubscription(auth.accessToken, target.id, request);
      } else if (action === "pause") {
        await pauseCommercialSubscription(auth.accessToken, target.id, request);
      } else if (action === "resume") {
        await resumeCommercialSubscription(auth.accessToken, target.id, request);
      } else {
        await cancelCommercialSubscription(auth.accessToken, target.id, request);
      }
      setToast(`${target.displayName || target.planTemplateName} updated.`);
      setActionDialog({ open: false, action: null, target: null });
      await refresh();
      if (subscriptionId) {
        navigate(`/platform/commercial/subscriptions/${target.id}`, { replace: true });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update commercial subscription");
    } finally {
      setSaving(false);
    }
  }

  async function submitAssignment(payload: {
    tenantId: string;
    publishedVersionId: string;
    startDate: string;
    endDate?: string | null;
    autoRenew: boolean;
    displayName?: string | null;
    referenceNumber?: string | null;
    notes?: string | null;
  }) {
    if (!auth.accessToken) return;
    setSaving(true);
    try {
      const response = assignmentMode === "replace" && assignmentTarget
        ? await replaceCommercialSubscription(auth.accessToken, assignmentTarget.id, {
            publishedVersionId: payload.publishedVersionId,
            startDate: payload.startDate,
            endDate: payload.endDate,
            autoRenew: payload.autoRenew,
            displayName: payload.displayName,
            referenceNumber: payload.referenceNumber,
            notes: payload.notes,
          })
        : await createCommercialSubscription(auth.accessToken, {
            tenantId: payload.tenantId,
            publishedVersionId: payload.publishedVersionId,
            startDate: payload.startDate,
            endDate: payload.endDate,
            autoRenew: payload.autoRenew,
            displayName: payload.displayName,
            referenceNumber: payload.referenceNumber,
            notes: payload.notes,
          });
      setToast(`Subscription ${response.displayName || response.planTemplateName} saved.`);
      setAssignmentOpen(false);
      setAssignmentTarget(null);
      await refresh();
      navigate(`/platform/commercial/subscriptions/${response.id}`, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save commercial subscription");
    } finally {
      setSaving(false);
    }
  }

  const detailActions: Array<{ action: LifecycleAction; label: string; show: boolean }> = canManageSubscriptions && subscriptionDetail ? [
    { action: "activate", label: "Activate", show: subscriptionDetail.subscriptionStatus === "DRAFT" || subscriptionDetail.subscriptionStatus === "SCHEDULED" || subscriptionDetail.subscriptionStatus === "PAUSED" },
    { action: "pause", label: "Pause", show: subscriptionDetail.subscriptionStatus === "ACTIVE" },
    { action: "resume", label: "Resume", show: subscriptionDetail.subscriptionStatus === "PAUSED" },
    { action: "cancel", label: "Cancel", show: subscriptionDetail.subscriptionStatus !== "CANCELLED" && subscriptionDetail.subscriptionStatus !== "EXPIRED" && subscriptionDetail.subscriptionStatus !== "SUPERSEDED" },
  ] : [];

  const planOptions = templates;

  return (
    <Stack spacing={2.5}>
      <Stack spacing={0.75}>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>Commercial Subscriptions</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 840 }}>
          Commercial subscription records are design-time commercial assignments only. They do not change tenant runtime access yet.
        </Typography>
      </Stack>

      <Alert severity="info" variant="outlined">
        Commercial subscriptions are assignment records only. Existing legacy plan and module enforcement remains authoritative.
      </Alert>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Typography variant="body2" color="text.secondary">Loading commercial subscriptions…</Typography> : null}

      <Grid container spacing={2}>
        {[
          { key: "published", label: "Published Plans", value: planOptions.length },
          { key: "active", label: "Active Subscriptions", value: statusCounts.activeCount },
          { key: "scheduled", label: "Scheduled", value: statusCounts.scheduledCount },
          { key: "paused", label: "Paused", value: statusCounts.pausedCount },
          { key: "expired", label: "Expired", value: statusCounts.expiredCount },
          { key: "cancelled", label: "Cancelled", value: statusCounts.cancelledCount },
        ].map((card) => (
          <Grid key={card.key} size={{ xs: 12, sm: 6, md: 4 }}>
            <Card variant="outlined">
              <CardContent>
                <Stack spacing={0.75}>
                  <Chip size="small" label={card.label} color="primary" variant="outlined" sx={{ alignSelf: "flex-start" }} />
                  <Typography variant="h4" sx={{ fontWeight: 900 }}>{card.value}</Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={1.5}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1} flexWrap="wrap">
              <Typography variant="h6" sx={{ fontWeight: 900 }}>Subscriptions</Typography>
              <Stack direction="row" spacing={1}>
                {canManageSubscriptions ? (
                  <Button variant="contained" onClick={() => { setAssignmentMode("create"); setAssignmentTarget(null); setAssignmentOpen(true); }}>Assign Subscription</Button>
                ) : null}
                <Button variant="outlined" onClick={() => void refresh()}>Refresh</Button>
              </Stack>
            </Stack>

            <Stack direction="row" spacing={1} flexWrap="wrap">
              <TextField
                label="Search"
                value={filters.search}
                onChange={(event) => {
                  const next = new URLSearchParams(searchParams);
                  if (event.target.value) next.set("search", event.target.value); else next.delete("search");
                  setSearchParams(next, { replace: true });
                }}
                size="small"
                sx={{ minWidth: 240 }}
              />
              <TextField
                select
                label="Status"
                value={filters.status}
                onChange={(event) => {
                  const next = new URLSearchParams(searchParams);
                  if (event.target.value) next.set("status", String(event.target.value)); else next.delete("status");
                  setSearchParams(next, { replace: true });
                }}
                size="small"
                sx={{ minWidth: 180 }}
              >
                <MenuItem value="">All</MenuItem>
                {["DRAFT", "ACTIVE", "SCHEDULED", "PAUSED", "EXPIRED", "CANCELLED", "SUPERSEDED"].map((status) => (
                  <MenuItem key={status} value={status}>{status}</MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Tenant"
                value={filters.tenantId}
                onChange={(event) => {
                  const next = new URLSearchParams(searchParams);
                  if (event.target.value) next.set("tenantId", String(event.target.value)); else next.delete("tenantId");
                  setSearchParams(next, { replace: true });
                }}
                size="small"
                sx={{ minWidth: 220 }}
              >
                <MenuItem value="">All tenants</MenuItem>
                {tenants.map((tenant) => (
                  <MenuItem key={tenant.id} value={tenant.id}>{tenant.name}</MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Plan"
                value={filters.planTemplateId}
                onChange={(event) => {
                  const next = new URLSearchParams(searchParams);
                  if (event.target.value) next.set("planTemplateId", String(event.target.value)); else next.delete("planTemplateId");
                  setSearchParams(next, { replace: true });
                }}
                size="small"
                sx={{ minWidth: 220 }}
              >
                <MenuItem value="">All plans</MenuItem>
                {planOptions.map((template) => (
                  <MenuItem key={template.id} value={template.id}>{template.name}</MenuItem>
                ))}
              </TextField>
            </Stack>

            <TableContainer>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Tenant</TableCell>
                    <TableCell>Published Plan</TableCell>
                    <TableCell>Current Status</TableCell>
                    <TableCell>Effective Dates</TableCell>
                    <TableCell>Auto Renew</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {subscriptions.map((row) => (
                    <TableRow key={row.id} hover selected={subscriptionId === row.id}>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{tenants.find((tenant) => tenant.id === row.tenantId)?.name || "Unknown tenant"}</Typography>
                          <Typography variant="caption" color="text.secondary">Code: {tenants.find((tenant) => tenant.id === row.tenantId)?.code || "—"}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.planTemplateName}</Typography>
                          <Typography variant="caption" color="text.secondary">{row.publishedVersionLabel}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell><Chip size="small" label={row.subscriptionStatus} color={statusColor(row.subscriptionStatus)} /></TableCell>
                      <TableCell>{formatDate(row.startDate)}{row.endDate ? ` to ${formatDate(row.endDate)}` : ""}</TableCell>
                      <TableCell>{row.autoRenew ? "Yes" : "No"}</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap">
                          <Button size="small" onClick={() => navigate(`/platform/commercial/subscriptions/${row.id}`)}>View</Button>
                          {canManageSubscriptions ? (
                            <>
                              <Button
                                size="small"
                                onClick={() => {
                                  setAssignmentMode("replace");
                                  setSaving(true);
                                  void (async () => {
                                    try {
                                      const detail = await getCommercialSubscription(auth.accessToken || "", row.id);
                                      setAssignmentTarget(detail);
                                      setAssignmentOpen(true);
                                    } catch (err) {
                                      setError(err instanceof Error ? err.message : "Failed to load subscription details");
                                    } finally {
                                      setSaving(false);
                                    }
                                  })();
                                }}
                              >
                                Replace
                              </Button>
                              {row.subscriptionStatus === "DRAFT" || row.subscriptionStatus === "SCHEDULED" || row.subscriptionStatus === "PAUSED" ? <Button size="small" onClick={() => setActionDialog({ open: true, action: "activate", target: row })}>Activate</Button> : null}
                              {row.subscriptionStatus === "ACTIVE" ? <Button size="small" onClick={() => setActionDialog({ open: true, action: "pause", target: row })}>Pause</Button> : null}
                              {row.subscriptionStatus === "PAUSED" ? <Button size="small" onClick={() => setActionDialog({ open: true, action: "resume", target: row })}>Resume</Button> : null}
                              {row.subscriptionStatus !== "CANCELLED" && row.subscriptionStatus !== "EXPIRED" && row.subscriptionStatus !== "SUPERSEDED" ? <Button size="small" onClick={() => setActionDialog({ open: true, action: "cancel", target: row })}>Cancel</Button> : null}
                            </>
                          ) : null}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Stack>
        </CardContent>
      </Card>

      {subscriptionDetail ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 5 }}>
            <Card variant="outlined">
              <CardContent>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>Subscription Summary</Typography>
                  <Typography variant="body2" color="text.secondary">Tenant</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 700 }}>{selectedTenant?.name || "Unknown tenant"}</Typography>
                  <Typography variant="body2" color="text.secondary">Published Plan</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 700 }}>{subscriptionDetail.planTemplateName}</Typography>
                  <Typography variant="body2" color="text.secondary">Published Version</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 700 }}>{subscriptionDetail.publishedVersionLabel}</Typography>
                  <Typography variant="body2" color="text.secondary">Status</Typography>
                  <Chip size="small" label={subscriptionDetail.subscriptionStatus} color={statusColor(subscriptionDetail.subscriptionStatus)} sx={{ alignSelf: "flex-start" }} />
                  <Typography variant="body2" color="text.secondary">Effective Dates</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 700 }}>{formatDate(subscriptionDetail.startDate)}{subscriptionDetail.endDate ? ` to ${formatDate(subscriptionDetail.endDate)}` : ""}</Typography>
                  <Typography variant="body2" color="text.secondary">Reference</Typography>
                  <Typography variant="body1" sx={{ fontWeight: 700 }}>{subscriptionDetail.referenceNumber || "—"}</Typography>
                  <Typography variant="body2" color="text.secondary">Notes</Typography>
                  <Typography variant="body1" sx={{ whiteSpace: "pre-wrap" }}>{subscriptionDetail.notes || "—"}</Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 7 }}>
            <Card variant="outlined">
              <CardContent>
                <Stack spacing={1.5}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>Timeline</Typography>
                  {subscriptionDetail.history.length > 0 ? subscriptionDetail.history.map((entry) => (
                    <Paper key={entry.id} variant="outlined" sx={{ p: 1.5 }}>
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{entry.eventType.replaceAll("_", " ")}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {entry.previousStatus || "—"} → {entry.newStatus} · {formatDateTime(entry.performedAt)}
                        </Typography>
                        {entry.remarks ? <Typography variant="body2">{entry.remarks}</Typography> : null}
                      </Stack>
                    </Paper>
                  )) : <Alert severity="info">No history available yet.</Alert>}
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {detailActions.map((item) => (
                      item.show ? (
                        <Button key={item.action} variant="outlined" onClick={() => setActionDialog({ open: true, action: item.action, target: subscriptionDetail })}>
                          {item.label}
                        </Button>
                      ) : null
                    ))}
                    {canManageSubscriptions ? (
                      <Button variant="outlined" onClick={() => { setAssignmentMode("replace"); setAssignmentTarget(subscriptionDetail); setAssignmentOpen(true); }}>
                        Replace
                      </Button>
                    ) : null}
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      <CommercialSubscriptionAssignmentDialog
        open={assignmentOpen}
        mode={assignmentMode}
        token={auth.accessToken || ""}
        tenants={tenants}
        tenantSubscriptions={subscriptionContext}
        templates={templates}
        initialSubscription={assignmentTarget}
        canManage={canManageSubscriptions}
        submitting={saving}
        error={error}
        onClose={() => {
          setAssignmentOpen(false);
          setAssignmentTarget(null);
        }}
        onSubmit={submitAssignment}
      />

      <Dialog open={actionDialog.open} onClose={() => setActionDialog({ open: false, action: null, target: null })} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 900 }}>{actionDialog.action ? `${actionDialog.action[0].toUpperCase()}${actionDialog.action.slice(1)}` : "Update"} subscription?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            {actionDialog.target ? `${actionDialog.target.displayName || actionDialog.target.planTemplateName} will be updated.` : "This action will update the subscription lifecycle."}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialog({ open: false, action: null, target: null })}>Keep Editing</Button>
          <Button
            variant="contained"
            color="warning"
            onClick={() => actionDialog.action && actionDialog.target ? void runLifecycle(actionDialog.action, actionDialog.target) : null}
            disabled={saving}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(toast)} onClose={() => setToast(null)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 900 }}>Success</DialogTitle>
        <DialogContent>
          <Typography variant="body2">{toast}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setToast(null)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
