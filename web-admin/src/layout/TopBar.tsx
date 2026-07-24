import * as React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { alpha } from "@mui/material/styles";
import {
  AppBar,
  Badge,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Popover,
  Select,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
} from "@mui/material";
import NotificationsNoneRoundedIcon from "@mui/icons-material/NotificationsNoneRounded";
import MenuIcon from "@mui/icons-material/Menu";
import HelpCenterRoundedIcon from "@mui/icons-material/HelpCenterRounded";

import { useAuth } from "../auth/useAuth";
import { HelpContext } from "../shared/components/help/HelpProvider";
import { friendlyRoleLabel } from "../auth/moduleEntitlements";
import {
  getNotificationCenterPreview,
  getNotificationCenterUnreadCount,
  getPlatformTenants,
  type NotificationCenterItem,
} from "../api/clinicApi";
import { openGlobalHelp } from "../shared/components/help/helpEvents";
import BrandMark from "../shared/components/branding/BrandMark";

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
  if (pathname === "/notification-center") return "Notification Center";
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

function formatNotificationCenterTime(value: string | null | undefined): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

function NotificationBellMenu() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const [unreadCount, setUnreadCount] = React.useState(0);
  const [items, setItems] = React.useState<NotificationCenterItem[]>([]);
  const [loading, setLoading] = React.useState(false);

  const tenantId = auth.selectedTenant?.id || auth.tenantId;
  const canAccess = Boolean(auth.accessToken && tenantId && auth.permissions.includes("notification.center.read"));

  const refresh = React.useCallback(async () => {
    if (!canAccess || !auth.accessToken || !tenantId) {
      setUnreadCount(0);
      setItems([]);
      return;
    }
    setLoading(true);
    try {
      const [countRes, previewRes] = await Promise.all([
        getNotificationCenterUnreadCount(auth.accessToken, tenantId),
        getNotificationCenterPreview(auth.accessToken, tenantId, 5),
      ]);
      setUnreadCount(countRes.count);
      setItems(previewRes.items);
    } catch {
      setUnreadCount(0);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, canAccess, tenantId]);

  React.useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => {
      void refresh();
    }, 60000);
    return () => window.clearInterval(timer);
  }, [refresh]);

  if (!canAccess) {
    return null;
  }

  const open = Boolean(anchorEl);

  return (
    <>
      <Tooltip title="Notification Center">
        <IconButton
          color="inherit"
          onClick={(event) => {
            setAnchorEl(event.currentTarget);
            void refresh();
          }}
          aria-label="Open notification center"
        >
          <Badge color="error" badgeContent={unreadCount} max={99}>
            <NotificationsNoneRoundedIcon />
          </Badge>
        </IconButton>
      </Tooltip>
      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
        PaperProps={{ sx: { width: 380, maxWidth: "calc(100vw - 24px)", borderRadius: 3 } }}
      >
        <Box sx={{ p: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
            <Box>
              <Typography sx={{ fontWeight: 900 }}>Notification Center</Typography>
              <Typography variant="body2" color="text.secondary">
                Latest unread staff notifications
              </Typography>
            </Box>
            <Chip size="small" color={unreadCount > 0 ? "error" : "default"} label={`${unreadCount} unread`} />
          </Stack>
          <Divider sx={{ my: 1.5 }} />
          {loading ? (
            <Box sx={{ py: 4, display: "grid", placeItems: "center" }}>
              <CircularProgress size={24} />
            </Box>
          ) : items.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
              No unread notifications.
            </Typography>
          ) : (
            <List disablePadding sx={{ display: "grid", gap: 0.5 }}>
              {items.map((item) => (
                <ListItemButton
                  key={item.id}
                  onClick={() => {
                    setAnchorEl(null);
                    navigate("/notification-center");
                  }}
                  sx={{ borderRadius: 2, alignItems: "flex-start" }}
                >
                  <ListItemText
                    primary={item.title}
                    secondary={
                      <Stack spacing={0.25}>
                        <Typography variant="body2" color="text.secondary" sx={{ display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>
                          {item.preview}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatNotificationCenterTime(item.occurredAt)}
                        </Typography>
                      </Stack>
                    }
                  />
                </ListItemButton>
              ))}
            </List>
          )}
          <Divider sx={{ my: 1.5 }} />
          <Button
            fullWidth
            variant="contained"
            onClick={() => {
              setAnchorEl(null);
              navigate("/notification-center");
            }}
          >
            Open inbox
          </Button>
        </Box>
      </Popover>
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
