import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Collapse,
  DialogContentText,
  Divider,
  Drawer,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  MenuItem,
  LinearProgress,
  Select,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tabs,
  Tab,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import TuneRoundedIcon from "@mui/icons-material/TuneRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import NotificationsRoundedIcon from "@mui/icons-material/NotificationsRounded";
import MonitorHeartRoundedIcon from "@mui/icons-material/MonitorHeartRounded";
import AssessmentRoundedIcon from "@mui/icons-material/AssessmentRounded";
import HistoryRoundedIcon from "@mui/icons-material/HistoryRounded";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import ExpandLessRoundedIcon from "@mui/icons-material/ExpandLessRounded";
import CheckCircleRoundedIcon from "@mui/icons-material/CheckCircleRounded";
import ErrorRoundedIcon from "@mui/icons-material/ErrorRounded";
import InfoRoundedIcon from "@mui/icons-material/InfoRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import TrendingUpRoundedIcon from "@mui/icons-material/TrendingUpRounded";
import TrendingDownRoundedIcon from "@mui/icons-material/TrendingDownRounded";
import RemoveRoundedIcon from "@mui/icons-material/RemoveRounded";
import AnalyticsRoundedIcon from "@mui/icons-material/AnalyticsRounded";
import DonutSmallRoundedIcon from "@mui/icons-material/DonutSmallRounded";
import ShowChartRoundedIcon from "@mui/icons-material/ShowChartRounded";
import TableRowsRoundedIcon from "@mui/icons-material/TableRowsRounded";
import HealthAndSafetyRoundedIcon from "@mui/icons-material/HealthAndSafetyRounded";
import LocalFireDepartmentRoundedIcon from "@mui/icons-material/LocalFireDepartmentRounded";
import HourglassBottomRoundedIcon from "@mui/icons-material/HourglassBottomRounded";
import InsightsRoundedIcon from "@mui/icons-material/InsightsRounded";
import { Link, useSearchParams } from "react-router-dom";

import { useAuth } from "../../auth/useAuth";
import { ConfirmationDialog } from "../../components/clinical/ConfirmationDialog";
import {
  bulkRetryNotificationOperations,
  getNotificationOperationsAnalytics,
  getNotificationOperationsAudit,
  getNotificationOperationsDelivery,
  getNotificationOperationsDeliveries,
  getNotificationOperationsFailures,
  getNotificationOperationsProviders,
  getNotificationOperationsSummary,
  getPlatformTenants,
  type NotificationOperationsAnalyticsResponse,
  type NotificationOperationsAuditResponse,
  type NotificationOperationsChannelRow,
  type NotificationOperationsDeliveryRow,
  type NotificationOperationsPageResponse,
  type NotificationOperationsPeriod,
  type NotificationOperationsProviderRow,
  type NotificationOperationsQuery,
  type NotificationOperationsRetryResponse,
  type NotificationOperationsSummaryResponse,
  type PlatformTenant,
} from "../../api/clinicApi";
import {
  CHANNEL_ORDER,
  NOTIFICATION_OPERATION_PERIODS,
  NOTIFICATION_OPERATION_TABS,
  type NotificationOperationsTab,
  categoryLabel,
  channelDisplayStatus,
  channelBadgeLabel,
  channelBadgeTitle,
  channelLabel,
  channelRowOrder,
  channelSummaryLabel,
  eventLabel,
  humanizeAuditAction,
  humanizeAuditActor,
  formatDateOnly,
  formatRatio,
  formatTimestamp,
  normalizeChannel,
  providerConfigurationLabel,
  providerStatusLabel,
  overallStatusColor,
  overallStatusLabel,
  type ChannelTone,
} from "./notificationOperationsModel";

const DEFAULT_PAGE_SIZE = 10;

type QueryState = NotificationOperationsQuery;

function initialQueryState(searchParams: URLSearchParams, tenantId: string): QueryState {
  return {
    tenantId: searchParams.get("tenantId") || tenantId || "",
    period: (searchParams.get("period") as NotificationOperationsPeriod | null) || "LAST_7_DAYS",
    from: searchParams.get("from") || null,
    to: searchParams.get("to") || null,
    status: searchParams.get("status") || null,
    eventType: (searchParams.get("eventType") as NotificationOperationsQuery["eventType"]) || null,
    channel: (searchParams.get("channel") as NotificationOperationsQuery["channel"]) || null,
    channelStatus: searchParams.get("channelStatus") || null,
    patientName: searchParams.get("patientName") || null,
    patientReference: searchParams.get("patientReference") || null,
    businessReference: searchParams.get("businessReference") || null,
    provider: searchParams.get("provider") || null,
    hasFailure: searchParams.get("hasFailure") ? searchParams.get("hasFailure") === "true" : null,
    hasRetry: searchParams.get("hasRetry") ? searchParams.get("hasRetry") === "true" : null,
    sourceModule: searchParams.get("sourceModule") || null,
    search: searchParams.get("search") || null,
    page: Number(searchParams.get("page") || 0),
    size: Number(searchParams.get("size") || DEFAULT_PAGE_SIZE),
  };
}

function buildSearchParams(tab: NotificationOperationsTab, query: QueryState): URLSearchParams {
  const next = new URLSearchParams();
  next.set("tab", tab);
  if (query.tenantId) next.set("tenantId", query.tenantId);
  if (query.period) next.set("period", query.period);
  if (query.from) next.set("from", query.from);
  if (query.to) next.set("to", query.to);
  if (query.status) next.set("status", query.status);
  if (query.eventType) next.set("eventType", query.eventType);
  if (query.channel) next.set("channel", query.channel);
  if (query.channelStatus) next.set("channelStatus", query.channelStatus);
  if (query.patientName) next.set("patientName", query.patientName);
  if (query.patientReference) next.set("patientReference", query.patientReference);
  if (query.businessReference) next.set("businessReference", query.businessReference);
  if (query.provider) next.set("provider", query.provider);
  if (query.hasFailure !== null && query.hasFailure !== undefined) next.set("hasFailure", String(query.hasFailure));
  if (query.hasRetry !== null && query.hasRetry !== undefined) next.set("hasRetry", String(query.hasRetry));
  if (query.sourceModule) next.set("sourceModule", query.sourceModule);
  if (query.search) next.set("search", query.search);
  if (typeof query.page === "number") next.set("page", String(query.page));
  if (typeof query.size === "number") next.set("size", String(query.size));
  return next;
}

function toneIcon(tone: ChannelTone) {
  switch (tone) {
    case "success":
      return <CheckCircleRoundedIcon fontSize="inherit" color="success" />;
    case "warning":
      return <WarningAmberRoundedIcon fontSize="inherit" color="warning" />;
    case "error":
      return <ErrorRoundedIcon fontSize="inherit" color="error" />;
    default:
      return <InfoRoundedIcon fontSize="inherit" color="disabled" />;
  }
}

function statusChipColor(status: string) {
  switch (String(status)) {
    case "DELIVERED":
      return "success";
    case "PARTIAL":
      return "warning";
    case "PENDING":
      return "info";
    case "FAILED":
      return "error";
    default:
      return "default";
  }
}

function periodLabel(period: NotificationOperationsPeriod) {
  return NOTIFICATION_OPERATION_PERIODS.find((item) => item.key === period)?.label ?? period;
}

function summaryLabel(value: string | null | undefined) {
  if (!value) return "-";
  return value;
}

function seriesMax(points: Array<{ value: number }>) {
  return Math.max(1, ...points.map((point) => point.value));
}

export default function NotificationOperationsPage() {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const canRead = auth.hasPermission("notification.read") || auth.hasPermission("audit.read") || isPlatformAdmin || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("TENANT_ADMIN") || auth.rolesUpper.includes("AUDITOR") || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");
  const canRetry = auth.hasPermission("notification.retry") || isPlatformAdmin || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("TENANT_ADMIN") || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");

  const [query, setQuery] = React.useState<QueryState>(() => initialQueryState(searchParams, auth.tenantId ?? ""));
  const [activeTab, setActiveTab] = React.useState<NotificationOperationsTab>((searchParams.get("tab") as NotificationOperationsTab | null) || "overview");
  const [tenantOptions, setTenantOptions] = React.useState<PlatformTenant[]>([]);
  const [summary, setSummary] = React.useState<NotificationOperationsSummaryResponse | null>(null);
  const [deliveries, setDeliveries] = React.useState<NotificationOperationsPageResponse | null>(null);
  const [failures, setFailures] = React.useState<NotificationOperationsPageResponse | null>(null);
  const [providers, setProviders] = React.useState<NotificationOperationsProviderRow[]>([]);
  const [analytics, setAnalytics] = React.useState<NotificationOperationsAnalyticsResponse | null>(null);
  const [audit, setAudit] = React.useState<NotificationOperationsAuditResponse | null>(null);
  const [drawerRow, setDrawerRow] = React.useState<NotificationOperationsDeliveryRow | null>(null);
  const [drawerLoading, setDrawerLoading] = React.useState(false);
  const [auditExpandedId, setAuditExpandedId] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [selectedIds, setSelectedIds] = React.useState<string[]>([]);
  const [retryTarget, setRetryTarget] = React.useState<{ ids: string[]; label: string } | null>(null);
  const [working, setWorking] = React.useState(false);

  React.useEffect(() => {
    const next = buildSearchParams(activeTab, query);
    if (searchParams.toString() !== next.toString()) {
      setSearchParams(next, { replace: true });
    }
  }, [activeTab, query, searchParams, setSearchParams]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadTenants() {
      if (!auth.accessToken || !isPlatformAdmin) {
        return;
      }
      try {
        const rows = await getPlatformTenants(auth.accessToken);
        if (!cancelled) {
          setTenantOptions(rows);
        }
      } catch {
        if (!cancelled) {
          setTenantOptions([]);
        }
      }
    }
    void loadTenants();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, isPlatformAdmin]);

  React.useEffect(() => {
    if (!query.tenantId && isPlatformAdmin && tenantOptions.length > 0) {
      setQuery((current) => ({ ...current, tenantId: tenantOptions[0].id }));
    }
  }, [isPlatformAdmin, query.tenantId, tenantOptions]);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !query.tenantId || !canRead) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const base = { ...query, tenantId: query.tenantId };
      const [summaryRes, deliveriesRes, failuresRes, providersRes, analyticsRes, auditRes] = await Promise.all([
        getNotificationOperationsSummary(auth.accessToken, base),
        getNotificationOperationsDeliveries(auth.accessToken, base),
        getNotificationOperationsFailures(auth.accessToken, base),
        getNotificationOperationsProviders(auth.accessToken, base),
        getNotificationOperationsAnalytics(auth.accessToken, base),
        getNotificationOperationsAudit(auth.accessToken, base),
      ]);
      setSummary(summaryRes);
      setDeliveries(deliveriesRes);
      setFailures(failuresRes);
      setProviders(providersRes);
      setAnalytics(analyticsRes);
      setAudit(auditRes);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load notification operations");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, canRead, query]);

  React.useEffect(() => {
    void load();
  }, [load]);

  React.useEffect(() => {
    setSelectedIds([]);
  }, [activeTab, query.tenantId, query.page, query.size]);

  const selectedTenantName = React.useMemo(() => {
    if (!query.tenantId) {
      return null;
    }
    return tenantOptions.find((tenant) => tenant.id === query.tenantId)?.name ?? summary?.tenantName ?? null;
  }, [query.tenantId, summary?.tenantName, tenantOptions]);

  const updateQuery = (patch: Partial<QueryState>) => {
    setQuery((current) => ({ ...current, ...patch }));
  };

  const setTab = (tab: NotificationOperationsTab) => setActiveTab(tab);

  const onOpenDelivery = async (logicalNotificationId: string) => {
    if (!auth.accessToken || !query.tenantId) {
      return;
    }
    setDrawerLoading(true);
    try {
      const row = await getNotificationOperationsDelivery(auth.accessToken, query, logicalNotificationId);
      setDrawerRow(row);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load notification detail");
    } finally {
      setDrawerLoading(false);
    }
  };

  const onRetry = (ids: string[], label: string) => {
    if (!canRetry || ids.length === 0) {
      return;
    }
    setRetryTarget({ ids, label });
  };

  const confirmRetry = async () => {
    if (!auth.accessToken || !query.tenantId || !retryTarget) {
      return;
    }
    setWorking(true);
    try {
      await bulkRetryNotificationOperations(auth.accessToken, query.tenantId, retryTarget.ids);
      await load();
      setSelectedIds([]);
      setRetryTarget(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Retry failed");
    } finally {
      setWorking(false);
    }
  };

  if (!canRead) {
    return <Alert severity="error">You do not have access to notification operations.</Alert>;
  }

  if (!query.tenantId) {
    return (
      <Stack spacing={2}>
        <Alert severity={isPlatformAdmin ? "info" : "warning"}>
          {isPlatformAdmin
            ? "Select a tenant to inspect notification operations."
            : "No tenant is selected for this session."}
        </Alert>
        {isPlatformAdmin ? (
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Select tenant
                </Typography>
                <FormControl fullWidth>
                  <InputLabel id="notification-ops-tenant-label">Tenant</InputLabel>
                  <Select
                    labelId="notification-ops-tenant-label"
                    label="Tenant"
                    value=""
                    onChange={(event) => updateQuery({ tenantId: String(event.target.value) })}
                  >
                    {tenantOptions.map((tenant) => (
                      <MenuItem key={tenant.id} value={tenant.id}>
                        {tenant.name} ({tenant.code})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Stack>
            </CardContent>
          </Card>
        ) : null}
      </Stack>
    );
  }

  const currentPageRows = activeTab === "failures" ? failures?.items ?? [] : deliveries?.items ?? [];
  const currentPage = activeTab === "failures" ? failures?.page ?? 0 : deliveries?.page ?? 0;
  const currentPageSize = activeTab === "failures" ? failures?.size ?? DEFAULT_PAGE_SIZE : deliveries?.size ?? DEFAULT_PAGE_SIZE;
  const currentTotal = activeTab === "failures" ? failures?.totalElements ?? 0 : deliveries?.totalElements ?? 0;
  const currentTotalPages = activeTab === "failures" ? failures?.totalPages ?? 0 : deliveries?.totalPages ?? 0;
  const selectedTenantRow = summary ? summary.tenantName : selectedTenantName ?? query.tenantId;

  const activeRows = activeTab === "failures" ? failures?.items ?? [] : deliveries?.items ?? [];
  const selectedCount = selectedIds.length;
  const healthyProviders = providers.filter((provider) => provider.readinessStatus.toLowerCase().includes("healthy") || provider.configurationStatus.toLowerCase().includes("ready")).length;
  const topKpis = [
    {
      label: "Notification Success",
      value: summary ? `${summary.successRate.toFixed(0)}%` : "0%",
      helper: "Healthy notification deliveries",
      detail: summary ? `${summary.sentCount} delivered from ${summary.channelDeliveriesAttempted} channel rows` : "No delivery data yet",
      tone: summary ? (summary.successRate >= 95 ? "success" : summary.successRate >= 80 ? "warning" : "error") : "neutral",
      icon: <HealthAndSafetyRoundedIcon fontSize="small" />,
      trend: summary ? (summary.successRate >= 95 ? "Healthy" : summary.successRate >= 80 ? "Watch" : "At risk") : "No data",
    },
    {
      label: "Healthy Providers",
      value: `${healthyProviders} / ${providers.length || 4}`,
      helper: "Providers ready for delivery",
      detail: providers.length ? `${providers.length - healthyProviders} need attention` : "Readiness data not loaded",
      tone: healthyProviders > 0 ? "success" : "warning",
      icon: <MonitorHeartRoundedIcon fontSize="small" />,
      trend: providers.length ? `${healthyProviders} healthy` : "Pending",
    },
    {
      label: "Failed Deliveries",
      value: String(summary?.failedCount ?? 0),
      helper: "Rows needing investigation",
      detail: summary?.failedCount ? "Review failures & retries" : "No active failures",
      tone: (summary?.failedCount ?? 0) > 0 ? "error" : "success",
      icon: <LocalFireDepartmentRoundedIcon fontSize="small" />,
      trend: (summary?.failedCount ?? 0) > 0 ? "Investigate" : "Clear",
    },
    {
      label: "Pending Retry",
      value: String(summary?.pendingCount ?? 0),
      helper: "Queued or awaiting retry",
      detail: summary?.pendingCount ? "Work through the retry queue" : "Queue clear",
      tone: (summary?.pendingCount ?? 0) > 0 ? "warning" : "success",
      icon: <HourglassBottomRoundedIcon fontSize="small" />,
      trend: (summary?.pendingCount ?? 0) > 0 ? "In queue" : "Idle",
    },
  ] as const;
  const technicalKpis = [
    { label: "Logical Notifications", value: summary?.logicalNotificationsCreated ?? 0, helper: "Grouped business notifications", tone: "info" as const },
    { label: "Channel Deliveries", value: summary?.channelDeliveriesAttempted ?? 0, helper: "Per-channel delivery rows", tone: "info" as const },
    { label: "Retry Attempts", value: summary?.retryCount ?? 0, helper: "Persisted retry attempts", tone: "warning" as const },
    { label: "Stale Suppressed", value: summary?.staleDeliveriesSuppressed ?? 0, helper: "No-longer-applicable deliveries", tone: "default" as const },
    { label: "Average Latency", value: summary ? formatLatency(summary.averageDeliveryLatencyMs) : "-", helper: "Queued to sent latency", tone: "default" as const },
  ];

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Notification Operations
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Monitor delivery, investigate failures, review provider readiness, and safely retry eligible notification rows.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Button component={Link} to="/admin/notification-settings" variant="outlined" startIcon={<TuneRoundedIcon fontSize="small" />}>
            Notification Settings
          </Button>
          <Button component={Link} to="/admin/templates?templateType=NOTIFICATION" variant="outlined" startIcon={<OpenInNewRoundedIcon fontSize="small" />}>
            Templates
          </Button>
          <Button onClick={() => void load()} variant="contained" startIcon={<RefreshRoundedIcon fontSize="small" />}>
            Refresh
          </Button>
        </Stack>
      </Box>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Box sx={{ display: "flex", gap: 1.25, flexWrap: "wrap", alignItems: "center" }}>
              <NotificationsRoundedIcon fontSize="small" color="primary" />
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                Workflow
              </Typography>
              <Chip size="small" label="Monitor" />
              <Chip size="small" label="Investigate" />
              <Chip size="small" label="Resolve" />
              <Chip size="small" label="Retry" />
              <Chip size="small" label="Verify" />
            </Box>

            <Stack direction="row" spacing={2} flexWrap="wrap">
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <InputLabel id="notification-ops-period-label">Period</InputLabel>
                <Select
                  labelId="notification-ops-period-label"
                  label="Period"
                  value={query.period}
                  onChange={(event) => updateQuery({ period: String(event.target.value) as NotificationOperationsPeriod, page: 0 })}
                >
                  {NOTIFICATION_OPERATION_PERIODS.map((option) => (
                    <MenuItem key={option.key} value={option.key}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              {isPlatformAdmin ? (
                <FormControl size="small" sx={{ minWidth: 260 }}>
                  <InputLabel id="notification-ops-tenant-filter-label">Tenant</InputLabel>
                  <Select
                    labelId="notification-ops-tenant-filter-label"
                    label="Tenant"
                    value={query.tenantId}
                    onChange={(event) => updateQuery({ tenantId: String(event.target.value), page: 0 })}
                  >
                    {tenantOptions.map((tenant) => (
                      <MenuItem key={tenant.id} value={tenant.id}>
                        {tenant.name} ({tenant.code})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              ) : null}
              <TextField
                size="small"
                label="From"
                type="date"
                value={query.from ? query.from.slice(0, 10) : ""}
                InputLabelProps={{ shrink: true }}
                onChange={(event) => updateQuery({ from: event.target.value ? `${event.target.value}T00:00:00Z` : null, page: 0 })}
              />
              <TextField
                size="small"
                label="To"
                type="date"
                value={query.to ? query.to.slice(0, 10) : ""}
                InputLabelProps={{ shrink: true }}
                onChange={(event) => updateQuery({ to: event.target.value ? `${event.target.value}T23:59:59Z` : null, page: 0 })}
              />
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <NotificationOperationsLoadingState isPlatformAdmin={isPlatformAdmin} /> : null}

      <Grid container spacing={2}>
        {topKpis.map((kpi) => (
          <Grid key={kpi.label} size={{ xs: 12, sm: 6, lg: 3 }}>
            <OperationalKpiCard {...kpi} />
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        {technicalKpis.map((kpi) => (
          <Grid key={kpi.label} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
            <TechnicalKpiCard {...kpi} />
          </Grid>
        ))}
      </Grid>

      <Card>
        <CardContent>
          <Tabs
            value={activeTab}
            onChange={(_, value) => setTab(value as NotificationOperationsTab)}
            variant="scrollable"
            scrollButtons="auto"
            aria-label="Notification operations tabs"
          >
            {NOTIFICATION_OPERATION_TABS.map((tab) => (
              <Tab key={tab.key} value={tab.key} label={tab.label} />
            ))}
          </Tabs>
        </CardContent>
      </Card>

      {activeTab === "overview" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <AssessmentRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Detailed metrics</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Technical counts that support operational review.
                    </Typography>
                  </Box>
                  <Grid container spacing={2}>
                    {(summary?.kpis ?? []).map((kpi) => (
                      <Grid key={kpi.label} size={{ xs: 12, sm: 6, md: 4 }}>
                        <KpiCard label={kpi.label} value={kpi.value} helper={kpi.helper} />
                      </Grid>
                    ))}
                  </Grid>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <MonitorHeartRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Scheduler & queue</Typography>
                  </Box>
                  <SmallRow label="Enabled" value={summary?.scheduler.enabled ? "Yes" : "No"} />
                  <SmallRow label="Fixed delay" value={summary?.scheduler.fixedDelay ?? "-"} />
                  <SmallRow label="Reminder enabled" value={summary?.scheduler.appointmentReminderEnabled ? "Yes" : "No"} />
                  <SmallRow label="Reminder window" value={summary ? `${summary.scheduler.appointmentReminderHoursBefore}h before / ${summary.scheduler.appointmentReminderGraceMinutes}m grace` : "-"} />
                  <SmallRow label="Outbox pending" value={summary?.scheduler.outboxPendingCount ?? 0} />
                  <SmallRow label="Outbox failed" value={summary?.scheduler.outboxFailedCount ?? 0} />
                  <SmallRow label="Tenant" value={selectedTenantRow ?? "-"} />
                  <SmallRow label="Period" value={summary ? periodLabel((query.period as NotificationOperationsPeriod) || "LAST_7_DAYS") : "-"} />
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {activeTab === "deliveries" || activeTab === "failures" ? (
        <Card>
          <CardContent>
            <Stack spacing={2}>
              <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>{activeTab === "failures" ? "Failures & Retries" : "Deliveries"}</Typography>
                {activeTab === "failures" ? (
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="body2" color="text.secondary">{selectedCount} selected</Typography>
                    <Button
                      variant="contained"
                      disabled={!selectedCount || !canRetry}
                      onClick={() => onRetry(selectedIds, `${selectedCount} selected delivery${selectedCount === 1 ? "" : "ies"}`)}
                    >
                      Retry selected
                    </Button>
                  </Stack>
                ) : null}
              </Box>

              <FiltersBar
                query={query}
                isPlatformAdmin={isPlatformAdmin}
                tenantOptions={tenantOptions}
                onChange={updateQuery}
              />

              <TableContainer sx={{ maxHeight: 720 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      {activeTab === "failures" ? <TableCell padding="checkbox" /> : null}
                      <TableCell>Date / time</TableCell>
                      {isPlatformAdmin ? <TableCell>Tenant</TableCell> : null}
                      <TableCell>Notification type</TableCell>
                      <TableCell>Recipient</TableCell>
                      <TableCell>Business reference</TableCell>
                      <TableCell>Overall status</TableCell>
                      <TableCell>Channels</TableCell>
                      <TableCell>Retry count</TableCell>
                      <TableCell>Last updated</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {currentPageRows.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={(isPlatformAdmin ? 10 : 9) + (activeTab === "failures" ? 1 : 0)}>
                          <Alert severity="info">No notification rows matched the current filters.</Alert>
                        </TableCell>
                      </TableRow>
                    ) : currentPageRows.map((row) => {
                      const selected = selectedIds.includes(row.logicalNotificationId);
                      const channels = channelRowOrder(row.deliveries);
                      return (
                        <React.Fragment key={row.logicalNotificationId}>
                          <TableRow hover>
                            {activeTab === "failures" ? (
                              <TableCell padding="checkbox">
                                <Checkbox
                                  checked={selected}
                                  onChange={(_, checked) => {
                                    setSelectedIds((current) => checked ? Array.from(new Set([...current, row.logicalNotificationId])) : current.filter((id) => id !== row.logicalNotificationId));
                                  }}
                                />
                              </TableCell>
                            ) : null}
                            <TableCell>{formatTimestamp(row.queuedAt)}</TableCell>
                            {isPlatformAdmin ? <TableCell>{row.tenantName}</TableCell> : null}
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.eventLabel}</Typography>
                                <Typography variant="caption" color="text.secondary">{row.category}</Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{row.patientName}</TableCell>
                            <TableCell>{summaryLabel(row.businessReference)}</TableCell>
                            <TableCell><Chip size="small" color={statusChipColor(row.overallStatus)} label={overallStatusLabel(row.overallStatus)} /></TableCell>
                            <TableCell>
                              <Tooltip title={channelSummaryLabel(row)} arrow>
                                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ maxWidth: 560, rowGap: 0.75 }}>
                                  {CHANNEL_ORDER.map((channel) => {
                                    const delivery = channels.find((entry) => normalizeChannel(entry.channel) === channel) ?? null;
                                    const presentation = channelDisplayStatus(channel, delivery);
                                    const chipTone = presentation.tone;
                                    return (
                                      <Chip
                                        key={channel}
                                        size="small"
                                        icon={toneIcon(chipTone)}
                                        label={channelBadgeLabel(channel, delivery)}
                                        variant={chipTone === "neutral" ? "outlined" : "filled"}
                                        color={chipTone === "success" ? "success" : chipTone === "warning" ? "warning" : chipTone === "error" ? "error" : "default"}
                                        title={channelBadgeTitle(channel, delivery)}
                                        aria-label={presentation.title}
                                        sx={{ fontWeight: 700, "& .MuiChip-label": { whiteSpace: "nowrap" } }}
                                      />
                                    );
                                  })}
                                </Stack>
                              </Tooltip>
                            </TableCell>
                            <TableCell>{row.retryCount}</TableCell>
                            <TableCell>{formatTimestamp(row.lastActivityAt)}</TableCell>
                            <TableCell align="right">
                              <Stack direction="row" spacing={1} justifyContent="flex-end">
                                {activeTab === "failures" && canRetry ? (
                                  <Button
                                    size="small"
                                    disabled={!row.deliveries.some((item) => item.retryable)}
                                    onClick={() => onRetry(row.deliveries.filter((item) => item.retryable).map((item) => item.id), `${row.eventLabel} for ${row.patientName}`)}
                                  >
                                    Retry
                                  </Button>
                                ) : null}
                                <Button size="small" onClick={() => void onOpenDelivery(row.logicalNotificationId)}>
                                  Details
                                </Button>
                              </Stack>
                            </TableCell>
                          </TableRow>
                        </React.Fragment>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>

              <TablePagination
                component="div"
                count={currentTotal}
                page={currentPage}
                rowsPerPage={currentPageSize}
                onPageChange={(_, page) => updateQuery({ page })}
                onRowsPerPageChange={(event) => updateQuery({ size: Number(event.target.value), page: 0 })}
                rowsPerPageOptions={[10, 25, 50]}
              />
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === "providers" ? (
        <Card>
          <CardContent>
            <Stack spacing={2}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <MonitorHeartRoundedIcon fontSize="small" color="primary" />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Provider readiness</Typography>
                  <Typography variant="body2" color="text.secondary">Configuration readiness and live delivery signals where available.</Typography>
                </Box>
              </Box>
              <Grid container spacing={2}>
                {providers.length === 0 ? (
                  <Grid size={{ xs: 12 }}>
                    <Alert severity="info">No provider readiness data available for the selected period.</Alert>
                  </Grid>
                ) : providers.map((provider) => (
                  <Grid key={provider.key} size={{ xs: 12, lg: 6 }}>
                    <ProviderHealthCard provider={provider} />
                  </Grid>
                ))}
              </Grid>
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === "analytics" ? (
        <Grid container spacing={2}>
          {analytics ? (
            <>
              <Grid size={{ xs: 12, md: 6 }}>
                <LineSeriesCard title="Notifications over time" series={analytics.notificationsByDay} subtitle="Logical notifications created by day" />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <LineSeriesCard title="Success / failure trend" series={analytics.successFailureTrend} subtitle="Channel delivery outcome balance" />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <DonutSeriesCard title="Channel distribution" series={analytics.channelDistribution} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <DonutSeriesCard title="Status distribution" series={analytics.statusDistribution} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <BarSeriesCard title="Top notification categories" series={analytics.topCategories} subtitle="Highest-volume notification groups" />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <BarSeriesCard title="Top failure reasons" series={analytics.topFailureReasons} subtitle="Primary causes of failed or skipped deliveries" />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <ProviderPerformanceChartCard title="Provider performance" series={analytics.providerPerformance} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <StackedRetryOutcomeCard title="Retry outcomes" series={analytics.retryOutcomes} />
              </Grid>
            </>
          ) : (
            <Grid size={{ xs: 12 }}>
              <Alert severity="info">No analytics data available for the selected period.</Alert>
            </Grid>
          )}
        </Grid>
      ) : null}

      {activeTab === "audit" ? (
        <Card>
          <CardContent>
            <Stack spacing={2}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <HistoryRoundedIcon fontSize="small" color="primary" />
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Audit trail</Typography>
              </Box>
              {(audit?.items ?? []).length === 0 ? (
                <Alert severity="info">No audit activity recorded for the selected period.</Alert>
              ) : (
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell />
                        <TableCell>Time</TableCell>
                        <TableCell>Actor</TableCell>
                        <TableCell>Tenant</TableCell>
                        <TableCell>Action</TableCell>
                        <TableCell>Business reference</TableCell>
                        <TableCell>Result</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(audit?.items ?? []).map((row) => {
                        const expanded = auditExpandedId === row.auditEventId;
                        return (
                          <React.Fragment key={row.auditEventId}>
                            <TableRow hover>
                              <TableCell padding="checkbox">
                                <IconButton
                                  size="small"
                                  aria-label={expanded ? "Collapse audit details" : "Expand audit details"}
                                  onClick={() => setAuditExpandedId(expanded ? null : row.auditEventId)}
                                >
                                  {expanded ? <ExpandLessRoundedIcon fontSize="small" /> : <ExpandMoreRoundedIcon fontSize="small" />}
                                </IconButton>
                              </TableCell>
                              <TableCell>{formatTimestamp(row.occurredAt)}</TableCell>
                              <TableCell>{humanizeAuditActor(row.actor)}</TableCell>
                              <TableCell>{row.tenantName}</TableCell>
                              <TableCell>{humanizeAuditAction(row.action)}</TableCell>
                              <TableCell>{summaryLabel(row.businessReference)}</TableCell>
                              <TableCell>{row.result ?? row.reason ?? "-"}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell colSpan={7} sx={{ py: 0, borderBottom: expanded ? undefined : 0 }}>
                                <Collapse in={expanded} timeout="auto" unmountOnExit>
                                  <Box sx={{ p: 2, bgcolor: "action.hover", borderRadius: 2, my: 1 }}>
                                    <Grid container spacing={2}>
                                      <Grid size={{ xs: 12, md: 6 }}>
                                        <Typography variant="body2"><strong>Actor:</strong> {humanizeAuditActor(row.actor)}</Typography>
                                        <Typography variant="body2"><strong>Raw actor:</strong> {row.actor}</Typography>
                                        <Typography variant="body2"><strong>Action:</strong> {humanizeAuditAction(row.action)}</Typography>
                                      </Grid>
                                      <Grid size={{ xs: 12, md: 6 }}>
                                        <Typography variant="body2"><strong>Audit event ID:</strong> {row.auditEventId}</Typography>
                                        <Typography variant="body2"><strong>Technical details:</strong> {row.technicalDetails ?? "None"}</Typography>
                                        <Typography variant="body2"><strong>Metadata:</strong> {JSON.stringify(row.metadata)}</Typography>
                                      </Grid>
                                    </Grid>
                                  </Box>
                                </Collapse>
                              </TableCell>
                            </TableRow>
                          </React.Fragment>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      <Drawer anchor="right" open={Boolean(drawerRow)} onClose={() => setDrawerRow(null)} PaperProps={{ sx: { width: { xs: "100%", md: 640 } } }}>
        <Box sx={{ p: 3, display: "grid", gap: 2 }}>
          <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, alignItems: "flex-start" }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>{drawerRow?.eventLabel ?? "Notification detail"}</Typography>
              <Typography variant="body2" color="text.secondary">{drawerRow?.patientName ?? "-"}</Typography>
            </Box>
            <Button onClick={() => setDrawerRow(null)}>Close</Button>
          </Box>
          {drawerLoading ? <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}><CircularProgress /></Box> : null}
          {drawerRow ? (
            <>
              <Stack spacing={1}>
                <Typography variant="body2"><strong>Business reference:</strong> {summaryLabel(drawerRow.businessReference)}</Typography>
                <Typography variant="body2"><strong>Overall status:</strong> {overallStatusLabel(drawerRow.overallStatus)}</Typography>
                <Typography variant="body2"><strong>Source module:</strong> {drawerRow.sourceModule}</Typography>
                <Typography variant="body2"><strong>Message preview:</strong> {drawerRow.messagePreview}</Typography>
              </Stack>
              <Divider />
              <Stack spacing={1.5}>
                {channelRowOrder(drawerRow.deliveries).map((channelRow) => {
                  const presentation = channelDisplayStatus(channelRow.channel, channelRow);
                  return (
                    <Card key={channelRow.id} variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2 }}>
                            <Box>
                              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{channelLabel(channelRow.channel)}</Typography>
                              <Typography variant="body2" color="text.secondary">{presentation.statusLabel}</Typography>
                            </Box>
                            <Chip size="small" label={presentation.statusLabel} color={presentation.tone === "neutral" ? "default" : presentation.tone} />
                          </Box>
                          <Typography variant="body2"><strong>Provider:</strong> {channelRow.provider}</Typography>
                          <Typography variant="body2"><strong>Recipient:</strong> {channelRow.recipient}</Typography>
                          <Typography variant="body2"><strong>Queued:</strong> {formatTimestamp(channelRow.queuedAt)}</Typography>
                          <Typography variant="body2"><strong>Sent:</strong> {formatTimestamp(channelRow.sentAt)}</Typography>
                          <Typography variant="body2"><strong>Attempts:</strong> {channelRow.persistedAttemptCount}</Typography>
                          <Typography variant="body2"><strong>Failure / skip:</strong> {presentation.reason ?? "None"}</Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  );
                })}
              </Stack>
            </>
          ) : null}
        </Box>
      </Drawer>

      <ConfirmationDialog
        open={Boolean(retryTarget)}
        title="Retry notification delivery"
        description={
          retryTarget
            ? `Retry ${retryTarget.label}? This will requeue ${retryTarget.ids.length} eligible delivery row${retryTarget.ids.length === 1 ? "" : "s"}.`
            : ""
        }
        confirmLabel="Retry"
        cancelLabel="Cancel"
        onConfirm={confirmRetry}
        onCancel={() => setRetryTarget(null)}
        confirmLoading={working}
      >
        <DialogContentText>
          Only eligible transient failures will be retried. Stale, disabled, or non-retryable rows remain unchanged.
        </DialogContentText>
      </ConfirmationDialog>
    </Stack>
  );
}

function CompactMetric({ label, value, helper }: { label: string; value: number | string; helper: string }) {
  return (
    <Stack spacing={0.5}>
      <Typography variant="overline" color="text.secondary">{label}</Typography>
      <Typography variant="h5" sx={{ fontWeight: 900 }}>{value}</Typography>
      <Typography variant="body2" color="text.secondary">{helper}</Typography>
    </Stack>
  );
}

function KpiCard({ label, value, helper }: { label: string; value: string; helper: string }) {
  return (
    <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 2, height: "100%" }}>
      <Stack spacing={0.5}>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
        <Typography variant="h5" sx={{ fontWeight: 900 }}>{value}</Typography>
        <Typography variant="caption" color="text.secondary">{helper}</Typography>
      </Stack>
    </Box>
  );
}

function SmallRow({ label, value }: { label: string; value: string | number }) {
  return (
    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="body2" sx={{ fontWeight: 700 }}>{value}</Typography>
    </Box>
  );
}

function trendIcon(kind: "success" | "warning" | "error" | "neutral") {
  switch (kind) {
    case "success":
      return <TrendingUpRoundedIcon fontSize="inherit" />;
    case "warning":
      return <RemoveRoundedIcon fontSize="inherit" />;
    case "error":
      return <TrendingDownRoundedIcon fontSize="inherit" />;
    default:
      return <InfoRoundedIcon fontSize="inherit" />;
  }
}

function trendTone(kind: "success" | "warning" | "error" | "neutral") {
  switch (kind) {
    case "success":
      return "success";
    case "warning":
      return "warning";
    case "error":
      return "error";
    default:
      return "default";
  }
}

function trendLabel(kind: "success" | "warning" | "error" | "neutral") {
  switch (kind) {
    case "success":
      return "Healthy";
    case "warning":
      return "Watch";
    case "error":
      return "At risk";
    default:
      return "Stable";
  }
}

function formatLatency(value: number | null | undefined) {
  if (value === null || value === undefined) return "-";
  if (value < 1000) return `${Math.round(value)} ms`;
  return `${(value / 1000).toFixed(1)} s`;
}

function OperationalKpiCard({
  label,
  value,
  helper,
  detail,
  tone,
  trend,
  icon,
}: {
  label: string;
  value: string;
  helper: string;
  detail: string;
  tone: "success" | "warning" | "error" | "neutral";
  trend: string;
  icon: React.ReactNode;
}) {
  const chipColor = trendTone(tone);
  return (
    <Card variant="outlined" sx={{ height: "100%", borderRadius: 3 }}>
      <CardContent>
        <Stack spacing={1}>
          <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={1}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Box
                sx={{
                  display: "grid",
                  placeItems: "center",
                  width: 32,
                  height: 32,
                  borderRadius: 2,
                  bgcolor: tone === "neutral" ? "action.hover" : `${chipColor}.main`,
                  color: tone === "neutral" ? "text.secondary" : "common.white",
                }}
              >
                {icon}
              </Box>
              <Box>
                <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 700 }}>{label}</Typography>
                <Typography variant="h5" sx={{ fontWeight: 900, lineHeight: 1.05 }}>{value}</Typography>
              </Box>
            </Stack>
            <Chip size="small" icon={trendIcon(tone)} label={trendLabel(tone)} color={trendTone(tone) as "success" | "warning" | "error" | "default"} variant="outlined" />
          </Stack>
          <Typography variant="caption" color="text.secondary">{helper}</Typography>
          <Typography variant="body2" sx={{ fontWeight: 700 }}>{detail}</Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

function TechnicalKpiCard({
  label,
  value,
  helper,
  tone,
}: {
  label: string;
  value: number | string;
  helper: string;
  tone: "info" | "warning" | "default";
}) {
  return (
    <Card variant="outlined" sx={{ height: "100%", borderRadius: 3 }}>
      <CardContent>
        <Stack spacing={0.75}>
          <Typography variant="overline" color="text.secondary" sx={{ lineHeight: 1 }}>{label}</Typography>
          <Typography variant="h5" sx={{ fontWeight: 900, lineHeight: 1.05 }}>{value}</Typography>
          <Chip size="small" label={tone === "warning" ? "Operational" : tone === "info" ? "Technical" : "Reference"} color={tone === "default" ? "default" : tone} variant="outlined" sx={{ width: "fit-content" }} />
          <Typography variant="body2" color="text.secondary">{helper}</Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

function LoadingSkeletonGrid() {
  return (
    <Grid container spacing={2}>
      {Array.from({ length: 4 }).map((_, index) => (
        <Grid key={index} size={{ xs: 12, sm: 6, lg: 3 }}>
          <Card variant="outlined" sx={{ borderRadius: 3 }}>
            <CardContent>
              <Stack spacing={1}>
                <Skeleton variant="circular" width={32} height={32} />
                <Skeleton variant="text" width="60%" />
                <Skeleton variant="text" width="40%" height={40} />
                <Skeleton variant="text" width="80%" />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}

function NotificationOperationsLoadingState({ isPlatformAdmin }: { isPlatformAdmin: boolean }) {
  return (
    <Stack spacing={2}>
      <LoadingSkeletonGrid />
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card variant="outlined" sx={{ borderRadius: 3 }}>
            <CardContent>
              <Stack spacing={1.25}>
                <Skeleton variant="text" width="35%" height={34} />
                <Skeleton variant="rounded" height={220} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card variant="outlined" sx={{ borderRadius: 3 }}>
            <CardContent>
              <Stack spacing={1.25}>
                <Skeleton variant="text" width="45%" height={34} />
                {Array.from({ length: 5 }).map((_, index) => (
                  <Skeleton key={index} variant="text" height={26} />
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <Card variant="outlined" sx={{ borderRadius: 3 }}>
        <CardContent>
          <Stack spacing={1.25}>
            <Skeleton variant="text" width="30%" height={34} />
            <Skeleton variant="rounded" height={360} />
          </Stack>
        </CardContent>
      </Card>
      {isPlatformAdmin ? <Skeleton variant="text" width="20%" /> : null}
    </Stack>
  );
}

function ProviderHealthCard({ provider }: { provider: NotificationOperationsProviderRow }) {
  const readiness = providerStatusLabel(provider.readinessStatus);
  const configuration = providerConfigurationLabel(provider);
  const statusTone = readiness === "Healthy" ? "success" : readiness === "Degraded" ? "warning" : readiness === "Disabled" ? "default" : readiness === "Not Configured" ? "default" : "warning";
  const configTone = configuration === "Configuration Ready" ? "success" : "default";
  const successRate = provider.successCount + provider.failureCount === 0 ? 0 : Math.round((provider.successCount / (provider.successCount + provider.failureCount)) * 100);

  return (
    <Card variant="outlined" sx={{ height: "100%", borderRadius: 3 }}>
      <CardContent>
        <Stack spacing={1.25}>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>{provider.name}</Typography>
              <Typography variant="body2" color="text.secondary">{provider.providerType}</Typography>
            </Box>
            <Stack spacing={0.5} alignItems="flex-end">
              <Chip size="small" label={readiness} color={statusTone as any} variant="outlined" />
              <Chip size="small" label={configuration} color={configTone as any} variant="outlined" />
            </Stack>
          </Stack>

          <Grid container spacing={1}>
            <Grid size={{ xs: 6, sm: 4 }}>
              <SmallStat label="Pending" value={provider.pendingCount} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4 }}>
              <SmallStat label="Failures" value={provider.failureCount} tone={provider.failureCount > 0 ? "error" : "success"} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4 }}>
              <SmallStat label="Successes" value={provider.successCount} tone="success" />
            </Grid>
            <Grid size={{ xs: 6, sm: 4 }}>
              <SmallStat label="Avg latency" value={provider.averageLatencyMs ? formatLatency(provider.averageLatencyMs) : "-"} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4 }}>
              <SmallStat label="Last success" value={formatTimestamp(provider.lastSuccessfulAt)} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4 }}>
              <SmallStat label="Last failure" value={formatTimestamp(provider.lastFailedAt)} />
            </Grid>
          </Grid>

          <Box sx={{ pt: 0.5 }}>
            <Stack direction="row" justifyContent="space-between" spacing={1} sx={{ mb: 0.5 }}>
              <Typography variant="caption" color="text.secondary">Operational success</Typography>
              <Typography variant="caption" sx={{ fontWeight: 800 }}>{successRate}%</Typography>
            </Stack>
            <LinearProgress variant="determinate" value={successRate} color={provider.failureCount > 0 ? "warning" : "success"} sx={{ height: 8, borderRadius: 999, bgcolor: "action.hover" }} />
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

function SmallStat({ label, value, tone = "default" }: { label: string; value: string | number; tone?: "default" | "success" | "warning" | "error" }) {
  return (
    <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1 }}>
      <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1 }}>{label}</Typography>
      <Typography variant="body2" sx={{ fontWeight: 800, mt: 0.35, color: tone === "error" ? "error.main" : tone === "warning" ? "warning.main" : tone === "success" ? "success.main" : "text.primary" }}>{value}</Typography>
    </Box>
  );
}

function LineSeriesCard({ title, subtitle, series }: { title: string; subtitle: string; series: Array<{ label: string; value: number }> }) {
  const width = 360;
  const height = 150;
  const max = seriesMax(series);
  const step = series.length > 1 ? width / (series.length - 1) : width;
  const points = series.map((point, index) => {
    const x = series.length === 1 ? width / 2 : index * step;
    const y = height - (point.value / max) * (height - 24) - 12;
    return `${x},${y}`;
  }).join(" ");

  return (
    <Card variant="outlined" sx={{ borderRadius: 3, height: "100%" }}>
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>{title}</Typography>
              <Typography variant="body2" color="text.secondary">{subtitle}</Typography>
            </Box>
            <ShowChartRoundedIcon color="primary" />
          </Box>
          {series.length === 0 ? (
            <Alert severity="info">No data available.</Alert>
          ) : (
            <Box component="svg" viewBox={`0 0 ${width} ${height}`} sx={{ width: "100%", height: 180 }}>
              <defs>
                <linearGradient id={`line-${title.replace(/\s+/g, "-").toLowerCase()}`} x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor="currentColor" stopOpacity="0.2" />
                  <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
                </linearGradient>
              </defs>
              <polyline fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" points={points} />
              <polyline fill={`url(#line-${title.replace(/\s+/g, "-").toLowerCase()})`} stroke="none" points={`0,${height} ${points} ${width},${height}`} />
              {series.map((point, index) => {
                const x = series.length === 1 ? width / 2 : index * step;
                const y = height - (point.value / max) * (height - 24) - 12;
                return <circle key={point.label} cx={x} cy={y} r="4.5" fill="currentColor" />;
              })}
            </Box>
          )}
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {series.slice(0, 4).map((point) => (
              <Chip key={point.label} size="small" label={`${point.label}: ${point.value}`} variant="outlined" />
            ))}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

function DonutSeriesCard({ title, series }: { title: string; series: Array<{ label: string; value: number }> }) {
  const total = series.reduce((sum, point) => sum + point.value, 0) || 1;
  let cumulative = 0;
  const colors = ["#1976d2", "#2e7d32", "#ed6c02", "#9c27b0", "#d32f2f", "#0288d1"];
  const stops = series.map((point, index) => {
    const start = (cumulative / total) * 100;
    cumulative += point.value;
    const end = (cumulative / total) * 100;
    return `${colors[index % colors.length]} ${start}% ${end}%`;
  });

  return (
    <Card variant="outlined" sx={{ borderRadius: 3, height: "100%" }}>
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>{title}</Typography>
              <Typography variant="body2" color="text.secondary">Distribution across the selected period</Typography>
            </Box>
            <DonutSmallRoundedIcon color="primary" />
          </Box>
          {series.length === 0 ? (
            <Alert severity="info">No data available.</Alert>
          ) : (
            <Box sx={{ display: "grid", placeItems: "center", py: 1 }}>
              <Box sx={{ width: 160, height: 160, borderRadius: "50%", background: `conic-gradient(${stops.join(",")})`, position: "relative" }}>
                <Box sx={{ position: "absolute", inset: 20, borderRadius: "50%", bgcolor: "background.paper", display: "grid", placeItems: "center", textAlign: "center", p: 1 }}>
                  <Typography variant="caption" color="text.secondary">Total</Typography>
                  <Typography variant="h6" sx={{ fontWeight: 900, lineHeight: 1 }}>{total}</Typography>
                </Box>
              </Box>
            </Box>
          )}
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {series.slice(0, 6).map((point, index) => (
              <Chip key={point.label} size="small" variant="outlined" icon={<Box sx={{ width: 8, height: 8, borderRadius: "50%", bgcolor: colors[index % colors.length] }} />} label={`${point.label}: ${point.value}`} />
            ))}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

function BarSeriesCard({ title, subtitle, series }: { title: string; subtitle: string; series: Array<{ label: string; value: number }> }) {
  const max = seriesMax(series);
  return (
    <Card variant="outlined" sx={{ borderRadius: 3, height: "100%" }}>
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>{title}</Typography>
              <Typography variant="body2" color="text.secondary">{subtitle}</Typography>
            </Box>
            <TableRowsRoundedIcon color="primary" />
          </Box>
          {series.length === 0 ? (
            <Alert severity="info">No data available.</Alert>
          ) : series.map((point) => (
            <Box key={point.label} sx={{ display: "grid", gap: 0.5 }}>
              <Stack direction="row" justifyContent="space-between" spacing={1}>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{point.label}</Typography>
                <Typography variant="body2" sx={{ fontWeight: 800 }}>{point.value}</Typography>
              </Stack>
              <LinearProgress variant="determinate" value={(point.value / max) * 100} sx={{ height: 10, borderRadius: 999, bgcolor: "action.hover" }} />
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}

function ProviderPerformanceChartCard({ title, series }: { title: string; series: Array<{ label: string; value: number }> }) {
  const max = seriesMax(series);
  return (
    <Card variant="outlined" sx={{ borderRadius: 3, height: "100%" }}>
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>{title}</Typography>
              <Typography variant="body2" color="text.secondary">Provider throughput and latency trend</Typography>
            </Box>
            <AnalyticsRoundedIcon color="primary" />
          </Box>
          {series.length === 0 ? (
            <Alert severity="info">No data available.</Alert>
          ) : (
            <Stack spacing={1}>
              {series.map((point) => (
                <Box key={point.label} sx={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) auto", gap: 1, alignItems: "center" }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{point.label}</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 800 }}>{point.value}</Typography>
                  <LinearProgress variant="determinate" value={(point.value / max) * 100} sx={{ gridColumn: "1 / -1", height: 10, borderRadius: 999, bgcolor: "action.hover" }} />
                </Box>
              ))}
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

function StackedRetryOutcomeCard({ title, series }: { title: string; series: Array<{ label: string; value: number }> }) {
  const total = series.reduce((sum, point) => sum + point.value, 0) || 1;
  const colors = ["#2e7d32", "#ed6c02", "#d32f2f", "#0288d1"];
  return (
    <Card variant="outlined" sx={{ borderRadius: 3, height: "100%" }}>
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>{title}</Typography>
              <Typography variant="body2" color="text.secondary">Retry result distribution</Typography>
            </Box>
            <InsightsRoundedIcon color="primary" />
          </Box>
          {series.length === 0 ? (
            <Alert severity="info">No data available.</Alert>
          ) : (
            <Stack spacing={1.25}>
              <Box sx={{ display: "flex", height: 14, borderRadius: 999, overflow: "hidden", bgcolor: "action.hover" }}>
                {series.map((point, index) => (
                  <Box
                    key={point.label}
                    sx={{
                      width: `${(point.value / total) * 100}%`,
                      bgcolor: colors[index % colors.length],
                    }}
                    title={`${point.label}: ${point.value}`}
                  />
                ))}
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {series.map((point, index) => (
                  <Chip key={point.label} size="small" variant="outlined" label={`${point.label}: ${point.value}`} />
                ))}
              </Stack>
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

function FiltersBar({
  query,
  isPlatformAdmin,
  tenantOptions,
  onChange,
}: {
  query: QueryState;
  isPlatformAdmin: boolean;
  tenantOptions: PlatformTenant[];
  onChange: (patch: Partial<QueryState>) => void;
}) {
  return (
    <Grid container spacing={1.5}>
      <Grid size={{ xs: 12, md: 3 }}>
        <FormControl fullWidth size="small">
          <InputLabel id="ops-status-label">Overall status</InputLabel>
          <Select
            labelId="ops-status-label"
            label="Overall status"
            value={query.status || ""}
            onChange={(event) => onChange({ status: String(event.target.value) || null, page: 0 })}
          >
            {["", "DELIVERED", "PARTIAL", "PENDING", "FAILED", "NOT_DELIVERED"].map((value) => (
              <MenuItem key={value || "all"} value={value}>
                {value ? overallStatusLabel(value) : "All"}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <FormControl fullWidth size="small">
          <InputLabel id="ops-event-label">Notification type</InputLabel>
          <Select
            labelId="ops-event-label"
            label="Notification type"
            value={query.eventType || ""}
            onChange={(event) => onChange({ eventType: (String(event.target.value) || null) as NotificationOperationsQuery["eventType"], page: 0 })}
          >
            {["", "APPOINTMENT_BOOKED", "APPOINTMENT_RESCHEDULED", "APPOINTMENT_CANCELLED", "PAYMENT_REMINDER", "PAYMENT_RECEIVED", "PRESCRIPTION_READY", "LAB_REPORT_READY", "BILL_GENERATED", "FOLLOW_UP_DUE", "VACCINATION_DUE"].map((value) => (
              <MenuItem key={value || "all"} value={value}>
                {value ? eventLabel(value) : "All"}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <FormControl fullWidth size="small">
          <InputLabel id="ops-channel-label">Channel</InputLabel>
          <Select
            labelId="ops-channel-label"
            label="Channel"
            value={query.channel || ""}
            onChange={(event) => onChange({ channel: (String(event.target.value) || null) as NotificationOperationsQuery["channel"], page: 0 })}
          >
            {["", ...CHANNEL_ORDER].map((value) => (
              <MenuItem key={value || "all"} value={value}>
                {value ? channelLabel(value) : "All"}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <TextField fullWidth size="small" label="Recipient name" value={query.patientName ?? ""} onChange={(event) => onChange({ patientName: event.target.value || null, page: 0 })} />
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <TextField fullWidth size="small" label="Recipient reference" value={query.patientReference ?? ""} onChange={(event) => onChange({ patientReference: event.target.value || null, page: 0 })} />
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <TextField fullWidth size="small" label="Business reference" value={query.businessReference ?? ""} onChange={(event) => onChange({ businessReference: event.target.value || null, page: 0 })} />
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <TextField fullWidth size="small" label="Provider" value={query.provider ?? ""} onChange={(event) => onChange({ provider: event.target.value || null, page: 0 })} />
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <TextField fullWidth size="small" label="Source module" value={query.sourceModule ?? ""} onChange={(event) => onChange({ sourceModule: event.target.value || null, page: 0 })} />
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <TextField fullWidth size="small" label="Search" value={query.search ?? ""} onChange={(event) => onChange({ search: event.target.value || null, page: 0 })} />
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <FormControl fullWidth size="small">
          <InputLabel id="ops-failure-label">Has failure</InputLabel>
          <Select
            labelId="ops-failure-label"
            label="Has failure"
            value={query.hasFailure === null || query.hasFailure === undefined ? "" : String(query.hasFailure)}
            onChange={(event) => onChange({ hasFailure: event.target.value === "" ? null : event.target.value === "true", page: 0 })}
          >
            <MenuItem value="">All</MenuItem>
            <MenuItem value="true">Yes</MenuItem>
            <MenuItem value="false">No</MenuItem>
          </Select>
        </FormControl>
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <FormControl fullWidth size="small">
          <InputLabel id="ops-retry-label">Has retry</InputLabel>
          <Select
            labelId="ops-retry-label"
            label="Has retry"
            value={query.hasRetry === null || query.hasRetry === undefined ? "" : String(query.hasRetry)}
            onChange={(event) => onChange({ hasRetry: event.target.value === "" ? null : event.target.value === "true", page: 0 })}
          >
            <MenuItem value="">All</MenuItem>
            <MenuItem value="true">Yes</MenuItem>
            <MenuItem value="false">No</MenuItem>
          </Select>
        </FormControl>
      </Grid>
      {isPlatformAdmin ? (
        <Grid size={{ xs: 12, md: 3 }}>
          <FormControl fullWidth size="small">
            <InputLabel id="ops-tenant-filter-small">Tenant</InputLabel>
            <Select
              labelId="ops-tenant-filter-small"
              label="Tenant"
              value={query.tenantId || ""}
              onChange={(event) => onChange({ tenantId: String(event.target.value) || null, page: 0 })}
            >
              {tenantOptions.map((tenant) => (
                <MenuItem key={tenant.id} value={tenant.id}>{tenant.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>
      ) : null}
      <Grid size={{ xs: 12, md: 3 }}>
        <TextField fullWidth size="small" label="Page" type="number" value={query.page ?? 0} onChange={(event) => onChange({ page: Number(event.target.value) || 0 })} />
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <FormControl fullWidth size="small">
          <InputLabel id="ops-size-label">Rows</InputLabel>
          <Select
            labelId="ops-size-label"
            label="Rows"
            value={query.size ?? DEFAULT_PAGE_SIZE}
            onChange={(event) => onChange({ size: Number(event.target.value), page: 0 })}
          >
            {[10, 25, 50].map((size) => (
              <MenuItem key={size} value={size}>{size}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </Grid>
    </Grid>
  );
}

function SeriesCard({ title, series }: { title: string; series: Array<{ label: string; value: number }> }) {
  const max = seriesMax(series);
  return (
    <Card>
      <CardContent>
        <Stack spacing={1.5}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>{title}</Typography>
          {series.length === 0 ? (
            <Alert severity="info">No data available.</Alert>
          ) : series.map((point) => (
            <Box key={point.label} sx={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 1, alignItems: "center" }}>
              <Typography variant="body2">{point.label}</Typography>
              <Typography variant="body2" sx={{ fontWeight: 700 }}>{point.value}</Typography>
              <Box sx={{ gridColumn: "1 / -1", height: 8, borderRadius: 999, bgcolor: "grey.200", overflow: "hidden" }}>
                <Box sx={{ width: `${(point.value / max) * 100}%`, height: "100%", bgcolor: "primary.main" }} />
              </Box>
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}
