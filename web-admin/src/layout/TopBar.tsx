import * as React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { alpha } from "@mui/material/styles";
import {
  AppBar,
  Avatar,
  Box,
  Button,
  Chip,
  IconButton,
  MenuItem,
  Select,
  Toolbar,
  Tooltip,
  Typography,
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import LocalHospitalRoundedIcon from "@mui/icons-material/LocalHospitalRounded";

import { useAuth } from "../auth/useAuth";
import { getPlatformTenants } from "../api/clinicApi";

function formatPathLabel(pathname: string): string {
  if (pathname === "/") return "Dashboard";
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

export default function TopBar({ onToggleSidebar }: { onToggleSidebar: () => void }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const primaryRole = auth.rolesUpper[0] || "USER";
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
      position="static"
      color="inherit"
      elevation={0}
      sx={{
        borderBottom: "1px solid",
        borderColor: "divider",
        bgcolor: alpha("#ffffff", 0.95),
        backdropFilter: "blur(8px)",
      }}
    >
      <Toolbar sx={{ gap: 1.25, minHeight: 72 }}>
        <IconButton color="inherit" onClick={onToggleSidebar}>
          <MenuIcon />
        </IconButton>
        <LocalHospitalRoundedIcon fontSize="small" color="primary" />
        <Box>
          <Typography variant="subtitle1" sx={{ fontWeight: 900, lineHeight: 1.2 }}>
            Clinic Management
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {formatPathLabel(location.pathname)}
          </Typography>
        </Box>

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

        <Box sx={{ display: "flex", alignItems: "center", gap: 1.25, mr: 2 }}>
          <Avatar sx={{ width: 28, height: 28 }}>{(auth.username || "U").slice(0, 1).toUpperCase()}</Avatar>
          <Typography variant="body2">{auth.username}</Typography>
        </Box>

        <Button color="inherit" onClick={() => auth.logout()}>
          Logout
        </Button>
      </Toolbar>
    </AppBar>
  );
}
