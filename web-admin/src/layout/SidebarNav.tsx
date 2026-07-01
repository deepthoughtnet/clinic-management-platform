import * as React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  Badge,
  Box,
  Chip,
  Collapse,
  Divider,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  Tooltip,
  Typography,
} from "@mui/material";
import DashboardRoundedIcon from "@mui/icons-material/DashboardRounded";
import EventRoundedIcon from "@mui/icons-material/EventRounded";
import ViewWeekRoundedIcon from "@mui/icons-material/ViewWeekRounded";
import QueueRoundedIcon from "@mui/icons-material/QueueRounded";
import CalendarMonthRoundedIcon from "@mui/icons-material/CalendarMonthRounded";
import PeopleAltRoundedIcon from "@mui/icons-material/PeopleAltRounded";
import MedicalServicesRoundedIcon from "@mui/icons-material/MedicalServicesRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";
import VaccinesRoundedIcon from "@mui/icons-material/VaccinesRounded";
import Inventory2RoundedIcon from "@mui/icons-material/Inventory2Rounded";
import MedicationRoundedIcon from "@mui/icons-material/MedicationRounded";
import LocalPharmacyRoundedIcon from "@mui/icons-material/LocalPharmacyRounded";
import PaymentsRoundedIcon from "@mui/icons-material/PaymentsRounded";
import BarChartRoundedIcon from "@mui/icons-material/BarChartRounded";
import AutorenewRoundedIcon from "@mui/icons-material/AutorenewRounded";
import CampaignRoundedIcon from "@mui/icons-material/CampaignRounded";
import MessageRoundedIcon from "@mui/icons-material/MessageRounded";
import NotificationsActiveRoundedIcon from "@mui/icons-material/NotificationsActiveRounded";
import GroupsRoundedIcon from "@mui/icons-material/GroupsRounded";
import ContactPhoneRoundedIcon from "@mui/icons-material/ContactPhoneRounded";
import SmartToyRoundedIcon from "@mui/icons-material/SmartToyRounded";
import SettingsRoundedIcon from "@mui/icons-material/SettingsRounded";
import BadgeRoundedIcon from "@mui/icons-material/BadgeRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import NotificationsRoundedIcon from "@mui/icons-material/NotificationsRounded";
import ExtensionRoundedIcon from "@mui/icons-material/ExtensionRounded";
import ApartmentRoundedIcon from "@mui/icons-material/ApartmentRounded";
import AutoAwesomeMotionRoundedIcon from "@mui/icons-material/AutoAwesomeMotionRounded";
import GroupRoundedIcon from "@mui/icons-material/GroupRounded";
import MonitorHeartRoundedIcon from "@mui/icons-material/MonitorHeartRounded";
import ExpandLessRoundedIcon from "@mui/icons-material/ExpandLessRounded";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";

import { NAV_GROUPS, type NavGroup, type NavItem } from "./nav";
import { useAuth } from "../auth/useAuth";
import { friendlyRoleLabel, hasTenantModule } from "../auth/moduleEntitlements";
import { branding } from "../branding";
import BrandMark from "../shared/components/branding/BrandMark";
import { canAccessFeature, resolveEnabledTenantModules } from "../modules/moduleRegistry";

const GROUP_STATE_STORAGE_KEY = "clinic_sidebar_group_state_v1";

export type SidebarNavProps = {
  open: boolean;
  variant: "permanent" | "temporary";
  width: number;
  onClose?: () => void;
};

function isActivePath(current: string, path: string): boolean {
  const normalizedPath = path.split("?")[0] || path;
  if (normalizedPath === "/") return current === "/" || current === "/dashboard";

  const prefixMatchPaths = new Set([
    "/patients",
    "/appointments",
    "/consultations",
    "/platform/tenants",
  ]);

  if (prefixMatchPaths.has(normalizedPath)) {
    return current === normalizedPath || current.startsWith(`${normalizedPath}/`);
  }

  return current === normalizedPath;
}

function iconFor(key: string): React.ReactNode {
  const iconMap: Record<string, React.ReactNode> = {
    "platform-dashboard": <DashboardRoundedIcon fontSize="small" />,
    "platform-tenants": <ApartmentRoundedIcon fontSize="small" />,
    "platform-plans": <AutoAwesomeMotionRoundedIcon fontSize="small" />,
    "platform-users": <GroupRoundedIcon fontSize="small" />,
    "platform-help": <DescriptionRoundedIcon fontSize="small" />,
    "platform-subscriptions": <ReceiptLongRoundedIcon fontSize="small" />,
    "platform-analytics": <BarChartRoundedIcon fontSize="small" />,
    "platform-health": <MonitorHeartRoundedIcon fontSize="small" />,
    dashboard: <DashboardRoundedIcon fontSize="small" />,
    "pharmacy-dashboard": <LocalPharmacyRoundedIcon fontSize="small" />,
    patients: <PeopleAltRoundedIcon fontSize="small" />,
    appointments: <EventRoundedIcon fontSize="small" />,
    "day-board": <ViewWeekRoundedIcon fontSize="small" />,
    "doctor-availability": <CalendarMonthRoundedIcon fontSize="small" />,
    queue: <QueueRoundedIcon fontSize="small" />,
    "queue-ops": <QueueRoundedIcon fontSize="small" />,
    consultations: <MedicalServicesRoundedIcon fontSize="small" />,
    prescriptions: <ReceiptLongRoundedIcon fontSize="small" />,
    billing: <PaymentsRoundedIcon fontSize="small" />,
    notifications: <NotificationsRoundedIcon fontSize="small" />,
    vaccinations: <VaccinesRoundedIcon fontSize="small" />,
    inventory: <Inventory2RoundedIcon fontSize="small" />,
    "pharmacy-ops": <Inventory2RoundedIcon fontSize="small" />,
    "pharmacy-operations": <Inventory2RoundedIcon fontSize="small" />,
    reports: <BarChartRoundedIcon fontSize="small" />,
    payments: <PaymentsRoundedIcon fontSize="small" />,
    refunds: <AutorenewRoundedIcon fontSize="small" />,
    "dispense-queue": <LocalPharmacyRoundedIcon fontSize="small" />,
    "reports-audit": <Inventory2RoundedIcon fontSize="small" />,
    "medicine-master": <MedicationRoundedIcon fontSize="small" />,
    "prescription-register": <ReceiptLongRoundedIcon fontSize="small" />,
    "pharmacy-procure": <Inventory2RoundedIcon fontSize="small" />,
    "pharmacy-reconcile": <Inventory2RoundedIcon fontSize="small" />,
    "pharmacy-procure-test": <Inventory2RoundedIcon fontSize="small" />,
    "pharmacy-reconcile-test": <Inventory2RoundedIcon fontSize="small" />,
    campaigns: <CampaignRoundedIcon fontSize="small" />,
    messaging: <MessageRoundedIcon fontSize="small" />,
    reminders: <NotificationsActiveRoundedIcon fontSize="small" />,
    "patient-engagement": <GroupsRoundedIcon fontSize="small" />,
    leads: <ContactPhoneRoundedIcon fontSize="small" />,
    "webinar-automation": <CampaignRoundedIcon fontSize="small" />,
    "ai-calls": <SmartToyRoundedIcon fontSize="small" />,
    "ai-receptionist-active": <SmartToyRoundedIcon fontSize="small" />,
    "ai-receptionist-callbacks": <ContactPhoneRoundedIcon fontSize="small" />,
    "ai-receptionist-escalations": <NotificationsActiveRoundedIcon fontSize="small" />,
    "ai-receptionist-handoffs": <CalendarMonthRoundedIcon fontSize="small" />,
    "settings-profile": <SettingsRoundedIcon fontSize="small" />,
    "settings-users-roles": <BadgeRoundedIcon fontSize="small" />,
    templates: <DescriptionRoundedIcon fontSize="small" />,
    "notification-settings": <NotificationsRoundedIcon fontSize="small" />,
    integrations: <ExtensionRoundedIcon fontSize="small" />,
  };
  return iconMap[key] || <DashboardRoundedIcon fontSize="small" />;
}

function roleDefaultExpanded(roles: Set<string>, groupKey: string): boolean {
  const isPlatformAdmin = roles.has("PLATFORM_ADMIN");
  const isBillingUser = roles.has("BILLING_USER");
  const isPharmacyRole = roles.has("PHARMA")
    || roles.has("PHARMACY")
    || roles.has("PHARMACIST")
    || roles.has("PHARMACY_INVENTORY_MANAGER")
    || roles.has("PHARMACY_POS_USER");
  if (isPlatformAdmin) return groupKey === "platform";
  if (isBillingUser) return groupKey === "finance";
  if (isPharmacyRole) return groupKey === "pharmacy";
  if (groupKey === "operations" || groupKey === "clinical") return true;
  return false;
}

export default function SidebarNav({ open, variant, width, onClose }: SidebarNavProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const auth = useAuth();
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const compact = variant === "permanent" && !open;
  const activeRoles = React.useMemo(() => {
    const next = new Set(auth.rolesUpper);
    const tenantRole = (auth.tenantRole || auth.activeTenantMemberships.find((membership) => membership.tenantId === auth.tenantId)?.role || "").toUpperCase();
    if (tenantRole) next.add(tenantRole);
    if (isPlatformAdmin && auth.tenantId) next.add("PLATFORM_TENANT_SUPPORT");
    return next;
  }, [auth.rolesUpper, auth.tenantRole, auth.activeTenantMemberships, auth.tenantId, isPlatformAdmin]);

  const carePilotEnabledForTenant = React.useMemo(() => hasTenantModule(auth, "carePilot"), [auth]);
  const enabledTenantModules = React.useMemo(() => resolveEnabledTenantModules(auth), [auth]);
  const isPharmacyOnlyTenant = React.useMemo(
    () => Boolean(auth.tenantId)
      && enabledTenantModules.has("INVENTORY")
      && !enabledTenantModules.has("APPOINTMENTS")
      && !enabledTenantModules.has("CONSULTATION")
      && !enabledTenantModules.has("VACCINATION")
      && !enabledTenantModules.has("LABORATORY"),
    [auth.tenantId, enabledTenantModules],
  );
  const tenantRole = React.useMemo(() => (auth.tenantRole || auth.activeTenantMemberships.find((membership) => membership.tenantId === auth.tenantId)?.role || "").toUpperCase(), [auth.activeTenantMemberships, auth.tenantId, auth.tenantRole]);
  const canUsePosSale = React.useMemo(
    () => auth.hasPermission("billing.create") || auth.hasPermission("payment.collect") || auth.rolesUpper.includes("CLINIC_ADMIN"),
    [auth],
  );
  const canSeeMedicineMaster = React.useMemo(
    () => auth.permissions.includes("medicine.read") || auth.hasPermission("inventory.manage") || auth.rolesUpper.includes("CLINIC_ADMIN"),
    [auth],
  );

  const visibleGroups = React.useMemo(() => {
    const isTenantSelected = Boolean(auth.tenantId);
    return NAV_GROUPS.map((group) => {
      if (group.platformOnly && !isPlatformAdmin) return null;
      if (group.requiresTenant && !isTenantSelected) return null;
      const groupRoleMatch = !group.rolesAny || group.rolesAny.some((role) => activeRoles.has(role));
      if (!groupRoleMatch && !(isPlatformAdmin && auth.tenantId)) return null;
      if (group.key === "carepilot" && !carePilotEnabledForTenant) return null;
      if (group.key === "clinical" && isPharmacyOnlyTenant) return null;

      const items = group.items.filter((item) => {
        if (item.platformOnly && !isPlatformAdmin) return false;
        if (item.requiresTenant && !isTenantSelected) return false;
        if (item.rolesAny && item.rolesAny.length > 0 && !item.rolesAny.some((role) => activeRoles.has(role)) && !(isPlatformAdmin && auth.tenantId)) return false;
        if (item.moduleAll && !item.moduleAll.every((moduleCode) => enabledTenantModules.has(moduleCode))) return false;
        if (item.moduleAny && !item.moduleAny.some((moduleCode) => enabledTenantModules.has(moduleCode))) return false;
        if (group.key === "pharmacy") {
          const inventoryManagerAllowed = new Set(["pharmacy-dashboard", "medicine-master", "inventory", "pharmacy-procurement", "pharmacy-reconciliation", "reports-audit"]);
          const posUserAllowed = new Set(["pharmacy-dashboard", "pharmacy-pos", "inventory", "reports-audit"]);
          if (tenantRole === "PHARMACY_POS_USER" && !posUserAllowed.has(item.key)) return false;
          if (tenantRole === "PHARMACY_INVENTORY_MANAGER" && !inventoryManagerAllowed.has(item.key) && !(item.key === "pharmacy-pos" && canUsePosSale)) return false;
          if ((tenantRole === "PHARMACY_POS_USER" || tenantRole === "PHARMACY_INVENTORY_MANAGER") && item.key === "pharmacy-pos" && !canUsePosSale) return false;
          if (item.key === "pharmacy-pos" && !canAccessFeature(auth, "pharmacy-pos")) return false;
          if (item.key === "medicine-master" && !canSeeMedicineMaster) return false;
          if (item.key === "pharmacy-pos" && activeRoles.has("PHARMACY_INVENTORY_MANAGER") && !canUsePosSale) return false;
        }
        if ((group.key === "operations" || group.key === "clinical" || group.key === "pharmacy" || group.key === "finance" || group.key === "carepilot" || group.key === "administration") && isPlatformAdmin && !auth.tenantId) return false;
        return true;
      });
      if (items.length === 0) return null;
      return { ...group, items };
    }).filter(Boolean) as NavGroup[];
  }, [activeRoles, auth.permissions, auth.rolesUpper, auth.tenantId, isPlatformAdmin, carePilotEnabledForTenant, canSeeMedicineMaster, canUsePosSale, enabledTenantModules]);

  const activeGroupKeys = React.useMemo(() => {
    return new Set(
      visibleGroups
        .filter((group) => group.items.some((item) => item.path && isActivePath(location.pathname, item.path)))
        .map((group) => group.key),
    );
  }, [visibleGroups, location.pathname]);

  const [expanded, setExpanded] = React.useState<Record<string, boolean>>({});

  React.useEffect(() => {
    try {
      const raw = localStorage.getItem(GROUP_STATE_STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as Record<string, boolean>) : {};
      const defaults: Record<string, boolean> = {};
      for (const group of visibleGroups) {
        defaults[group.key] = parsed[group.key]
          ?? (group.defaultExpanded ?? roleDefaultExpanded(activeRoles, group.key));
      }
      for (const key of activeGroupKeys) defaults[key] = true;
      setExpanded(defaults);
    } catch {
      const defaults: Record<string, boolean> = {};
      for (const group of visibleGroups) {
        defaults[group.key] = group.defaultExpanded ?? roleDefaultExpanded(activeRoles, group.key);
      }
      for (const key of activeGroupKeys) defaults[key] = true;
      setExpanded(defaults);
    }
  }, [visibleGroups, activeGroupKeys, activeRoles]);

  React.useEffect(() => {
    const merged = { ...expanded };
    let changed = false;
    for (const key of activeGroupKeys) {
      if (!merged[key]) {
        merged[key] = true;
        changed = true;
      }
    }
    if (changed) {
      setExpanded(merged);
      try {
        localStorage.setItem(GROUP_STATE_STORAGE_KEY, JSON.stringify(merged));
      } catch {
        // no-op
      }
    }
  }, [activeGroupKeys, expanded]);

  const toggleGroup = (groupKey: string) => {
    setExpanded((current) => {
      const next = { ...current, [groupKey]: !current[groupKey] };
      try {
        localStorage.setItem(GROUP_STATE_STORAGE_KEY, JSON.stringify(next));
      } catch {
        // no-op
      }
      return next;
    });
  };

  const renderItem = (item: NavItem) => {
    const active = Boolean(item.path && isActivePath(location.pathname, item.path));
    const labelNode = (
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1, minWidth: 0 }}>
        <ListItemText primary={item.label} primaryTypographyProps={{ variant: "body2", noWrap: true }} />
        {!compact && item.badge ? <Chip label={item.badge} size="small" variant="outlined" sx={{ height: 20, fontSize: 10 }} /> : null}
      </Box>
    );

    const content = (
      <ListItemButton
        key={item.key}
        component="button"
        type="button"
        onClick={(event) => {
          if (item.disabled || !item.path) return;
          event.preventDefault();
          navigate(item.path);
          if (variant === "temporary") onClose?.();
        }}
        disabled={item.disabled}
        sx={{
          mx: 1,
          my: 0.25,
          borderRadius: 1.75,
          bgcolor: active ? "action.selected" : undefined,
          px: compact ? 1 : 1.25,
          justifyContent: compact ? "center" : "flex-start",
          opacity: item.disabled ? 0.55 : 1,
          cursor: item.disabled ? "not-allowed" : undefined,
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
          {iconFor(item.key)}
        </Box>
        {!compact ? labelNode : null}
      </ListItemButton>
    );

    if (compact) {
      const title = item.badge ? `${item.label} (${item.badge})` : item.label;
      return <Tooltip key={item.key} title={title} placement="right">{content}</Tooltip>;
    }
    if (item.disabled && item.badge) {
      return <Tooltip key={item.key} title={item.badge} placement="right">{content}</Tooltip>;
    }
    return content;
  };

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
          <Box sx={{ display: "grid", placeItems: "center", py: 0.25 }}>
            <BrandMark compact size={34} showCopy={false} title={branding.productName} />
          </Box>
        ) : (
          <BrandMark size={38} />
        )}
      </Box>

      <Divider />

      <List sx={{ py: 0 }}>
        {visibleGroups.map((group) => (
          <Box key={group.key}>
            <ListItemButton
              onClick={() => toggleGroup(group.key)}
              sx={{
                px: compact ? 1.25 : 2,
                py: compact ? 1 : 1.25,
                justifyContent: compact ? "center" : "space-between",
              }}
            >
              {!compact ? (
                <Typography variant="overline" sx={{ opacity: 0.72, letterSpacing: 0.8 }}>
                  {group.label.toUpperCase()}
                </Typography>
              ) : (
                <Badge color="default" variant="dot" overlap="circular">
                  <Box sx={{ width: 8, height: 8, borderRadius: "50%", bgcolor: "text.disabled" }} />
                </Badge>
              )}
              {!compact ? (expanded[group.key] ? <ExpandLessRoundedIcon fontSize="small" /> : <ExpandMoreRoundedIcon fontSize="small" />) : null}
            </ListItemButton>
            <Collapse in={compact ? true : Boolean(expanded[group.key])} timeout="auto" unmountOnExit={!compact}>
              {group.items.map(renderItem)}
            </Collapse>
          </Box>
        ))}
      </List>

      <Box sx={{ flex: 1 }} />

      <Divider />

      <Box sx={{ px: compact ? 1 : 2, py: 1.5, textAlign: compact ? "center" : "left" }}>
        <Typography variant="caption" sx={{ opacity: 0.7 }}>
          {compact ? "v0.1" : `v0.1 • ${friendlyRoleLabel(auth)}`}
        </Typography>
      </Box>
    </Drawer>
  );
}
