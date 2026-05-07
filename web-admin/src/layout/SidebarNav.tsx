import { NavLink, useLocation } from "react-router-dom";
import { Box, Divider, Drawer, List, ListItemButton, ListItemText, Tooltip, Typography } from "@mui/material";
import DashboardRoundedIcon from "@mui/icons-material/DashboardRounded";
import ApartmentRoundedIcon from "@mui/icons-material/ApartmentRounded";
import AutoAwesomeMotionRoundedIcon from "@mui/icons-material/AutoAwesomeMotionRounded";
import GroupRoundedIcon from "@mui/icons-material/GroupRounded";
import PeopleAltRoundedIcon from "@mui/icons-material/PeopleAltRounded";
import EventRoundedIcon from "@mui/icons-material/EventRounded";
import QueueRoundedIcon from "@mui/icons-material/QueueRounded";
import MedicalServicesRoundedIcon from "@mui/icons-material/MedicalServicesRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";
import PaymentsRoundedIcon from "@mui/icons-material/PaymentsRounded";
import NotificationsRoundedIcon from "@mui/icons-material/NotificationsRounded";
import VaccinesRoundedIcon from "@mui/icons-material/VaccinesRounded";
import Inventory2RoundedIcon from "@mui/icons-material/Inventory2Rounded";
import BarChartRoundedIcon from "@mui/icons-material/BarChartRounded";
import SettingsRoundedIcon from "@mui/icons-material/SettingsRounded";
import BadgeRoundedIcon from "@mui/icons-material/BadgeRounded";

import { NAV, type NavItem } from "./nav";
import { useAuth } from "../auth/useAuth";

export type SidebarNavProps = {
  open: boolean;
  variant: "permanent" | "temporary";
  width: number;
  onClose?: () => void;
};

function SectionHeader({ label, compact }: { label: string; compact: boolean }) {
  if (compact) {
    return <Box sx={{ py: 0.75 }} />;
  }
  return (
    <Box sx={{ px: 2, pt: 2, pb: 1 }}>
      <Typography variant="overline" sx={{ opacity: 0.7 }}>
        {label}
      </Typography>
    </Box>
  );
}

function isActivePath(current: string, path: string): boolean {
  if (path === "/") return current === "/";
  return current === path || current.startsWith(`${path}/`);
}

export default function SidebarNav({ open, variant, width, onClose }: SidebarNavProps) {
  const location = useLocation();
  const auth = useAuth();
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const compact = variant === "permanent" && !open;
  const activeRoles = new Set(auth.rolesUpper);
  const tenantRole = (auth.tenantRole || auth.activeTenantMemberships
    .find((membership) => membership.tenantId === auth.tenantId)
    ?.role
    || "")
    .toUpperCase();
  if (tenantRole) {
    activeRoles.add(tenantRole);
  }

  const iconMap: Record<string, React.ReactNode> = {
    "platform-dashboard": <DashboardRoundedIcon fontSize="small" />,
    "platform-tenants": <ApartmentRoundedIcon fontSize="small" />,
    "platform-plans": <AutoAwesomeMotionRoundedIcon fontSize="small" />,
    "platform-users": <GroupRoundedIcon fontSize="small" />,
    dashboard: <DashboardRoundedIcon fontSize="small" />,
    patients: <PeopleAltRoundedIcon fontSize="small" />,
    appointments: <EventRoundedIcon fontSize="small" />,
    queue: <QueueRoundedIcon fontSize="small" />,
    "queue-ops": <QueueRoundedIcon fontSize="small" />,
    consultations: <MedicalServicesRoundedIcon fontSize="small" />,
    prescriptions: <ReceiptLongRoundedIcon fontSize="small" />,
    billing: <PaymentsRoundedIcon fontSize="small" />,
    notifications: <NotificationsRoundedIcon fontSize="small" />,
    vaccinations: <VaccinesRoundedIcon fontSize="small" />,
    inventory: <Inventory2RoundedIcon fontSize="small" />,
    reports: <BarChartRoundedIcon fontSize="small" />,
    "settings-profile": <SettingsRoundedIcon fontSize="small" />,
    "settings-users-roles": <BadgeRoundedIcon fontSize="small" />,
  };

  const items = NAV.filter((item) => {
    if (item.platformOnly && !isPlatformAdmin) return false;
    if (item.requiresTenant && !auth.tenantId) return false;
    if (item.rolesAny && item.rolesAny.length > 0 && !item.rolesAny.some((role) => activeRoles.has(role))) return false;
    if (item.section === "Clinic" && isPlatformAdmin && !auth.tenantId) return false;
    if (item.section === "Settings" && isPlatformAdmin && !auth.tenantId) return false;
    return true;
  }).filter((item, index, list) => {
    if (!item.section) return true;
    const nextItem = list[index + 1];
    return Boolean(nextItem && !nextItem.section);
  });

  return (
    <Drawer
      open={open}
      onClose={onClose}
      variant={variant}
      sx={{
        width,
        flexShrink: 0,
        "& .MuiDrawer-paper": {
          width,
          boxSizing: "border-box",
        },
      }}
    >
      <Box sx={{ px: compact ? 1 : 2, py: 1.75 }}>
        {compact ? (
          <Typography variant="subtitle2" sx={{ fontWeight: 900, textAlign: "center" }}>
            CM
          </Typography>
        ) : (
          <>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              Clinic Management
            </Typography>
            <Typography variant="caption" sx={{ opacity: 0.7 }}>
              Care operations console
            </Typography>
          </>
        )}
      </Box>

      <Divider />

      <List sx={{ py: 0 }}>
        {items.map((item: NavItem) => {
          if (item.section) return <SectionHeader key={item.key} label={item.section} compact={compact} />;
          if (!item.path) return null;

          const active = isActivePath(location.pathname, item.path);

          const content = (
            <ListItemButton
              key={item.key}
              component={NavLink}
              to={item.path}
              onClick={variant === "temporary" ? onClose : undefined}
              sx={{
                mx: 1,
                my: 0.25,
                borderRadius: 1.75,
                bgcolor: active ? "action.selected" : undefined,
                px: compact ? 1 : 1.25,
                justifyContent: compact ? "center" : "flex-start",
              }}
            >
              <Box
                sx={{
                  width: 28,
                  height: 28,
                  borderRadius: 1,
                  bgcolor: active ? "primary.main" : "action.hover",
                  color: active ? "primary.contrastText" : "text.secondary",
                  mr: compact ? 0 : 1.25,
                  flexShrink: 0,
                  display: "grid",
                  placeItems: "center",
                }}
              >
                {iconMap[item.key]}
              </Box>
              {!compact ? <ListItemText primary={item.label} primaryTypographyProps={{ variant: "body2", noWrap: true }} /> : null}
            </ListItemButton>
          );
          return compact ? (
            <Tooltip key={item.key} title={item.label} placement="right">
              {content}
            </Tooltip>
          ) : (
            content
          );
        })}
      </List>

      <Box sx={{ flex: 1 }} />

      <Divider />

      <Box sx={{ px: compact ? 1 : 2, py: 1.5, textAlign: compact ? "center" : "left" }}>
        <Typography variant="caption" sx={{ opacity: 0.7 }}>
          {compact ? "v0.1" : `v0.1 • ${auth.rolesUpper[0] || "User"}`}
        </Typography>
      </Box>
    </Drawer>
  );
}
