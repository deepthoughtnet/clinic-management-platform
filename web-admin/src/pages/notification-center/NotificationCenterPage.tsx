import * as React from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { alpha } from "@mui/material/styles";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Collapse,
  Divider,
  Drawer,
  FormControl,
  InputAdornment,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Snackbar,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography,
  useMediaQuery,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";
import NotificationsActiveRoundedIcon from "@mui/icons-material/NotificationsActiveRounded";
import TaskAltRoundedIcon from "@mui/icons-material/TaskAltRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import TodayRoundedIcon from "@mui/icons-material/TodayRounded";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import MarkUnreadChatAltRoundedIcon from "@mui/icons-material/MarkUnreadChatAltRounded";
import MarkEmailUnreadRoundedIcon from "@mui/icons-material/MarkEmailUnreadRounded";
import DoneAllRoundedIcon from "@mui/icons-material/DoneAllRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import ExpandLessRoundedIcon from "@mui/icons-material/ExpandLessRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import CalendarMonthRoundedIcon from "@mui/icons-material/CalendarMonthRounded";
import LocalHospitalRoundedIcon from "@mui/icons-material/LocalHospitalRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import MedicationRoundedIcon from "@mui/icons-material/MedicationRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";
import CampaignRoundedIcon from "@mui/icons-material/CampaignRounded";
import SmartToyRoundedIcon from "@mui/icons-material/SmartToyRounded";
import PriorityHighRoundedIcon from "@mui/icons-material/PriorityHighRounded";
import LowPriorityRoundedIcon from "@mui/icons-material/LowPriorityRounded";
import CircleRoundedIcon from "@mui/icons-material/CircleRounded";

import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactTableFrame } from "../../components/compact/CompactUi";
import {
  getClinicClock,
  getNotificationCenterInbox,
  getNotificationCenterItem,
  getNotificationCenterSummary,
  markNotificationCenterRead,
  markNotificationCenterReadAll,
  markNotificationCenterUnread,
  type ClinicClock,
  type NotificationCenterCategory,
  type NotificationCenterItem,
  type NotificationCenterPage as NotificationCenterPageResponse,
  type NotificationCenterPriority,
  type NotificationCenterSummary,
} from "../../api/clinicApi";
import {
  NOTIFICATION_CENTER_TAB_OPTIONS,
  NOTIFICATION_CENTER_CATEGORY_OPTIONS,
  NOTIFICATION_CENTER_PRIORITY_OPTIONS,
  NOTIFICATION_CENTER_REFRESH_EVENT,
  buildNotificationCenterInboxQuery,
  buildNotificationCenterSearchParams,
  formatNotificationDateKey,
  formatNotificationExactTimestamp,
  formatNotificationRelativeTime,
  getNotificationActionPresentation,
  getNotificationCategoryPresentation,
  getNotificationPriorityPresentation,
  normalizeNotificationPage,
  normalizeNotificationSummary,
  parseNotificationCenterRouteState,
  type NotificationCenterRouteState,
  type NotificationCenterTab,
} from "./notificationCenterModel.js";

type SnackbarState = { severity: "success" | "error" | "info"; message: string } | null;

type NotificationStatusPresentation = {
  label: string;
  tone: "default" | "primary" | "error" | "warning" | "success";
  icon: React.ReactElement;
  tooltip: string;
};

const EMPTY_NOTIFICATION_ITEMS: NotificationCenterItem[] = [];

function categoryIcon(key: string) {
  switch (key) {
    case "calendar":
      return <CalendarMonthRoundedIcon fontSize="small" />;
    case "clinical":
      return <LocalHospitalRoundedIcon fontSize="small" />;
    case "lab":
      return <ScienceRoundedIcon fontSize="small" />;
    case "pharmacy":
      return <MedicationRoundedIcon fontSize="small" />;
    case "billing":
      return <ReceiptLongRoundedIcon fontSize="small" />;
    case "system":
      return <NotificationsActiveRoundedIcon fontSize="small" />;
    case "engage":
      return <CampaignRoundedIcon fontSize="small" />;
    case "ai":
      return <SmartToyRoundedIcon fontSize="small" />;
    default:
      return <NotificationsActiveRoundedIcon fontSize="small" />;
  }
}

function priorityIcon(key: string) {
  switch (key) {
    case "critical":
      return <PriorityHighRoundedIcon fontSize="small" />;
    case "high":
      return <WarningAmberRoundedIcon fontSize="small" />;
    case "low":
      return <LowPriorityRoundedIcon fontSize="small" />;
    default:
      return <TaskAltRoundedIcon fontSize="small" />;
  }
}

function summaryIcon(key: "unread" | "requiresAction" | "critical" | "today") {
  switch (key) {
    case "unread":
      return <NotificationsActiveRoundedIcon fontSize="small" />;
    case "requiresAction":
      return <TaskAltRoundedIcon fontSize="small" />;
    case "critical":
      return <WarningAmberRoundedIcon fontSize="small" />;
    case "today":
      return <TodayRoundedIcon fontSize="small" />;
    default:
      return <NotificationsActiveRoundedIcon fontSize="small" />;
  }
}

function getNotificationStatusPresentation(read: boolean): NotificationStatusPresentation {
  return read
    ? {
        label: "Read",
        tone: "default",
        icon: <TaskAltRoundedIcon fontSize="small" />,
        tooltip: "Read",
      }
    : {
        label: "Unread",
        tone: "primary",
        icon: <CircleRoundedIcon sx={{ fontSize: 8 }} />,
        tooltip: "Unread",
      };
}

function updateNotificationCenterPageItem(
  page: NotificationCenterPageResponse | null,
  notificationId: string,
  updater: (item: NotificationCenterItem) => NotificationCenterItem,
): NotificationCenterPageResponse | null {
  if (!page || !Array.isArray(page.items)) {
    return page;
  }
  let changed = false;
  const items = page.items.map((item) => {
    if (item.id !== notificationId) {
      return item;
    }
    changed = true;
    return updater(item);
  });
  return changed ? { ...page, items } : page;
}

function updateNotificationCenterPageMarkAll(
  page: NotificationCenterPageResponse | null,
  readAt: string,
  unreadTab: boolean,
): NotificationCenterPageResponse | null {
  if (!page || !Array.isArray(page.items)) {
    return page;
  }
  if (unreadTab) {
    return {
      ...page,
      items: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
    };
  }
  return {
    ...page,
    items: page.items.map((item) => ({ ...item, read: true, readAt: item.readAt || readAt })),
  };
}

function SummaryCard({
  title,
  value,
  helper,
  icon,
  active,
  onClick,
}: {
  title: string;
  value: string | number;
  helper: string;
  icon: React.ReactNode;
  active?: boolean;
  onClick?: () => void;
}) {
  return (
    <Card
      variant="outlined"
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={(event) => {
        if (!onClick) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onClick();
        }
      }}
      sx={{
        cursor: onClick ? "pointer" : "default",
        borderColor: active ? "primary.main" : "divider",
        bgcolor: active ? alpha("#1976d2", 0.05) : "background.paper",
        transition: "border-color 120ms ease, transform 120ms ease",
        "&:hover": onClick ? { borderColor: "primary.main", transform: "translateY(-1px)" } : undefined,
      }}
    >
      <CardContent sx={{ p: 2 }}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Box
            sx={{
              width: 38,
              height: 38,
              borderRadius: "50%",
              display: "grid",
              placeItems: "center",
              bgcolor: alpha("#1976d2", 0.12),
              color: "primary.main",
              flex: "0 0 auto",
            }}
          >
            {icon}
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="h6" sx={{ fontWeight: 900, lineHeight: 1.1 }}>
              {value}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 800 }}>
              {title}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {helper}
            </Typography>
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

export default function NotificationCenterPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const theme = useTheme();
  const isCompactViewport = useMediaQuery(theme.breakpoints.down("md"));

  const tenantId = auth.selectedTenant?.id || auth.tenantId;
  const scopeKey = `${tenantId || ""}:${auth.appUserId || ""}`;
  const canRead = Boolean(auth.accessToken && tenantId && auth.permissions.includes("notification.center.read"));

  const routeState = React.useMemo(() => parseNotificationCenterRouteState(searchParams), [searchParams]);
  const [searchDraft, setSearchDraft] = React.useState(routeState.search);
  const [summary, setSummary] = React.useState<NotificationCenterSummary | null>(null);
  const [pageData, setPageData] = React.useState<NotificationCenterPageResponse | null>(null);
  const [clinicClock, setClinicClock] = React.useState<ClinicClock | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [busyId, setBusyId] = React.useState<string | null>(null);
  const [snackbar, setSnackbar] = React.useState<SnackbarState>(null);
  const [detail, setDetail] = React.useState<NotificationCenterItem | null>(null);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [detailError, setDetailError] = React.useState<string | null>(null);
  const [technicalExpanded, setTechnicalExpanded] = React.useState(false);

  const loadAbortRef = React.useRef<AbortController | null>(null);
  const loadSeqRef = React.useRef(0);
  const detailAbortRef = React.useRef<AbortController | null>(null);
  const detailSeqRef = React.useRef(0);

  const currentTimeZone = clinicClock?.clinicTimeZone || "UTC";
  const currentNow = clinicClock?.clinicNow || null;
  const todayDateKey = React.useMemo(
    () => formatNotificationDateKey(currentNow || new Date().toISOString(), currentTimeZone),
    [currentNow, currentTimeZone],
  );
  const pageItems = React.useMemo(() => pageData?.items ?? EMPTY_NOTIFICATION_ITEMS, [pageData]);
  const snackbarSeverity = snackbar?.severity ?? "info";
  const snackbarMessage = snackbar?.message ?? "";

  const commitRouteState = React.useCallback((patch: Partial<NotificationCenterRouteState>, preserveNotificationId = false) => {
    const next = {
      ...routeState,
      ...patch,
    };
    if (!preserveNotificationId && patch.notificationId === undefined) {
      next.notificationId = "";
    }
    setSearchParams(buildNotificationCenterSearchParams(next), { replace: true });
  }, [routeState, setSearchParams]);

  const loadData = React.useCallback(async () => {
    if (!canRead || !auth.accessToken || !tenantId) {
      setSummary(null);
      setPageData({ items: [], page: 0, size: routeState.size, totalElements: 0, totalPages: 0 });
      setClinicClock(null);
      setError(null);
      setLoading(false);
      return;
    }

    const currentSeq = loadSeqRef.current + 1;
    loadSeqRef.current = currentSeq;
    loadAbortRef.current?.abort();
    const controller = new AbortController();
    loadAbortRef.current = controller;

    setLoading(true);
    try {
      const clock = await getClinicClock(auth.accessToken, tenantId, controller.signal).catch(() => ({
        clinicTimeZone: "UTC",
        clinicNow: new Date().toISOString(),
        serverNowUtc: new Date().toISOString(),
      }));
      if (controller.signal.aborted || loadSeqRef.current !== currentSeq) {
        return;
      }
      setClinicClock(clock);
      const inboxQuery = buildNotificationCenterInboxQuery({
        tab: routeState.tab,
        category: routeState.category as NotificationCenterCategory | "",
        priority: routeState.priority as NotificationCenterPriority | "",
        search: routeState.search,
        from: routeState.from,
        to: routeState.to,
        page: routeState.page,
        size: routeState.size,
      }, clock.clinicTimeZone);
      const [summaryRes, pageRes] = await Promise.all([
        getNotificationCenterSummary(auth.accessToken, tenantId, controller.signal),
        getNotificationCenterInbox(auth.accessToken, tenantId, inboxQuery, controller.signal),
      ]);
      if (controller.signal.aborted || loadSeqRef.current !== currentSeq) {
        return;
      }
      setSummary(normalizeNotificationSummary(summaryRes));
      setPageData(normalizeNotificationPage(pageRes) as NotificationCenterPageResponse);
      setError(null);
    } catch (err) {
      if (controller.signal.aborted || loadSeqRef.current !== currentSeq) {
        return;
      }
      setError(err instanceof Error ? err.message : "Failed to load notifications.");
      setSummary(normalizeNotificationSummary(null));
      setPageData(normalizeNotificationPage({ items: [], page: routeState.page, size: routeState.size, totalElements: 0, totalPages: 0 }) as NotificationCenterPageResponse);
    } finally {
      if (loadSeqRef.current === currentSeq) {
        setLoading(false);
      }
    }
  }, [
    auth.accessToken,
    canRead,
    routeState.category,
    routeState.from,
    routeState.page,
    routeState.priority,
    routeState.search,
    routeState.size,
    routeState.tab,
    routeState.to,
    tenantId,
  ]);

  React.useEffect(() => {
    void loadData();
    return () => {
      loadAbortRef.current?.abort();
    };
  }, [loadData, scopeKey]);

  React.useEffect(() => {
    setSearchDraft(routeState.search);
  }, [routeState.search]);

  React.useEffect(() => {
    const timer = window.setTimeout(() => {
      const nextSearch = searchDraft.trim();
      if (nextSearch !== routeState.search) {
        commitRouteState({ search: nextSearch, page: 0 }, false);
      }
    }, 300);
    return () => window.clearTimeout(timer);
  }, [commitRouteState, routeState.search, searchDraft]);

  React.useEffect(() => {
    const refresh = () => void loadData();
    window.addEventListener("focus", refresh);
    window.addEventListener(NOTIFICATION_CENTER_REFRESH_EVENT, refresh as EventListener);
    return () => {
      window.removeEventListener("focus", refresh);
      window.removeEventListener(NOTIFICATION_CENTER_REFRESH_EVENT, refresh as EventListener);
    };
  }, [loadData]);

  React.useEffect(() => {
    const notificationId = routeState.notificationId.trim();
    if (!notificationId) {
      detailAbortRef.current?.abort();
      setDetail(null);
      setDetailError(null);
      setDetailLoading(false);
      setTechnicalExpanded(false);
      return;
    }

    const listMatch = pageItems.find((item) => item.id === notificationId);
    if (listMatch && detail?.id === notificationId) {
      setDetailError(null);
      setDetailLoading(false);
      return;
    }
    if (listMatch) {
      setDetail(listMatch);
      setDetailError(null);
      setDetailLoading(false);
      return;
    }

    if (!canRead || !auth.accessToken || !tenantId) {
      return;
    }

    setDetail(null);
    setDetailError(null);
    detailAbortRef.current?.abort();
    const controller = new AbortController();
    detailAbortRef.current = controller;
    const currentSeq = detailSeqRef.current + 1;
    detailSeqRef.current = currentSeq;
    setDetailLoading(true);
    setDetailError(null);

    void getNotificationCenterItem(auth.accessToken, tenantId, notificationId, controller.signal)
      .then((item) => {
        if (controller.signal.aborted || detailSeqRef.current !== currentSeq) {
          return;
        }
        setDetail(item);
      })
      .catch((err) => {
        if (controller.signal.aborted || detailSeqRef.current !== currentSeq) {
          return;
        }
        setDetailError(err instanceof Error ? err.message : "Failed to load notification.");
      })
      .finally(() => {
        if (!controller.signal.aborted && detailSeqRef.current === currentSeq) {
          setDetailLoading(false);
        }
      });

    return () => controller.abort();
  }, [auth.accessToken, canRead, pageItems, routeState.notificationId, tenantId, detail?.id]);

  React.useEffect(() => {
    if (!pageData || loading) {
      return;
    }
    const maxPage = Math.max(0, (pageData.totalPages || 1) - 1);
    if (routeState.page > maxPage) {
      commitRouteState({ page: maxPage, notificationId: "" }, false);
    }
  }, [commitRouteState, loading, pageData, routeState.page]);

  const openDetail = React.useCallback((item: NotificationCenterItem) => {
    setDetail(item);
    setDetailError(null);
    setTechnicalExpanded(false);
    commitRouteState({ notificationId: item.id }, true);
  }, [commitRouteState]);

  const closeDetail = React.useCallback(() => {
    detailAbortRef.current?.abort();
    setDetail(null);
    setDetailError(null);
    setTechnicalExpanded(false);
    commitRouteState({ notificationId: "" }, false);
  }, [commitRouteState]);

  const markItem = React.useCallback(async (item: NotificationCenterItem) => {
    if (!auth.accessToken || !tenantId) {
      return;
    }
    setBusyId(item.id);
    const nextRead = !item.read;
    const optimisticReadAt = nextRead ? (item.readAt || new Date().toISOString()) : null;
    const previousSummary = summary;
    const previousPageData = pageData;
    const previousDetail = detail;
    setSummary((current) => (current
      ? normalizeNotificationSummary({
        ...current,
        unreadCount: Math.max(0, current.unreadCount + (nextRead ? -1 : 1)),
      })
      : current));
    setPageData((current) => updateNotificationCenterPageItem(
      current,
      item.id,
      (row) => ({ ...row, read: nextRead, readAt: optimisticReadAt }),
    ));
    setDetail((current) => (current?.id === item.id
      ? { ...current, read: nextRead, readAt: optimisticReadAt }
      : current));
    try {
      const next = item.read
        ? await markNotificationCenterUnread(auth.accessToken, tenantId, item.id)
        : await markNotificationCenterRead(auth.accessToken, tenantId, item.id);
      setDetail((current) => (current?.id === next.id ? next : current));
      setPageData((current) => updateNotificationCenterPageItem(current, next.id, () => next));
      window.dispatchEvent(new Event(NOTIFICATION_CENTER_REFRESH_EVENT));
      setSnackbar({
        severity: "success",
        message: next.read ? "Marked as read." : "Marked as unread.",
      });
    } catch (err) {
      setSummary(previousSummary);
      setPageData(previousPageData);
      setDetail(previousDetail);
      setSnackbar({
        severity: "error",
        message: err instanceof Error ? err.message : "Unable to update notification.",
      });
    } finally {
      setBusyId(null);
    }
  }, [auth.accessToken, loadData, tenantId]);

  const markAllRead = React.useCallback(async () => {
    if (!auth.accessToken || !tenantId || (summary?.unreadCount ?? 0) === 0) {
      return;
    }
    setBusyId("__all__");
    const previousSummary = summary;
    const previousPageData = pageData;
    const previousDetail = detail;
    const optimisticReadAt = new Date().toISOString();
    setSummary((current) => (current ? normalizeNotificationSummary({ ...current, unreadCount: 0 }) : current));
    setPageData((current) => updateNotificationCenterPageMarkAll(current, optimisticReadAt, routeState.tab === "unread"));
    setDetail((current) => (current ? { ...current, read: true, readAt: current.readAt || optimisticReadAt } : current));
    try {
      await markNotificationCenterReadAll(auth.accessToken, tenantId);
      window.dispatchEvent(new Event(NOTIFICATION_CENTER_REFRESH_EVENT));
      setSnackbar({ severity: "success", message: "Marked all notifications as read." });
    } catch (err) {
      setSummary(previousSummary);
      setPageData(previousPageData);
      setDetail(previousDetail);
      setSnackbar({
        severity: "error",
        message: err instanceof Error ? err.message : "Unable to mark all notifications as read.",
      });
    } finally {
      setBusyId(null);
    }
  }, [auth.accessToken, detail, routeState.tab, summary, tenantId]);

  const applyQuickFilter = React.useCallback((patch: Partial<NotificationCenterRouteState>) => {
    commitRouteState({ ...patch, page: 0 }, false);
  }, [commitRouteState]);

  const hasSearchFilters = Boolean(routeState.search || routeState.category || routeState.priority || routeState.from || routeState.to);
  const hasFilters = hasSearchFilters || routeState.tab !== "all";
  const emptyState = React.useMemo(() => {
    if (hasSearchFilters) {
      return {
        title: "No matching notifications",
        subtitle: "Reset filters to return to the full inbox.",
      };
    }
    if (routeState.tab === "unread") {
      return {
        title: "You're all caught up",
        subtitle: "No unread notifications remain for the current user.",
      };
    }
    if (routeState.tab === "requires-action") {
      return {
        title: "No actionable notifications",
        subtitle: "Nothing currently requires attention for the active user.",
      };
    }
    return {
      title: "No notifications yet",
      subtitle: "Notifications will appear here when new events are projected for this user.",
    };
  }, [hasSearchFilters, routeState.tab]);
  const activeDetail = detail || pageItems.find((item) => item.id === routeState.notificationId) || null;
  const drawerOpen = Boolean(routeState.notificationId);
  const safePage = React.useMemo(() => {
    if (!pageData) {
      return 0;
    }
    return Math.max(0, Math.min(routeState.page, Math.max(0, (pageData.totalPages || 1) - 1)));
  }, [pageData, routeState.page]);

  const actionForDetail = activeDetail ? getNotificationActionPresentation(activeDetail.actionRoute, activeDetail.actionLabel, activeDetail.actionTargetId) : null;

  const summaryCards = summary ? [
    {
      key: "unread" as const,
      title: "Unread",
      value: summary.unreadCount,
      helper: "Items waiting in your inbox",
      icon: summaryIcon("unread"),
      active: routeState.tab === "unread",
      onClick: () => applyQuickFilter({ tab: "unread" }),
    },
    {
      key: "requiresAction" as const,
      title: "Requires Action",
      value: summary.requiresActionCount,
      helper: "Notifications with an action",
      icon: summaryIcon("requiresAction"),
      active: routeState.tab === "requires-action",
      onClick: () => applyQuickFilter({ tab: "requires-action" }),
    },
    {
      key: "critical" as const,
      title: "Critical",
      value: summary.criticalCount,
      helper: "High priority notifications",
      icon: summaryIcon("critical"),
      active: routeState.priority === "CRITICAL",
      onClick: () => applyQuickFilter({ tab: "all", priority: "CRITICAL" }),
    },
    {
      key: "today" as const,
      title: "Today",
      value: summary.todayCount,
      helper: "Notifications received today",
      icon: summaryIcon("today"),
      active: Boolean(todayDateKey && routeState.from === todayDateKey && routeState.to === todayDateKey),
      onClick: todayDateKey ? () => applyQuickFilter({ tab: "all", from: todayDateKey, to: todayDateKey }) : undefined,
    },
  ] : [];

  if (!canRead) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="info">Notification Center is not available for the current user.</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={2.5}>
        <Paper elevation={0} sx={{ p: 2.5, borderRadius: 3 }}>
          <Stack spacing={2}>
            <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5} alignItems={{ xs: "flex-start", md: "center" }}>
              <Box>
                <Typography variant="h4" sx={{ fontWeight: 900 }}>
                  My Notifications
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Updates and actions relevant to your work.
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent="flex-end">
                <Chip size="small" color={summary?.unreadCount ? "error" : "default"} label={`${summary?.unreadCount ?? 0} unread`} />
                <Button variant="outlined" startIcon={<RefreshRoundedIcon />} onClick={() => void loadData()} disabled={loading}>
                  Refresh
                </Button>
                <Button variant="outlined" startIcon={<DoneAllRoundedIcon />} onClick={() => void markAllRead()} disabled={(summary?.unreadCount ?? 0) === 0 || loading || busyId === "__all__"}>
                  Mark all as read
                </Button>
              </Stack>
            </Stack>

            <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))", lg: "repeat(4, minmax(0, 1fr))" }, gap: 2 }}>
              {loading && !summary ? Array.from({ length: 4 }).map((_, index) => <Skeleton key={index} variant="rounded" height={118} />) : summaryCards.map((card) => (
                <SummaryCard key={card.key} title={card.title} value={card.value} helper={card.helper} icon={card.icon} active={card.active} onClick={card.onClick} />
              ))}
            </Box>
          </Stack>
        </Paper>

        <Paper elevation={0} sx={{ p: 1, borderRadius: 3 }}>
          <Tabs
            value={routeState.tab}
            onChange={(_, next) => applyQuickFilter({ tab: next as NotificationCenterTab })}
            variant="scrollable"
            allowScrollButtonsMobile
          >
            {NOTIFICATION_CENTER_TAB_OPTIONS.map((option) => (
              <Tab key={option.key} value={option.key} label={option.label} />
            ))}
          </Tabs>
        </Paper>

        <CompactFilterCard
          title="Filters"
          subtitle="Search, narrow, and reset the current inbox slice."
          actions={hasFilters ? (
            <Button
              size="small"
              variant="text"
              onClick={() => {
                setSearchDraft("");
                commitRouteState({ tab: "all", search: "", category: "", priority: "", from: "", to: "", page: 0, notificationId: "" }, false);
              }}
            >
              Reset filters
            </Button>
          ) : null}
        >
          <Stack spacing={1.5}>
            <Stack direction={{ xs: "column", xl: "row" }} spacing={1.25} flexWrap="wrap" alignItems={{ xs: "stretch", xl: "center" }}>
              <TextField
                size="small"
                label="Search"
                value={searchDraft}
                onChange={(event) => setSearchDraft(event.target.value)}
                placeholder="Patient, reference, event, recipient"
                sx={{ minWidth: { xs: "100%", md: 280 }, flex: "1 1 280px" }}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchRoundedIcon fontSize="small" />
                    </InputAdornment>
                  ),
                }}
              />
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <InputLabel>Category</InputLabel>
                <Select
                  value={routeState.category}
                  label="Category"
                  onChange={(event) => applyQuickFilter({ category: String(event.target.value) as NotificationCenterCategory | "" })}
                >
                  {NOTIFICATION_CENTER_CATEGORY_OPTIONS.map((option) => (
                    <MenuItem key={option.value || "ALL"} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <InputLabel>Priority</InputLabel>
                <Select
                  value={routeState.priority}
                  label="Priority"
                  onChange={(event) => applyQuickFilter({ priority: String(event.target.value) as NotificationCenterPriority | "" })}
                >
                  {NOTIFICATION_CENTER_PRIORITY_OPTIONS.map((option) => (
                    <MenuItem key={option.value || "ALL"} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                size="small"
                type="date"
                label="From"
                value={routeState.from}
                onChange={(event) => applyQuickFilter({ from: String(event.target.value) })}
                InputLabelProps={{ shrink: true }}
                sx={{ minWidth: 160 }}
              />
              <TextField
                size="small"
                type="date"
                label="To"
                value={routeState.to}
                onChange={(event) => applyQuickFilter({ to: String(event.target.value) })}
                InputLabelProps={{ shrink: true }}
                sx={{ minWidth: 160 }}
              />
            </Stack>

            {error ? <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => void loadData()}>Retry</Button>}>{error}</Alert> : null}

            {isCompactViewport ? (
              <Stack spacing={1.25}>
                {loading && !pageData ? Array.from({ length: 4 }).map((_, index) => (
                  <Card key={index} variant="outlined" sx={{ borderRadius: 2 }}>
                    <CardContent sx={{ p: 1.5 }}>
                      <Stack spacing={1}>
                        <Skeleton variant="text" width="60%" />
                        <Skeleton variant="rounded" height={48} />
                        <Stack direction="row" spacing={1}>
                          <Skeleton variant="rounded" width={88} height={24} />
                          <Skeleton variant="rounded" width={88} height={24} />
                        </Stack>
                      </Stack>
                    </CardContent>
                  </Card>
                )) : pageItems.length ? pageItems.map((item) => {
                  const category = getNotificationCategoryPresentation(item.category);
                  const priority = getNotificationPriorityPresentation(item.priority);
                  const exactTime = formatNotificationExactTimestamp(item.occurredAt, currentTimeZone);
                  const relativeTime = formatNotificationRelativeTime(item.occurredAt, currentTimeZone, currentNow);
                  const action = getNotificationActionPresentation(item.actionRoute, item.actionLabel, item.actionTargetId);
                  const status = getNotificationStatusPresentation(item.read);
                  return (
                    <Card
                      key={item.id}
                      variant="outlined"
                      role="button"
                      tabIndex={0}
                      onClick={() => openDetail(item)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          openDetail(item);
                        }
                      }}
                      sx={{
                        borderRadius: 2,
                        borderColor: item.read ? "divider" : "primary.light",
                        bgcolor: item.read ? "background.paper" : alpha("#1976d2", 0.03),
                        cursor: "pointer",
                      }}
                    >
                      <CardContent sx={{ p: 1.5 }}>
                        <Stack spacing={1.1}>
                          <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between">
                            <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ minWidth: 0, flex: "1 1 auto" }}>
                              <Box
                                sx={{
                                  width: 34,
                                  height: 34,
                                  borderRadius: "50%",
                                  display: "grid",
                                  placeItems: "center",
                                  bgcolor: alpha("#1976d2", item.read ? 0.08 : 0.14),
                                  color: item.read ? "text.secondary" : "primary.main",
                                  flex: "0 0 auto",
                                }}
                              >
                                {categoryIcon(category.iconKey)}
                              </Box>
                              <Box sx={{ minWidth: 0, flex: "1 1 auto" }}>
                                <Typography variant="body1" sx={{ fontWeight: 900, lineHeight: 1.2 }} noWrap title={item.title}>
                                  {item.title}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                                  {item.sourceEventLabel || item.sourceEventType}
                                </Typography>
                              </Box>
                            </Stack>
                            <Stack direction="row" spacing={0.75} alignItems="center" sx={{ flex: "0 0 auto" }}>
                              <Chip size="small" color={item.read ? "default" : "primary"} icon={status.icon} label={status.label} title={status.tooltip} />
                              <Tooltip title={item.read ? "Mark unread" : "Mark read"}>
                                <span>
                                  <IconButton
                                    size="small"
                                    aria-label={item.read ? "Mark unread" : "Mark read"}
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      void markItem(item);
                                    }}
                                    disabled={busyId === item.id}
                                  >
                                    {item.read ? <MarkUnreadChatAltRoundedIcon fontSize="small" /> : <MarkEmailUnreadRoundedIcon fontSize="small" />}
                                  </IconButton>
                                </span>
                              </Tooltip>
                            </Stack>
                          </Stack>

                          <Typography
                            variant="body2"
                            color="text.secondary"
                            title={item.preview}
                            sx={{
                              display: "-webkit-box",
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: "vertical",
                              overflow: "hidden",
                              lineHeight: 1.35,
                            }}
                          >
                            {item.preview}
                          </Typography>

                          <Stack direction="row" spacing={0.75} flexWrap="wrap" alignItems="center">
                            <Chip size="small" label={category.label} icon={categoryIcon(category.iconKey)} variant="outlined" sx={{ height: 24 }} />
                            <Chip size="small" label={priority.label} icon={priorityIcon(priority.iconKey)} variant="outlined" sx={{ height: 24 }} />
                            {item.businessReference ? <Chip size="small" label={item.businessReference} variant="outlined" sx={{ height: 24 }} /> : null}
                          </Stack>

                          <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="center">
                            <Tooltip title={exactTime || undefined}>
                              <Typography variant="caption" color="text.secondary">
                                {relativeTime || exactTime || "—"}
                              </Typography>
                            </Tooltip>
                            {action ? (
                              <Button
                                size="small"
                                variant="text"
                                endIcon={<OpenInNewRoundedIcon fontSize="small" />}
                                onClick={(event) => {
                                  event.stopPropagation();
                                  navigate(action.route);
                                }}
                              >
                                {action.label}
                              </Button>
                            ) : null}
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  );
                }) : (
                  <CompactEmptyState
                    title={emptyState.title}
                    subtitle={emptyState.subtitle}
                    action={hasFilters ? (
                      <Button variant="outlined" onClick={() => {
                        setSearchDraft("");
                        commitRouteState({ tab: "all", search: "", category: "", priority: "", from: "", to: "", page: 0, notificationId: "" }, false);
                      }}>
                        Reset filters
                      </Button>
                    ) : null}
                  />
                )}
              </Stack>
            ) : (
              <CompactTableFrame maxHeight="72vh">
                <Table stickyHeader size="small" sx={{ minWidth: 1100 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Status</TableCell>
                      <TableCell>Notification</TableCell>
                      <TableCell>Category</TableCell>
                      <TableCell>Priority</TableCell>
                      <TableCell>Preview</TableCell>
                      <TableCell>Business reference</TableCell>
                      <TableCell>Received</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {loading && !pageData ? Array.from({ length: 5 }).map((_, index) => (
                      <TableRow key={index}>
                        {Array.from({ length: 8 }).map((__, cellIndex) => (
                          <TableCell key={cellIndex}><Skeleton variant="text" /></TableCell>
                        ))}
                      </TableRow>
                    )) : pageItems.length ? pageItems.map((item) => {
                      const category = getNotificationCategoryPresentation(item.category);
                      const priority = getNotificationPriorityPresentation(item.priority);
                      const exactTime = formatNotificationExactTimestamp(item.occurredAt, currentTimeZone);
                      const relativeTime = formatNotificationRelativeTime(item.occurredAt, currentTimeZone, currentNow);
                      const action = getNotificationActionPresentation(item.actionRoute, item.actionLabel, item.actionTargetId);
                      const status = getNotificationStatusPresentation(item.read);
                      const rowActive = !item.read || routeState.notificationId === item.id;
                      return (
                        <TableRow
                          key={item.id}
                          hover
                          selected={rowActive}
                          sx={{
                            cursor: "pointer",
                            bgcolor: item.read ? "background.paper" : alpha("#1976d2", 0.03),
                          }}
                          onClick={() => openDetail(item)}
                          tabIndex={0}
                          onKeyDown={(event) => {
                            if (event.key === "Enter" || event.key === " ") {
                              event.preventDefault();
                              openDetail(item);
                            }
                          }}
                          aria-label={`${item.title}. ${item.preview}`}
                        >
                          <TableCell>
                            <Chip size="small" color={item.read ? "default" : "primary"} icon={status.icon} label={status.label} title={status.tooltip} />
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ minWidth: 0 }}>
                              <Box
                                sx={{
                                  width: 32,
                                  height: 32,
                                  borderRadius: "50%",
                                  display: "grid",
                                  placeItems: "center",
                                  bgcolor: alpha("#1976d2", item.read ? 0.08 : 0.14),
                                  color: item.read ? "text.secondary" : "primary.main",
                                  flex: "0 0 auto",
                                }}
                              >
                                {categoryIcon(category.iconKey)}
                              </Box>
                              <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                                <Typography sx={{ fontWeight: 800, lineHeight: 1.2 }}>{item.title}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {item.sourceEventLabel || item.sourceEventType}
                                </Typography>
                              </Stack>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={category.label} icon={categoryIcon(category.iconKey)} variant="outlined" />
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={priority.label} icon={priorityIcon(priority.iconKey)} variant="outlined" />
                          </TableCell>
                          <TableCell>
                            <Typography
                              variant="body2"
                              title={item.preview}
                              sx={{
                                display: "-webkit-box",
                                WebkitLineClamp: 2,
                                WebkitBoxOrient: "vertical",
                                overflow: "hidden",
                                lineHeight: 1.35,
                              }}
                            >
                              {item.preview}
                            </Typography>
                          </TableCell>
                          <TableCell>{item.businessReference || "—"}</TableCell>
                          <TableCell>
                            <Tooltip title={exactTime || undefined}>
                              <Typography variant="body2" color="text.secondary">
                                {relativeTime || exactTime || "—"}
                              </Typography>
                            </Tooltip>
                          </TableCell>
                          <TableCell align="right" onClick={(event) => event.stopPropagation()}>
                            <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                              {action ? (
                                <Tooltip title={action.label}>
                                  <IconButton
                                    size="small"
                                    aria-label={action.label}
                                    onClick={() => {
                                      navigate(action.route);
                                    }}
                                  >
                                    <OpenInNewRoundedIcon fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                              ) : null}
                              <Tooltip title={item.read ? "Mark unread" : "Mark read"}>
                                <span>
                                  <IconButton
                                    size="small"
                                    aria-label={item.read ? "Mark unread" : "Mark read"}
                                    onClick={() => void markItem(item)}
                                    disabled={busyId === item.id}
                                  >
                                    {item.read ? <MarkUnreadChatAltRoundedIcon fontSize="small" /> : <MarkEmailUnreadRoundedIcon fontSize="small" />}
                                  </IconButton>
                                </span>
                              </Tooltip>
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    }) : (
                      <TableRow>
                        <TableCell colSpan={8}>
                          <CompactEmptyState
                            title={emptyState.title}
                            subtitle={emptyState.subtitle}
                            action={hasFilters ? (
                              <Button variant="outlined" onClick={() => {
                                setSearchDraft("");
                                commitRouteState({ tab: "all", search: "", category: "", priority: "", from: "", to: "", page: 0, notificationId: "" }, false);
                              }}>
                                Reset filters
                              </Button>
                            ) : null}
                          />
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </CompactTableFrame>
            )}

            <TablePagination
              component="div"
              count={pageData?.totalElements ?? 0}
              page={safePage}
              rowsPerPage={routeState.size}
              onPageChange={(_, nextPage) => commitRouteState({ page: nextPage, notificationId: "" }, false)}
              onRowsPerPageChange={(event) => commitRouteState({ page: 0, size: Number(event.target.value), notificationId: "" }, false)}
              rowsPerPageOptions={[10, 20, 50]}
            />
          </Stack>
        </CompactFilterCard>
      </Stack>

      <Drawer
        anchor="right"
        open={drawerOpen}
        onClose={closeDetail}
        PaperProps={{ sx: { width: { xs: "100%", md: 560 }, maxWidth: "100vw" } }}
      >
        <Box sx={{ p: 2.5 }}>
          {detailLoading && !activeDetail ? (
            <Stack spacing={1.25}>
              <Skeleton variant="text" height={36} />
              <Skeleton variant="rounded" height={64} />
              <Skeleton variant="rounded" height={64} />
              <Skeleton variant="rounded" height={64} />
            </Stack>
          ) : detailError && !activeDetail ? (
            <Stack spacing={2}>
              <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => void loadData()}>Retry</Button>}>{detailError}</Alert>
              <Button variant="outlined" onClick={closeDetail}>Close</Button>
            </Stack>
          ) : activeDetail ? (
            <Stack spacing={2}>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 900 }}>
                    {activeDetail.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {activeDetail.sourceEventLabel || activeDetail.sourceEventType} · {formatNotificationExactTimestamp(activeDetail.occurredAt, currentTimeZone)}
                  </Typography>
                </Box>
                <Chip size="small" color={activeDetail.read ? "default" : "primary"} label={activeDetail.read ? "Read" : "Unread"} />
              </Stack>

              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={getNotificationCategoryPresentation(activeDetail.category).label} icon={categoryIcon(getNotificationCategoryPresentation(activeDetail.category).iconKey)} variant="outlined" />
                <Chip size="small" label={getNotificationPriorityPresentation(activeDetail.priority).label} icon={priorityIcon(getNotificationPriorityPresentation(activeDetail.priority).iconKey)} variant="outlined" />
              </Stack>

              <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                  {activeDetail.preview}
                </Typography>
              </Paper>

              <Stack spacing={1}>
                <Typography variant="body2"><strong>Recipient:</strong> {activeDetail.recipientDisplayName || "Unknown"}</Typography>
                <Typography variant="body2"><strong>Role:</strong> {activeDetail.recipientRole || "—"}</Typography>
                <Typography variant="body2"><strong>Matched audience:</strong> {activeDetail.matchedAudience || "—"}</Typography>
                <Typography variant="body2"><strong>Business reference:</strong> {activeDetail.businessReference || "—"}</Typography>
                <Typography variant="body2"><strong>Source module:</strong> {activeDetail.sourceModule || "—"}</Typography>
                <Typography variant="body2"><strong>Created:</strong> {formatNotificationExactTimestamp(activeDetail.createdAt, currentTimeZone)}</Typography>
                <Typography variant="body2"><strong>Updated:</strong> {formatNotificationExactTimestamp(activeDetail.updatedAt, currentTimeZone)}</Typography>
                <Typography variant="body2"><strong>Correlation:</strong> {activeDetail.correlationId || "—"}</Typography>
                <Typography variant="body2"><strong>Causation:</strong> {activeDetail.causationId || "—"}</Typography>
              </Stack>

              <Stack direction="row" spacing={1} flexWrap="wrap">
                {actionForDetail ? (
                  <Button
                    variant="contained"
                    startIcon={<OpenInNewRoundedIcon />}
                    onClick={() => {
                      closeDetail();
                      navigate(actionForDetail.route);
                    }}
                  >
                    {actionForDetail.label}
                  </Button>
                ) : null}
                <Button variant="outlined" onClick={() => void markItem(activeDetail)} disabled={busyId === activeDetail.id}>
                  {activeDetail.read ? "Mark unread" : "Mark read"}
                </Button>
                <Button variant="text" onClick={closeDetail}>
                  Close
                </Button>
              </Stack>

              <Divider />
              <Button
                startIcon={technicalExpanded ? <ExpandLessRoundedIcon /> : <ExpandMoreRoundedIcon />}
                variant="text"
                onClick={() => setTechnicalExpanded((value) => !value)}
              >
                Technical details
              </Button>
              <Collapse in={technicalExpanded} timeout="auto">
                <Stack spacing={1}>
                  <Typography variant="body2"><strong>Notification ID:</strong> {activeDetail.notificationId}</Typography>
                  <Typography variant="body2"><strong>Action route:</strong> {activeDetail.actionRoute || "—"}</Typography>
                  <Typography variant="body2"><strong>Action target:</strong> {activeDetail.actionTargetId || "—"}</Typography>
                  <Typography variant="body2"><strong>Version:</strong> {activeDetail.version}</Typography>
                  <Typography variant="body2"><strong>Tenant:</strong> {activeDetail.tenantId}</Typography>
                </Stack>
              </Collapse>
            </Stack>
          ) : null}
        </Box>
      </Drawer>

      <Snackbar open={Boolean(snackbar)} autoHideDuration={3500} onClose={() => setSnackbar(null)} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert
          severity={snackbarSeverity}
          variant="filled"
          sx={{
            width: "100%",
            visibility: snackbar ? "visible" : "hidden",
          }}
        >
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
}
