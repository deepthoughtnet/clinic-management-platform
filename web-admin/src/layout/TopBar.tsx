import * as React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { alpha } from "@mui/material/styles";
import {
  Alert,
  AppBar,
  Badge,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  List,
  ListItemButton,
  MenuItem,
  Popover,
  Select,
  Stack,
  Skeleton,
  Toolbar,
  Tooltip,
  Typography,
  Snackbar,
} from "@mui/material";
import NotificationsRoundedIcon from "@mui/icons-material/NotificationsRounded";
import MenuIcon from "@mui/icons-material/Menu";
import HelpCenterRoundedIcon from "@mui/icons-material/HelpCenterRounded";
import CalendarMonthRoundedIcon from "@mui/icons-material/CalendarMonthRounded";
import LocalHospitalRoundedIcon from "@mui/icons-material/LocalHospitalRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import MedicationRoundedIcon from "@mui/icons-material/MedicationRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";
import CampaignRoundedIcon from "@mui/icons-material/CampaignRounded";
import SmartToyRoundedIcon from "@mui/icons-material/SmartToyRounded";
import NotificationsActiveRoundedIcon from "@mui/icons-material/NotificationsActiveRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import LowPriorityRoundedIcon from "@mui/icons-material/LowPriorityRounded";
import PriorityHighRoundedIcon from "@mui/icons-material/PriorityHighRounded";
import TaskAltRoundedIcon from "@mui/icons-material/TaskAltRounded";
import CircleRoundedIcon from "@mui/icons-material/CircleRounded";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";

import { useAuth } from "../auth/useAuth";
import { HelpContext } from "../shared/components/help/HelpProvider";
import { friendlyRoleLabel } from "../auth/moduleEntitlements";
import {
  getClinicClock,
  getNotificationCenterPreview,
  getNotificationCenterSummary,
  markNotificationCenterRead,
  markNotificationCenterReadAll,
  getPlatformTenants,
  type NotificationCenterItem,
} from "../api/clinicApi";
import { openGlobalHelp } from "../shared/components/help/helpEvents";
import BrandMark from "../shared/components/branding/BrandMark";
import {
  NOTIFICATION_CENTER_REFRESH_EVENT,
  formatNotificationExactTimestamp,
  formatNotificationRelativeTime,
  getNotificationActionPresentation,
  getNotificationCategoryPresentation,
  getNotificationPriorityPresentation,
} from "../pages/notification-center/notificationCenterModel.js";

function formatPathLabel(pathname: string): string {
  if (pathname === "/") return "Dashboard";
  if (pathname === "/dashboard") return "Dashboard";
  if (pathname === "/pharmacy/dashboard") return "Pharmacy Dashboard";
  if (pathname === "/lab") return "Laboratory Dashboard";
  if (pathname === "/patients/new") return "Patients";
  if (/^\/patients\/[^/]+\/edit$/.test(pathname) || /^\/patients\/[^/]+$/.test(pathname)) return "Patient Details";
  if (pathname === "/pharmacy/pos") return "POS Sale";
  if (pathname === "/prescriptions") return "Prescription Register";
  if (pathname === "/inventory") return "Inventory";
  if (pathname === "/pharmacy/medicines" || pathname === "/pharmacy/medicine-master") return "Medicine Master";
  if (pathname === "/pharmacy/procure") return "Procure";
  if (pathname === "/pharmacy/reconcile") return "Reconcile";
  if (pathname === "/pharmacy/procure-test") return "Procure Test";
  if (pathname === "/pharmacy/reconcile-test") return "Reconcile Test";
  if (pathname === "/pharmacy/procurement") return "Procurement";
  if (pathname === "/pharmacy/reconciliation") return "Reconciliation";
  if (pathname === "/pharmacy/operations") return "Procurement";
  if (pathname === "/pharmacy/stock-movements") return "Reports & Audit";
  if (pathname === "/notification-center") return "My Notifications";
  if (pathname === "/carepilot/ai-operations") return "AI Operations";
  if (pathname.startsWith("/platform/tenants")) return "Platform Tenants";
  if (pathname.startsWith("/platform/plans")) return "Plans / Modules";
  if (pathname.startsWith("/platform/users")) return "Users / Admins";
  const leaf = pathname.split("/").filter(Boolean).at(-1) || pathname;
  return leaf.replace(/-/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
}

function isSystemTenantOption(tenant: { tenantId: string; tenantCode?: string | null; tenantName?: string | null }): boolean {
  const values = [tenant.tenantId, tenant.tenantCode, tenant.tenantName].map((value) => (value || "").toUpperCase());
  return values.some((value) => value.startsWith("DEFAULT-ROLES") || value.includes("DEFAULT-ROLES-"));
}

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
      return <NotificationsRoundedIcon fontSize="small" />;
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

function NotificationBellMenu() {
  const auth = useAuth();
  const navigate = useNavigate();
  const buttonRef = React.useRef<HTMLButtonElement | null>(null);
  const requestSeqRef = React.useRef(0);
  const abortRef = React.useRef<AbortController | null>(null);
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const [summary, setSummary] = React.useState<{ unreadCount: number; requiresActionCount: number; criticalCount: number; todayCount: number } | null>(null);
  const [items, setItems] = React.useState<NotificationCenterItem[]>([]);
  const [clinicNow, setClinicNow] = React.useState<string | null>(null);
  const [clinicTimeZone, setClinicTimeZone] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [toast, setToast] = React.useState<{ severity: "success" | "error"; message: string } | null>(null);
  const [isVisible, setIsVisible] = React.useState(() => typeof document === "undefined" ? true : document.visibilityState !== "hidden");

  const tenantId = auth.selectedTenant?.id || auth.tenantId;
  const canAccess = Boolean(auth.accessToken && tenantId && auth.permissions.includes("notification.center.read"));
  const unreadCount = summary?.unreadCount ?? 0;

  const closePopover = React.useCallback(() => {
    setAnchorEl(null);
    window.setTimeout(() => buttonRef.current?.focus(), 0);
  }, []);

  const refresh = React.useCallback(async () => {
    if (!canAccess || !auth.accessToken || !tenantId) {
      setSummary(null);
      setItems([]);
      setClinicNow(null);
      setClinicTimeZone(null);
      return;
    }

    const currentSeq = requestSeqRef.current + 1;
    requestSeqRef.current = currentSeq;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setLoading(true);
    try {
      const [summaryRes, previewRes, clockRes] = await Promise.all([
        getNotificationCenterSummary(auth.accessToken, tenantId, controller.signal),
        getNotificationCenterPreview(auth.accessToken, tenantId, 8, controller.signal),
        getClinicClock(auth.accessToken, tenantId, controller.signal),
      ]);
      if (controller.signal.aborted || requestSeqRef.current !== currentSeq) {
        return;
      }
      setSummary(summaryRes);
      setItems(previewRes.items);
      setClinicNow(clockRes.clinicNow);
      setClinicTimeZone(clockRes.clinicTimeZone);
    } catch (error) {
      if (controller.signal.aborted || requestSeqRef.current !== currentSeq) {
        return;
      }
      setSummary(null);
      setItems([]);
      setClinicNow(null);
      setClinicTimeZone(null);
      setToast({
        severity: "error",
        message: error instanceof Error ? error.message : "Unable to load notifications.",
      });
    } finally {
      if (requestSeqRef.current === currentSeq) {
        setLoading(false);
      }
    }
  }, [auth.accessToken, canAccess, tenantId]);

  React.useEffect(() => {
    if (!canAccess) {
      abortRef.current?.abort();
      setSummary(null);
      setItems([]);
      setClinicNow(null);
      setClinicTimeZone(null);
      return;
    }
    void refresh();
  }, [canAccess, refresh]);

  React.useEffect(() => {
    const onFocus = () => {
      if (document.visibilityState !== "hidden") {
        void refresh();
      }
    };
    const onVisibilityChange = () => {
      setIsVisible(document.visibilityState !== "hidden");
      if (document.visibilityState !== "hidden") {
        void refresh();
      }
    };
    const onManualRefresh = () => {
      void refresh();
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onVisibilityChange);
    window.addEventListener(NOTIFICATION_CENTER_REFRESH_EVENT, onManualRefresh as EventListener);
    return () => {
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onVisibilityChange);
      window.removeEventListener(NOTIFICATION_CENTER_REFRESH_EVENT, onManualRefresh as EventListener);
    };
  }, [refresh]);

  React.useEffect(() => {
    if (!canAccess || !isVisible) {
      return;
    }
    const timer = window.setInterval(() => {
      void refresh();
    }, 30_000);
    return () => window.clearInterval(timer);
  }, [canAccess, isVisible, refresh]);

  React.useEffect(() => {
    if (!canAccess) {
      return;
    }
    const nextTenantId = auth.selectedTenant?.id || auth.tenantId || null;
    return () => {
      if (nextTenantId) {
        abortRef.current?.abort();
      }
    };
  }, [auth.selectedTenant?.id, auth.tenantId, canAccess]);

  if (!canAccess) {
    return null;
  }

  const open = Boolean(anchorEl);
  const unreadLabel = unreadCount > 0 ? `${unreadCount} unread` : "no unread items";
  const buttonLabel = `Notifications, ${unreadLabel}`;

  const handleOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
    void refresh();
  };

  const handleItemClick = (item: NotificationCenterItem) => {
    const action = getNotificationActionPresentation(item.actionRoute, item.actionLabel, item.actionTargetId);
    const previousItems = items;
    const previousSummary = summary;
    closePopover();
    if (!item.read) {
      setItems((current) => current.filter((row) => row.id !== item.id));
      setSummary((current) => (current ? { ...current, unreadCount: Math.max(0, current.unreadCount - 1) } : current));
      void markNotificationCenterRead(auth.accessToken || "", tenantId || "", item.id)
        .then((next) => {
          setItems((current) => current.filter((row) => row.id !== next.id));
          window.dispatchEvent(new Event(NOTIFICATION_CENTER_REFRESH_EVENT));
        })
        .catch(() => {
          setItems(previousItems);
          setSummary(previousSummary);
          setToast({ severity: "error", message: "Unable to update read state." });
          void refresh();
        });
    }
    if (action) {
      navigate(action.route);
      return;
    }
    navigate(`/notification-center?notificationId=${encodeURIComponent(item.id)}`);
  };

  const handleMarkAllRead = async () => {
    if (!auth.accessToken || !tenantId || unreadCount === 0) {
      return;
    }
    const previousItems = items;
    const previousSummary = summary;
    try {
      setLoading(true);
      setItems([]);
      setSummary((current) => (current ? { ...current, unreadCount: 0 } : current));
      await markNotificationCenterReadAll(auth.accessToken, tenantId);
      window.dispatchEvent(new Event(NOTIFICATION_CENTER_REFRESH_EVENT));
      setToast({ severity: "success", message: "Marked all notifications as read." });
    } catch (error) {
      setItems(previousItems);
      setSummary(previousSummary);
      setToast({ severity: "error", message: error instanceof Error ? error.message : "Unable to mark all as read." });
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Tooltip title={buttonLabel}>
        <IconButton
          ref={buttonRef}
          color="inherit"
          onClick={handleOpen}
          aria-label={buttonLabel}
          aria-haspopup="menu"
          aria-expanded={open ? "true" : undefined}
          aria-controls={open ? "notification-center-preview" : undefined}
        >
          <Badge color="error" badgeContent={unreadCount} max={99} invisible={unreadCount === 0}>
            <NotificationsRoundedIcon />
          </Badge>
        </IconButton>
      </Tooltip>
      <Popover
        id="notification-center-preview"
        open={open}
        anchorEl={anchorEl}
        onClose={closePopover}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
        PaperProps={{ sx: { width: 420, maxWidth: "calc(100vw - 24px)", borderRadius: 3, maxHeight: "calc(100vh - 24px)" } }}
      >
        <Box sx={{ p: 2, display: "flex", flexDirection: "column", maxHeight: "calc(100vh - 24px)" }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
            <Box>
              <Typography sx={{ fontWeight: 900 }}>Notifications</Typography>
              <Typography variant="body2" color="text.secondary">
                Updates relevant to your work
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} alignItems="center">
              <Chip size="small" color={unreadCount > 0 ? "error" : "default"} label={unreadLabel} />
              <Button size="small" variant="text" onClick={() => void handleMarkAllRead()} disabled={unreadCount === 0 || loading} startIcon={<TaskAltRoundedIcon fontSize="small" />}>
                Mark all as read
              </Button>
            </Stack>
          </Stack>
          <Divider sx={{ my: 1.5 }} />
          <Box sx={{ flex: "1 1 auto", minHeight: 0, overflow: "auto" }}>
            {loading && items.length === 0 ? (
              <Stack spacing={1.25} sx={{ py: 1 }}>
                <Skeleton variant="rounded" height={64} />
                <Skeleton variant="rounded" height={64} />
                <Skeleton variant="rounded" height={64} />
              </Stack>
            ) : items.length === 0 ? (
              <Box sx={{ py: 2 }}>
                <Typography variant="body2" sx={{ fontWeight: 800 }}>
                  No unread notifications.
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Open My Notifications for your full history and read state.
                </Typography>
              </Box>
            ) : (
              <List disablePadding sx={{ display: "grid", gap: 0.75 }}>
                {items.map((item) => {
                  const categoryPresentation = getNotificationCategoryPresentation(item.category);
                  const priorityPresentation = getNotificationPriorityPresentation(item.priority);
                  const relativeTime = formatNotificationRelativeTime(item.occurredAt, clinicTimeZone, clinicNow);
                  const exactTime = formatNotificationExactTimestamp(item.occurredAt, clinicTimeZone);
                  const action = getNotificationActionPresentation(item.actionRoute, item.actionLabel, item.actionTargetId);
                  return (
                    <ListItemButton
                      key={item.id}
                      onClick={() => handleItemClick(item)}
                      sx={{
                        borderRadius: 2,
                        alignItems: "flex-start",
                        py: 1.25,
                        px: 1.25,
                        border: "1px solid",
                        borderColor: item.read ? "divider" : "primary.light",
                        bgcolor: item.read ? "background.paper" : "action.hover",
                      }}
                      aria-label={`${item.title}. ${item.preview}. ${relativeTime || exactTime}`}
                    >
                      <Stack direction="row" spacing={1.25} sx={{ width: "100%" }} alignItems="flex-start">
                        <Box
                          sx={{
                            width: 34,
                            height: 34,
                            borderRadius: "50%",
                            display: "grid",
                            placeItems: "center",
                            bgcolor: item.read ? alpha("#64748b", 0.1) : alpha("#1976d2", 0.14),
                            color: item.read ? "text.secondary" : "primary.main",
                            flex: "0 0 auto",
                          }}
                        >
                          {categoryIcon(categoryPresentation.iconKey)}
                        </Box>
                        <Stack spacing={0.4} sx={{ minWidth: 0, flex: "1 1 auto" }}>
                          <Stack direction="row" spacing={0.75} justifyContent="space-between" alignItems="flex-start">
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap title={item.title}>
                                {item.title}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                                {categoryPresentation.label}
                              </Typography>
                            </Box>
                            <Stack direction="row" spacing={0.5} alignItems="center" sx={{ flex: "0 0 auto", color: item.read ? "text.secondary" : "primary.main" }}>
                              {!item.read ? <CircleRoundedIcon sx={{ fontSize: 8 }} /> : null}
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
                            <Chip size="small" icon={priorityIcon(priorityPresentation.iconKey)} label={priorityPresentation.label} variant="outlined" sx={{ height: 24 }} />
                            <Chip size="small" label={relativeTime || exactTime || "Just now"} variant="outlined" sx={{ height: 24 }} title={exactTime || undefined} />
                            {action ? <Chip size="small" label={action.label} color="primary" variant="outlined" sx={{ height: 24 }} icon={<OpenInNewRoundedIcon fontSize="small" />} /> : null}
                          </Stack>
                        </Stack>
                      </Stack>
                    </ListItemButton>
                  );
                })}
              </List>
            )}
          </Box>
          <Divider sx={{ my: 1.5 }} />
          <Button
            fullWidth
            variant="contained"
            onClick={() => {
              closePopover();
              navigate("/notification-center");
            }}
          >
            View all notifications
          </Button>
        </Box>
      </Popover>
      <Snackbar open={Boolean(toast)} autoHideDuration={3500} onClose={() => setToast(null)} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        {toast ? <Alert severity={toast.severity} variant="filled" sx={{ width: "100%" }}>{toast.message}</Alert> : <></>}
      </Snackbar>
    </>
  );
}

export default function TopBar({ onToggleSidebar, drawerWidth, isMobile }: { onToggleSidebar: () => void; drawerWidth: number; isMobile: boolean }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const helpContext = React.useContext(HelpContext);
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const primaryRole = friendlyRoleLabel(auth);
  const [platformTenantOptions, setPlatformTenantOptions] = React.useState<Array<{ tenantId: string; tenantCode?: string | null; tenantName?: string | null }>>([]);
  const tenantOptions = (isPlatformAdmin && platformTenantOptions.length > 0 ? platformTenantOptions : auth.activeTenantMemberships)
    .filter((tenant) => !isSystemTenantOption(tenant));
  const showTenantDropdown = isPlatformAdmin || tenantOptions.length > 1;

  React.useEffect(() => {
    let cancelled = false;
    async function loadPlatformTenants() {
      if (!isPlatformAdmin || !auth.accessToken) return;
      try {
        const tenants = await getPlatformTenants(auth.accessToken);
        if (cancelled) return;
        setPlatformTenantOptions(
          tenants
            .map((tenant) => ({
              tenantId: tenant.id,
              tenantCode: tenant.code,
              tenantName: tenant.name,
            }))
            .filter((tenant) => !isSystemTenantOption(tenant)),
        );
      } catch {
        if (!cancelled) setPlatformTenantOptions([]);
      }
    }
    void loadPlatformTenants();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, isPlatformAdmin]);

  return (
    <AppBar
      position="fixed"
      color="inherit"
      elevation={0}
      sx={{
        top: 0,
        left: isMobile ? 0 : `${drawerWidth}px`,
        width: isMobile ? "100%" : `calc(100% - ${drawerWidth}px)`,
        zIndex: (theme) => theme.zIndex.drawer + 2,
        backgroundColor: "#ffffff",
        backgroundImage: "none",
        borderBottom: "1px solid",
        borderColor: "divider",
        backdropFilter: "blur(8px)",
        boxShadow: "0 1px 0 rgba(15, 23, 42, 0.04)",
        "&::before": {
          content: '""',
          position: "absolute",
          inset: 0,
          pointerEvents: "none",
          background: "linear-gradient(180deg, rgba(255,255,255,0.96), rgba(255,255,255,0.9))",
        },
      }}
    >
      <Toolbar
        sx={{
          position: "relative",
          zIndex: 1,
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "minmax(0, 1fr) auto minmax(0, 1fr)" },
          gridTemplateAreas: {
            xs: `"left" "center" "right"`,
            md: `"left center right"`,
          },
          rowGap: { xs: 1, md: 0 },
          columnGap: 1.25,
          minHeight: 84,
          px: { xs: 1, md: 2 },
          py: 1,
          alignItems: "center",
        }}
      >
        <Box sx={{ gridArea: "left", display: "flex", alignItems: "center", gap: 1.25, minWidth: 0 }}>
          <IconButton color="inherit" onClick={onToggleSidebar}>
            <MenuIcon />
          </IconButton>
          <Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0, flex: "1 1 280px" }}>
            <BrandMark size={42} showCopy title="Jeevanam Healthcare" subtitle="Intelligent Healthcare Platform" />
            <Chip
              size="small"
              variant="outlined"
              label={formatPathLabel(location.pathname)}
              sx={{
                maxWidth: "100%",
                "& .MuiChip-label": {
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                },
              }}
            />
          </Stack>
        </Box>

        <Box
          sx={{
            gridArea: "center",
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            minWidth: 0,
          }}
        >
          <Chip
            label="DEMO / UAT"
            color="warning"
            size="small"
            sx={{
              fontWeight: 800,
              letterSpacing: 0.4,
              pointerEvents: "none",
              whiteSpace: "nowrap",
            }}
          />
        </Box>

        <Box
          sx={{
            gridArea: "right",
            display: "flex",
            alignItems: "center",
            gap: 1,
            flexWrap: "wrap",
            justifyContent: { xs: "flex-start", md: "flex-end" },
            minWidth: 0,
          }}
        >
          {isPlatformAdmin ? <Chip size="small" color="primary" label="Platform" /> : null}
          <Chip size="small" variant="outlined" label={primaryRole} />
          {showTenantDropdown ? (
            <>
              <Select
                size="small"
                value={auth.selectedTenant?.id || ""}
                displayEmpty
                sx={{ minWidth: 250 }}
                onChange={(event) => {
                  const selected = tenantOptions.find((item) => item.tenantId === event.target.value);
                  const nextTenant = selected
                    ? {
                        id: selected.tenantId,
                        code: selected.tenantCode || selected.tenantId,
                        name: selected.tenantName || selected.tenantCode || selected.tenantId,
                      }
                    : null;
                  auth.selectTenant(nextTenant);
                  if (nextTenant && location.pathname.startsWith("/platform")) {
                    navigate("/");
                  }
                }}
              >
                <MenuItem value="">{isPlatformAdmin ? "Platform mode (no clinic selected)" : "Select clinic tenant"}</MenuItem>
                {tenantOptions.map((tenant) => (
                  <MenuItem key={tenant.tenantId} value={tenant.tenantId}>
                    {(tenant.tenantName || tenant.tenantCode || tenant.tenantId) +
                      (tenant.tenantCode ? ` (${tenant.tenantCode})` : "")}
                  </MenuItem>
                ))}
              </Select>
              {isPlatformAdmin ? (
                <Button variant="outlined" onClick={() => navigate("/platform/tenants")}>
                  Open Tenants
                </Button>
              ) : null}
            </>
          ) : null}

          <Tooltip title={auth.selectedTenant?.id || "No tenant selected"}>
            <Chip
              label={auth.selectedTenant?.name || "No tenant selected"}
              color={auth.selectedTenant ? "secondary" : "default"}
              variant="outlined"
            />
          </Tooltip>
          <NotificationBellMenu />
          <Button
            type="button"
            size="small"
            variant="outlined"
            startIcon={<HelpCenterRoundedIcon fontSize="small" />}
            onClick={() => {
              if (import.meta.env?.DEV) {
                console.log("Help clicked");
              }
              helpContext?.openHelp();
              openGlobalHelp({ source: "topbar" });
            }}
            aria-label="Open help"
          >
            Help
          </Button>
          <Box
            sx={{
              width: 30,
              height: 30,
              borderRadius: "50%",
              display: "grid",
              placeItems: "center",
              bgcolor: alpha("#0f766e", 0.1),
              color: "primary.main",
              fontWeight: 900,
              fontSize: 13,
            }}
          >
            {(auth.username || "U").slice(0, 1).toUpperCase()}
          </Box>
          <Typography variant="body2">{auth.username}</Typography>
          <Button color="inherit" onClick={() => auth.logout()}>
            Logout
          </Button>
        </Box>
      </Toolbar>
    </AppBar>
  );
}
