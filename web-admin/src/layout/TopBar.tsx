import * as React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { alpha } from "@mui/material/styles";
import {
  AppBar,
  Box,
  Button,
  Chip,
  IconButton,
  MenuItem,
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
import { getPlatformTenants } from "../api/clinicApi";
import { openGlobalHelp } from "../shared/components/help/helpEvents";
import BrandMark from "../shared/components/branding/BrandMark";

function formatPathLabel(pathname: string): string {
  if (pathname === "/") return "Dashboard";
  if (pathname === "/dashboard") return "Dashboard";
  if (pathname === "/pharmacy/dashboard") return "Pharmacy Dashboard";
  if (pathname === "/pharmacy/pos") return "Pharmacy POS";
  if (pathname.startsWith("/pharmacy/operations")) return "Pharmacy Operations";
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
          gap: 1.25,
          minHeight: 84,
          px: { xs: 1, md: 2 },
          py: 1,
          flexWrap: "wrap",
          alignItems: "center",
        }}
      >
        <IconButton color="inherit" onClick={onToggleSidebar}>
          <MenuIcon />
        </IconButton>
        <Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0, flex: "1 1 280px" }}>
          <BrandMark compact size={40} showCopy={false} title="Jeevanam Healthcare" />
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

        <Box sx={{ flex: 1 }} />

        <Box sx={{ display: "flex", alignItems: "center", gap: 1, mr: 1.5, flexWrap: "wrap" }}>
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
        </Box>

        <Box sx={{ display: "flex", alignItems: "center", gap: 1, mr: 2, flexWrap: "wrap", justifyContent: "flex-end" }}>
          <Tooltip title="Notifications">
            <IconButton color="inherit" onClick={() => navigate("/notifications")} aria-label="Open notifications">
              <NotificationsNoneRoundedIcon />
            </IconButton>
          </Tooltip>
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
