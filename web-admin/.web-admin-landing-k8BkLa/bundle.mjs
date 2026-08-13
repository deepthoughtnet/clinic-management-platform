// src/auth/AuthContext.ts
import { createContext } from "react";
var AuthContext = createContext(null);

// src/pages/public/HealthcareLandingPage.tsx
import * as React from "react";
import { Link, Navigate } from "react-router-dom";
import { alpha as alpha2 } from "@mui/material/styles";
import {
  AdminPanelSettingsRounded,
  AutoAwesomeRounded,
  CampaignRounded,
  CheckCircleRounded,
  EventAvailableRounded,
  FavoriteRounded,
  HubRounded,
  LocalHospitalRounded,
  MedicalServicesRounded,
  MedicationRounded,
  ReceiptLongRounded,
  ScienceRounded,
  SearchRounded,
  ShieldRounded,
  GroupRounded,
  ArrowForwardRounded
} from "@mui/icons-material";
import {
  Box as Box2,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Divider,
  Paper,
  Stack as Stack2,
  Typography as Typography2
} from "@mui/material";

// src/auth/useAuth.ts
import { useContext } from "react";
function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error("AuthContext is not available");
  }
  return auth;
}

// src/branding.ts
var env = import.meta.env;
var branding = {
  productName: env.VITE_PRODUCT_NAME?.trim() || "Jeevanam Healthcare",
  tagline: env.VITE_PRODUCT_TAGLINE?.trim() || "Intelligent Healthcare Platform",
  companyName: env.VITE_COMPANY_NAME?.trim() || "DeepThoughtNet",
  aiPlatformName: env.VITE_AI_PLATFORM_NAME?.trim() || "AIVA"
};
function productTitle() {
  return `${branding.productName} | ${branding.tagline}`;
}

// src/config.ts
function trim(value) {
  return value?.trim() ?? "";
}
function providerFallback() {
  if (typeof window === "undefined") {
    return "https://discover.deepthoughtnet.com";
  }
  const url = new URL(window.location.origin);
  if (url.hostname === "localhost" || url.hostname === "127.0.0.1") {
    url.port = "5177";
    return url.toString().replace(/\/$/, "");
  }
  url.hostname = "discover.deepthoughtnet.com";
  url.pathname = "/";
  return url.toString().replace(/\/$/, "");
}
var adminConfig = {
  providerAppUrl: trim("") || providerFallback()
};

// src/modules/moduleRegistry.ts
var TENANT_MODULE_CODES = [
  "APPOINTMENTS",
  "CONSULTATION",
  "PRESCRIPTION",
  "BILLING",
  "VACCINATION",
  "INVENTORY",
  "PHARMACY_POS",
  "LABORATORY",
  "REPORTS",
  "AI_COPILOT",
  "CAREPILOT"
];
var DEFAULT_CLINIC_MODULES = [
  "APPOINTMENTS",
  "CONSULTATION",
  "PRESCRIPTION",
  "BILLING",
  "VACCINATION",
  "INVENTORY",
  "REPORTS"
];
var FEATURE_REGISTRY = {
  "clinic-dashboard": { moduleAny: ["APPOINTMENTS", "CONSULTATION"] },
  "pharmacy-dashboard": { moduleAny: ["INVENTORY", "PRESCRIPTION", "BILLING"] },
  "laboratory-dashboard": { moduleAny: ["LABORATORY"] },
  patients: { moduleAny: ["APPOINTMENTS", "CONSULTATION"] },
  appointments: { moduleAny: ["APPOINTMENTS"] },
  "day-board": { moduleAny: ["APPOINTMENTS"] },
  queue: { moduleAny: ["APPOINTMENTS"] },
  "doctor-availability": { moduleAny: ["APPOINTMENTS"] },
  notifications: { moduleAny: ["APPOINTMENTS"] },
  consultations: { moduleAny: ["CONSULTATION"] },
  prescriptions: { moduleAny: ["PRESCRIPTION"] },
  billing: { moduleAny: ["BILLING"] },
  "cash-counter": { moduleAny: ["BILLING"] },
  payments: { moduleAny: ["BILLING"] },
  refunds: { moduleAny: ["BILLING"] },
  vaccinations: { moduleAny: ["VACCINATION"] },
  inventory: { moduleAny: ["INVENTORY"] },
  "pharmacy-dispensing": { moduleAll: ["PRESCRIPTION", "INVENTORY"] },
  "pharmacy-pos": { moduleAny: ["PHARMACY_POS"] },
  "pharmacy-medicines": { moduleAny: ["INVENTORY"] },
  "pharmacy-procurement": { moduleAny: ["INVENTORY"] },
  "pharmacy-reconciliation": { moduleAny: ["INVENTORY"] },
  "pharmacy-operations": { moduleAny: ["INVENTORY"] },
  "pharmacy-stock-movements": { moduleAny: ["INVENTORY"] },
  reports: { moduleAny: ["REPORTS"] },
  laboratory: { moduleAny: ["LABORATORY"] },
  carepilot: { moduleAny: ["CAREPILOT"] },
  "ai-copilot": { moduleAny: ["AI_COPILOT"] }
};
function normalizeModuleMap(modules) {
  if (!modules) return null;
  const normalized = Object.entries(modules).reduce((acc, [key, value]) => {
    acc[key.trim().toUpperCase()] = value === true;
    return acc;
  }, {});
  return Object.keys(normalized).length ? normalized : null;
}
function legacyModuleFallback(auth) {
  const legacy = auth.tenantModules;
  if (!legacy) return null;
  const next = {};
  if (legacy.carePilot === true) next.CAREPILOT = true;
  if (legacy.aiCopilot === true) next.AI_COPILOT = true;
  return Object.keys(next).length ? next : null;
}
function addPharmacyPosFallback(source) {
  const hasStandalonePharmacyShape = source.INVENTORY === true && source.PRESCRIPTION === true && source.APPOINTMENTS !== true && source.CONSULTATION !== true && source.VACCINATION !== true && source.LABORATORY !== true;
  const hasExplicitPharmacyPosFlag = Object.prototype.hasOwnProperty.call(source, "PHARMACY_POS");
  if (hasStandalonePharmacyShape && !hasExplicitPharmacyPosFlag) {
    source.PHARMACY_POS = true;
  }
  return source;
}
function resolveEnabledTenantModules(auth) {
  if (!auth.tenantId) return /* @__PURE__ */ new Set();
  const membership = auth.activeTenantMemberships.find((item) => item.tenantId === auth.tenantId);
  const source = normalizeModuleMap(auth.enabledTenantModules) || normalizeModuleMap(membership?.enabledModules || null) || legacyModuleFallback(auth);
  if (!source) {
    return new Set(DEFAULT_CLINIC_MODULES);
  }
  const normalized = addPharmacyPosFallback({ ...source });
  const enabled = TENANT_MODULE_CODES.filter((code) => normalized[code] === true);
  return new Set(enabled);
}
function canAccessFeature(auth, featureId) {
  const rule = FEATURE_REGISTRY[featureId];
  if (!rule) return false;
  if (rule.alwaysVisible) return true;
  const enabledModules = resolveEnabledTenantModules(auth);
  if (rule.moduleAll && !rule.moduleAll.every((moduleCode) => enabledModules.has(moduleCode))) {
    return false;
  }
  if (rule.moduleAny && !rule.moduleAny.some((moduleCode) => enabledModules.has(moduleCode))) {
    return false;
  }
  return true;
}
function resolveTenantLandingPage(auth) {
  const enabled = resolveEnabledTenantModules(auth);
  const candidates = [
    enabled.has("INVENTORY") && (enabled.has("PRESCRIPTION") || enabled.has("BILLING")) && isPharmacyWorkspaceRole(auth) ? "/pharmacy/dashboard" : null,
    enabled.has("LABORATORY") && (isLabWorkspaceRole(auth) || hasLabReceptionAccess(auth)) ? "/lab" : null,
    enabled.has("BILLING") && isBillingWorkspaceRole(auth) ? "/billing" : null,
    enabled.has("APPOINTMENTS") || enabled.has("CONSULTATION") ? "/dashboard" : null,
    enabled.has("PRESCRIPTION") && isPharmacyWorkspaceRole(auth) ? "/prescriptions" : null,
    enabled.has("INVENTORY") && isPharmacyWorkspaceRole(auth) ? "/inventory" : null,
    enabled.has("REPORTS") ? "/reports" : null
  ];
  for (const candidate of candidates) {
    if (candidate && isRouteAccessibleForAuth(auth, candidate)) return candidate;
  }
  return "/settings/clinic-profile";
}
function normalizeRoutePath(pathname) {
  const normalized = pathname.split("?")[0].split("#")[0].trim();
  if (!normalized) return "/";
  return normalized.length > 1 ? normalized.replace(/\/+$/, "") : normalized;
}
function normalizeRoleValue(role) {
  return (role || "").trim().replace(/[-\s]+/g, "_").toUpperCase();
}
function getActiveRoles(auth) {
  const activeRoles = new Set((auth.rolesUpper || []).map((role) => normalizeRoleValue(role)));
  const tenantRole = normalizeRoleValue(auth.tenantRole);
  if (tenantRole) activeRoles.add(tenantRole);
  return activeRoles;
}
function hasAnyRole(activeRoles, ...roles) {
  return roles.some((role) => activeRoles.has(normalizeRoleValue(role)));
}
function hasLabReceptionAccess(auth) {
  return auth.permissions.includes("lab.reception.access") || hasAnyRole(getActiveRoles(auth), "LAB_FRONT_DESK", "LAB_TECHNICIAN", "LAB_APPROVER", "LAB_ASSISTANT", "CLINIC_ADMIN", "PLATFORM_ADMIN");
}
function isPharmacyWorkspaceRole(auth) {
  return hasAnyRole(getActiveRoles(auth), "CLINIC_ADMIN", "PHARMA", "PHARMACY", "PHARMACIST", "PHARMACY_INVENTORY_MANAGER", "PHARMACY_POS_USER", "PLATFORM_ADMIN");
}
function isLabWorkspaceRole(auth) {
  return hasAnyRole(getActiveRoles(auth), "CLINIC_ADMIN", "LAB_FRONT_DESK", "LAB_TECHNICIAN", "LAB_APPROVER", "PLATFORM_ADMIN");
}
function isBillingWorkspaceRole(auth) {
  return hasAnyRole(getActiveRoles(auth), "CLINIC_ADMIN", "BILLING_USER", "AUDITOR", "PLATFORM_ADMIN");
}
function isRouteAccessibleForAuth(auth, pathname) {
  const path = normalizeRoutePath(pathname);
  const activeRoles = getActiveRoles(auth);
  const doctorRole = hasAnyRole(activeRoles, "DOCTOR");
  const billingRole = isBillingWorkspaceRole(auth);
  const pharmacyRole = isPharmacyWorkspaceRole(auth);
  const labRole = isLabWorkspaceRole(auth);
  const labReceptionRole = hasLabReceptionAccess(auth);
  const operationalRole = hasAnyRole(activeRoles, "CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "AUDITOR", "PLATFORM_ADMIN");
  if (path === "/" || path === "/dashboard") {
    return canAccessFeature(auth, "clinic-dashboard") && operationalRole || !auth.tenantId && auth.rolesUpper.includes("PLATFORM_ADMIN");
  }
  if (path === "/pharmacy/dashboard") {
    return canAccessFeature(auth, "pharmacy-dashboard") && pharmacyRole;
  }
  if (path === "/patients") return canAccessFeature(auth, "patients");
  if (path === "/patients/new") return canAccessFeature(auth, "patients");
  if (path === "/patients/:id/edit") return canAccessFeature(auth, "patients");
  if (/^\/patients\/[^/]+\/edit$/.test(path)) return canAccessFeature(auth, "patients");
  if (/^\/patients\/[^/]+$/.test(path)) return canAccessFeature(auth, "patients") || doctorRole;
  if (path.startsWith("/patients/")) return canAccessFeature(auth, "patients");
  if (path === "/appointments" || path.startsWith("/appointments/")) return canAccessFeature(auth, "appointments") || canAccessFeature(auth, "day-board");
  if (path === "/queue") return canAccessFeature(auth, "queue");
  if (path === "/consultations") return canAccessFeature(auth, "consultations");
  if (path.startsWith("/consultations/")) return canAccessFeature(auth, "consultations") && doctorRole;
  if (path === "/prescriptions") return canAccessFeature(auth, "prescriptions");
  if (path === "/billing") return canAccessFeature(auth, "billing") && billingRole;
  if (path === "/notification-center") return Boolean(auth.tenantId) && auth.permissions.includes("notification.center.read");
  if (path.startsWith("/finance/")) {
    if (path === "/finance/cash-counter") return canAccessFeature(auth, "cash-counter");
    if (path === "/finance/payments") return canAccessFeature(auth, "payments");
    if (path === "/finance/refunds") return canAccessFeature(auth, "refunds");
    return canAccessFeature(auth, "billing") && billingRole;
  }
  if (path === "/vaccinations") return canAccessFeature(auth, "vaccinations");
  if (path === "/inventory" || path.startsWith("/pharmacy/inventory") || path.startsWith("/pharmacy/medicines") || path.startsWith("/pharmacy/procurement") || path.startsWith("/pharmacy/reconciliation") || path.startsWith("/pharmacy/operations") || path.startsWith("/pharmacy/stock-movements")) {
    return canAccessFeature(auth, "inventory") && pharmacyRole;
  }
  if (path === "/pharmacy/dispensing") return canAccessFeature(auth, "pharmacy-dispensing") && pharmacyRole;
  if (path === "/pharmacy/pos") return canAccessFeature(auth, "pharmacy-pos") && pharmacyRole;
  if (path === "/pharmacy/procure" || path === "/pharmacy/procure-test") return canAccessFeature(auth, "pharmacy-procurement") && pharmacyRole;
  if (path === "/pharmacy/reconcile" || path === "/pharmacy/reconcile-test") return canAccessFeature(auth, "pharmacy-reconciliation") && pharmacyRole;
  if (path === "/reports") return canAccessFeature(auth, "reports");
  if (path === "/lab" || path === "/laboratory") return canAccessFeature(auth, "laboratory") && (labRole || labReceptionRole);
  if (path.startsWith("/platform/")) {
    if (path === "/platform/tenants") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/help") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/product-implementation") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/provider-connections") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path.startsWith("/platform/provider-connections/")) return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/discover/provider-applications") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path.startsWith("/platform/discover/provider-applications/")) return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/commercial") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/commercial/catalog") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/commercial/plans") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path.startsWith("/platform/commercial/plans/")) return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/plans") return auth.rolesUpper.includes("PLATFORM_ADMIN") || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");
    if (path === "/platform/commercial-catalog") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    if (path === "/platform/users") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    return auth.rolesUpper.includes("PLATFORM_ADMIN");
  }
  if (path.startsWith("/carepilot/")) return canAccessFeature(auth, "carepilot");
  if (path.startsWith("/admin/") || path.startsWith("/ai/")) return true;
  if (path.startsWith("/doctors/")) return canAccessFeature(auth, "doctor-availability") || canAccessFeature(auth, "appointments");
  return true;
}

// src/shared/components/branding/BrandMark.tsx
import { alpha } from "@mui/material/styles";
import { Box, Stack, Tooltip, Typography } from "@mui/material";
import MedicalServicesRoundedIcon from "@mui/icons-material/MedicalServicesRounded";
import AutoAwesomeRoundedIcon from "@mui/icons-material/AutoAwesomeRounded";
import { jsx, jsxs } from "react/jsx-runtime";
function BrandMark({
  compact = false,
  size = 40,
  showCopy = true,
  title = branding.productName,
  subtitle = branding.tagline
}) {
  const iconBlock = /* @__PURE__ */ jsxs(
    Box,
    {
      sx: (theme) => ({
        width: size,
        height: size,
        borderRadius: compact ? 2 : 3,
        display: "grid",
        placeItems: "center",
        position: "relative",
        flexShrink: 0,
        color: "common.white",
        background: `linear-gradient(145deg, ${theme.palette.primary.dark}, ${theme.palette.primary.main})`,
        boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.24)}`,
        overflow: "hidden"
      }),
      children: [
        /* @__PURE__ */ jsx(MedicalServicesRoundedIcon, { sx: { fontSize: Math.max(18, Math.round(size * 0.58)) } }),
        /* @__PURE__ */ jsx(
          AutoAwesomeRoundedIcon,
          {
            sx: {
              position: "absolute",
              top: 5,
              right: 5,
              fontSize: Math.max(10, Math.round(size * 0.24)),
              opacity: 0.95
            }
          }
        )
      ]
    }
  );
  if (compact || !showCopy) {
    return /* @__PURE__ */ jsx(Tooltip, { title, children: iconBlock });
  }
  return /* @__PURE__ */ jsxs(Stack, { direction: "row", alignItems: "center", spacing: 1.5, sx: { minWidth: 0 }, children: [
    iconBlock,
    /* @__PURE__ */ jsxs(Box, { sx: { minWidth: 0 }, children: [
      /* @__PURE__ */ jsx(
        Typography,
        {
          variant: "subtitle1",
          sx: {
            fontWeight: 700,
            lineHeight: 1.1,
            letterSpacing: -0.2,
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis"
          },
          children: title
        }
      ),
      /* @__PURE__ */ jsx(
        Typography,
        {
          variant: "caption",
          color: "text.secondary",
          sx: {
            display: "block",
            lineHeight: 1.15,
            fontWeight: 500,
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis"
          },
          children: subtitle
        }
      )
    ] })
  ] });
}

// src/pages/public/HealthcareLandingPage.tsx
import { jsx as jsx2, jsxs as jsxs2 } from "react/jsx-runtime";
var operationsCards = [
  {
    title: "Reception & Appointments",
    description: "Patient registration, appointment handling, queue management and consultation handoff.",
    icon: /* @__PURE__ */ jsx2(EventAvailableRounded, { fontSize: "inherit" })
  },
  {
    title: "Doctor Workspace",
    description: "Consultation, diagnosis, prescriptions, investigations and clinical history.",
    icon: /* @__PURE__ */ jsx2(MedicalServicesRounded, { fontSize: "inherit" })
  },
  {
    title: "Billing",
    description: "Billing workflows, payments and financial operations.",
    icon: /* @__PURE__ */ jsx2(ReceiptLongRounded, { fontSize: "inherit" })
  },
  {
    title: "Pharmacy",
    description: "Procurement, GRN, inventory, POS and reconciliation.",
    icon: /* @__PURE__ */ jsx2(MedicationRounded, { fontSize: "inherit" })
  },
  {
    title: "Laboratory",
    description: "Orders, sample collection, results, verification and reporting.",
    icon: /* @__PURE__ */ jsx2(ScienceRounded, { fontSize: "inherit" })
  },
  {
    title: "Patient Engagement",
    description: "Campaigns, reminders, follow-ups and operational engagement workflows.",
    icon: /* @__PURE__ */ jsx2(CampaignRounded, { fontSize: "inherit" })
  },
  {
    title: "AIVA / AI Assistance",
    description: "Optional AI-assisted clinical and operational workflows when teams want them.",
    icon: /* @__PURE__ */ jsx2(AutoAwesomeRounded, { fontSize: "inherit" })
  },
  {
    title: "Administration",
    description: "Users, roles, tenant configuration, audit and operational controls.",
    icon: /* @__PURE__ */ jsx2(AdminPanelSettingsRounded, { fontSize: "inherit" })
  }
];
var audienceCards = [
  {
    title: "Clinics",
    description: "Coordinate reception, doctors, billing, pharmacy, lab and patient follow-up.",
    icon: /* @__PURE__ */ jsx2(GroupRounded, { fontSize: "inherit" })
  },
  {
    title: "Hospitals",
    description: "Manage connected clinical and administrative workflows across teams.",
    icon: /* @__PURE__ */ jsx2(LocalHospitalRounded, { fontSize: "inherit" })
  },
  {
    title: "Diagnostic / Lab operations",
    description: "Support collection, processing, verification and report publication.",
    icon: /* @__PURE__ */ jsx2(ScienceRounded, { fontSize: "inherit" })
  }
];
var ecosystemCards = [
  {
    title: "Jeevanam Discover",
    description: "Find doctors, clinics, hospitals and appointment options.",
    icon: /* @__PURE__ */ jsx2(SearchRounded, { fontSize: "inherit" })
  },
  {
    title: "Jeevanam Connect",
    description: "Manage your provider profile, publish your presence, and connect with patients.",
    icon: /* @__PURE__ */ jsx2(HubRounded, { fontSize: "inherit" }),
    cta: null
  },
  {
    title: "Jeevanam Care",
    description: "Patient appointments, prescriptions, reports, bills and care journey.",
    icon: /* @__PURE__ */ jsx2(FavoriteRounded, { fontSize: "inherit" })
  },
  {
    title: "Jeevanam Healthcare",
    description: "Clinical and administrative operations for clinics and hospitals.",
    icon: /* @__PURE__ */ jsx2(LocalHospitalRounded, { fontSize: "inherit" })
  }
];
function SectionHeading({
  eyebrow,
  title,
  description
}) {
  return /* @__PURE__ */ jsxs2(Stack2, { spacing: 1.25, sx: { maxWidth: 760 }, children: [
    /* @__PURE__ */ jsx2(
      Chip,
      {
        label: eyebrow,
        size: "small",
        sx: (theme) => ({
          alignSelf: "flex-start",
          bgcolor: alpha2(theme.palette.primary.main, 0.1),
          color: theme.palette.primary.dark,
          fontWeight: 800,
          letterSpacing: 0.5
        })
      }
    ),
    /* @__PURE__ */ jsx2(Typography2, { component: "h2", variant: "h4", sx: { fontWeight: 900, letterSpacing: -0.6 }, children: title }),
    /* @__PURE__ */ jsx2(Typography2, { variant: "body1", color: "text.secondary", sx: { lineHeight: 1.75 }, children: description })
  ] });
}
function CapabilityCard({ title, description, icon }) {
  return /* @__PURE__ */ jsx2(
    Card,
    {
      elevation: 0,
      sx: (theme) => ({
        height: "100%",
        borderRadius: 4,
        border: "1px solid",
        borderColor: alpha2(theme.palette.primary.main, 0.12),
        bgcolor: "background.paper",
        boxShadow: `0 18px 48px ${alpha2(theme.palette.common.black, 0.05)}`
      }),
      children: /* @__PURE__ */ jsxs2(CardContent, { sx: { p: 3, display: "flex", flexDirection: "column", gap: 1.5, height: "100%" }, children: [
        /* @__PURE__ */ jsx2(
          Box2,
          {
            sx: (theme) => ({
              width: 48,
              height: 48,
              borderRadius: 3,
              display: "grid",
              placeItems: "center",
              color: theme.palette.primary.main,
              bgcolor: alpha2(theme.palette.primary.main, 0.08)
            }),
            children: icon
          }
        ),
        /* @__PURE__ */ jsx2(Typography2, { variant: "h6", sx: { fontWeight: 850, letterSpacing: -0.2 }, children: title }),
        /* @__PURE__ */ jsx2(Typography2, { variant: "body2", color: "text.secondary", sx: { lineHeight: 1.7 }, children: description })
      ] })
    }
  );
}
function EcosystemCardView({ title, description, icon, cta }) {
  return /* @__PURE__ */ jsx2(
    Card,
    {
      elevation: 0,
      sx: (theme) => ({
        height: "100%",
        borderRadius: 4,
        border: "1px solid",
        borderColor: alpha2(theme.palette.secondary.main, 0.14),
        bgcolor: "background.paper",
        boxShadow: `0 18px 48px ${alpha2(theme.palette.common.black, 0.05)}`
      }),
      children: /* @__PURE__ */ jsxs2(CardContent, { sx: { p: 3, display: "flex", flexDirection: "column", gap: 1.5, height: "100%" }, children: [
        /* @__PURE__ */ jsx2(
          Box2,
          {
            sx: (theme) => ({
              width: 48,
              height: 48,
              borderRadius: 3,
              display: "grid",
              placeItems: "center",
              color: theme.palette.secondary.main,
              bgcolor: alpha2(theme.palette.secondary.main, 0.08)
            }),
            children: icon
          }
        ),
        /* @__PURE__ */ jsx2(Typography2, { variant: "h6", sx: { fontWeight: 850, letterSpacing: -0.2 }, children: title }),
        /* @__PURE__ */ jsx2(Typography2, { variant: "body2", color: "text.secondary", sx: { lineHeight: 1.7, flex: 1 }, children: description }),
        /* @__PURE__ */ jsx2(Box2, { sx: { pt: 0.5 }, children: cta ?? /* @__PURE__ */ jsx2(Typography2, { variant: "caption", color: "text.secondary", sx: { fontWeight: 700 }, children: "Product surface" }) })
      ] })
    }
  );
}
function WorkflowStep({ label, detail }) {
  return /* @__PURE__ */ jsxs2(
    Paper,
    {
      elevation: 0,
      sx: (theme) => ({
        minWidth: 180,
        flex: "1 1 180px",
        p: 2,
        borderRadius: 3,
        border: "1px solid",
        borderColor: alpha2(theme.palette.primary.main, 0.12),
        bgcolor: alpha2(theme.palette.background.paper, 0.9)
      }),
      children: [
        /* @__PURE__ */ jsx2(Typography2, { variant: "subtitle2", sx: { fontWeight: 850, mb: 0.5 }, children: label }),
        /* @__PURE__ */ jsx2(Typography2, { variant: "body2", color: "text.secondary", sx: { lineHeight: 1.65 }, children: detail })
      ]
    }
  );
}
function HealthcareLandingPage() {
  const auth = useAuth();
  React.useEffect(() => {
    document.title = productTitle();
  }, []);
  if (auth.initialized && auth.authenticated) {
    return /* @__PURE__ */ jsx2(Navigate, { to: resolveTenantLandingPage(auth), replace: true });
  }
  const providerWorkspaceUrl = `${adminConfig.providerAppUrl.replace(/\/$/, "")}/provider/login`;
  const scrollToCapabilities = () => {
    document.getElementById("capabilities")?.scrollIntoView({ behavior: "smooth", block: "start" });
  };
  return /* @__PURE__ */ jsxs2(
    Box2,
    {
      sx: (theme) => ({
        minHeight: "100vh",
        background: `
          radial-gradient(circle at top left, ${alpha2(theme.palette.primary.main, 0.12)}, transparent 30%),
          radial-gradient(circle at top right, ${alpha2(theme.palette.secondary.main, 0.12)}, transparent 26%),
          linear-gradient(180deg, ${alpha2(theme.palette.background.default, 0.96)} 0%, ${theme.palette.background.default} 52%)
        `
      }),
      children: [
        /* @__PURE__ */ jsx2(
          Box2,
          {
            component: "header",
            sx: (theme) => ({
              position: "sticky",
              top: 0,
              zIndex: 10,
              backdropFilter: "blur(14px)",
              backgroundColor: alpha2(theme.palette.background.default, 0.84),
              borderBottom: "1px solid",
              borderColor: alpha2(theme.palette.divider, 0.8)
            }),
            children: /* @__PURE__ */ jsx2(Container, { maxWidth: "xl", sx: { py: 1.5 }, children: /* @__PURE__ */ jsxs2(Stack2, { direction: "row", alignItems: "center", justifyContent: "space-between", gap: 2, flexWrap: "wrap", children: [
              /* @__PURE__ */ jsx2(BrandMark, { title: branding.productName, subtitle: branding.tagline }),
              /* @__PURE__ */ jsxs2(Stack2, { direction: "row", spacing: 1, flexWrap: "wrap", justifyContent: "flex-end", useFlexGap: true, children: [
                /* @__PURE__ */ jsx2(Button, { component: Link, to: "#capabilities", color: "inherit", sx: { fontWeight: 700 }, children: "Capabilities" }),
                /* @__PURE__ */ jsx2(Button, { component: Link, to: "#who-it-is-for", color: "inherit", sx: { fontWeight: 700 }, children: "For Clinics" }),
                /* @__PURE__ */ jsx2(Button, { component: Link, to: "#who-it-is-for", color: "inherit", sx: { fontWeight: 700 }, children: "For Hospitals" }),
                /* @__PURE__ */ jsx2(Button, { component: Link, to: "#ecosystem", color: "inherit", sx: { fontWeight: 700 }, children: "Jeevanam Ecosystem" }),
                /* @__PURE__ */ jsx2(Button, { component: Link, to: "/login", variant: "contained", children: "Sign in" })
              ] })
            ] }) })
          }
        ),
        /* @__PURE__ */ jsxs2(Box2, { component: "main", children: [
          /* @__PURE__ */ jsx2(Container, { maxWidth: "xl", sx: { py: { xs: 5, md: 8 } }, children: /* @__PURE__ */ jsxs2(
            Box2,
            {
              sx: {
                display: "grid",
                gridTemplateColumns: { xs: "1fr", lg: "1.08fr 0.92fr" },
                gap: { xs: 4, lg: 6 },
                alignItems: "center"
              },
              children: [
                /* @__PURE__ */ jsxs2(Stack2, { spacing: 3, sx: { maxWidth: 820 }, children: [
                  /* @__PURE__ */ jsx2(
                    Chip,
                    {
                      label: "JEEVANAM HEALTHCARE",
                      sx: (theme) => ({
                        alignSelf: "flex-start",
                        bgcolor: alpha2(theme.palette.primary.main, 0.1),
                        color: theme.palette.primary.dark,
                        fontWeight: 900,
                        letterSpacing: 1.2
                      })
                    }
                  ),
                  /* @__PURE__ */ jsx2(Typography2, { component: "h1", variant: "h2", sx: { fontWeight: 950, letterSpacing: -1.4, lineHeight: 0.98 }, children: "Run your clinic or hospital on one connected healthcare platform." }),
                  /* @__PURE__ */ jsx2(Typography2, { variant: "h6", color: "text.secondary", sx: { lineHeight: 1.7, maxWidth: 760 }, children: "Manage patient journeys, consultations, billing, pharmacy, laboratory, engagement and operations from one secure workspace." }),
                  /* @__PURE__ */ jsx2(Stack2, { direction: "row", spacing: 1.5, flexWrap: "wrap", useFlexGap: true, children: ["Clinic operations", "Hospital workflows", "Role-based access", "AI-assisted workflows"].map((label) => /* @__PURE__ */ jsx2(
                    Chip,
                    {
                      label,
                      icon: /* @__PURE__ */ jsx2(CheckCircleRounded, { fontSize: "small" }),
                      variant: "outlined",
                      sx: { fontWeight: 700 }
                    },
                    label
                  )) }),
                  /* @__PURE__ */ jsxs2(Stack2, { direction: "row", spacing: 1.5, flexWrap: "wrap", useFlexGap: true, children: [
                    /* @__PURE__ */ jsx2(Button, { component: Link, to: "/login", size: "large", variant: "contained", endIcon: /* @__PURE__ */ jsx2(ArrowForwardRounded, {}), children: "Sign in to Healthcare" }),
                    /* @__PURE__ */ jsx2(Button, { size: "large", variant: "outlined", onClick: scrollToCapabilities, children: "Explore capabilities" })
                  ] })
                ] }),
                /* @__PURE__ */ jsx2(
                  Paper,
                  {
                    elevation: 0,
                    sx: (theme) => ({
                      borderRadius: 6,
                      p: { xs: 3, md: 4 },
                      border: "1px solid",
                      borderColor: alpha2(theme.palette.primary.main, 0.12),
                      background: `linear-gradient(180deg, ${alpha2(theme.palette.common.white, 0.9)} 0%, ${alpha2(theme.palette.primary.main, 0.03)} 100%)`,
                      boxShadow: `0 24px 72px ${alpha2(theme.palette.common.black, 0.06)}`
                    }),
                    children: /* @__PURE__ */ jsxs2(Stack2, { spacing: 2.25, children: [
                      /* @__PURE__ */ jsx2(BrandMark, { size: 48 }),
                      /* @__PURE__ */ jsx2(Divider, {}),
                      /* @__PURE__ */ jsxs2(Stack2, { spacing: 1.25, children: [
                        /* @__PURE__ */ jsx2(Typography2, { variant: "overline", sx: { fontWeight: 900, letterSpacing: 1.3 }, children: "What Healthcare gives you" }),
                        [
                          "One secure workspace for clinics and hospitals",
                          "Role-based access and tenant-aware controls",
                          "Operational workflows for every team",
                          "Optional AI assistance without forcing AI into core care"
                        ].map((item) => /* @__PURE__ */ jsxs2(Stack2, { direction: "row", spacing: 1.25, alignItems: "flex-start", children: [
                          /* @__PURE__ */ jsx2(CheckCircleRounded, { color: "primary", sx: { mt: 0.25, fontSize: 20 } }),
                          /* @__PURE__ */ jsx2(Typography2, { variant: "body2", color: "text.secondary", sx: { lineHeight: 1.65 }, children: item })
                        ] }, item))
                      ] })
                    ] })
                  }
                )
              ]
            }
          ) }),
          /* @__PURE__ */ jsx2(Box2, { id: "who-it-is-for", sx: { py: { xs: 4, md: 6 } }, children: /* @__PURE__ */ jsxs2(Container, { maxWidth: "xl", children: [
            /* @__PURE__ */ jsx2(
              SectionHeading,
              {
                eyebrow: "Who it is for",
                title: "Built for healthcare operations",
                description: "Jeevanam Healthcare is the operational platform used by clinics and hospitals. It keeps the workflows connected without forcing teams into a single rigid process."
              }
            ),
            /* @__PURE__ */ jsx2(
              Box2,
              {
                sx: {
                  mt: 3,
                  display: "grid",
                  gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" },
                  gap: 2
                },
                children: audienceCards.map((card) => /* @__PURE__ */ jsx2(CapabilityCard, { ...card }, card.title))
              }
            )
          ] }) }),
          /* @__PURE__ */ jsx2(Box2, { id: "capabilities", sx: { py: { xs: 4, md: 6 } }, children: /* @__PURE__ */ jsxs2(Container, { maxWidth: "xl", children: [
            /* @__PURE__ */ jsx2(
              SectionHeading,
              {
                eyebrow: "Core capabilities",
                title: "One platform. Connected workflows.",
                description: "Everything in Healthcare is designed to support the daily flow of a clinic or hospital without pulling teams out of their workspace."
              }
            ),
            /* @__PURE__ */ jsx2(
              Box2,
              {
                sx: {
                  mt: 3,
                  display: "grid",
                  gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))", lg: "repeat(4, minmax(0, 1fr))" },
                  gap: 2
                },
                children: operationsCards.map((card) => /* @__PURE__ */ jsx2(CapabilityCard, { ...card }, card.title))
              }
            )
          ] }) }),
          /* @__PURE__ */ jsx2(Box2, { sx: { py: { xs: 4, md: 6 } }, children: /* @__PURE__ */ jsx2(Container, { maxWidth: "xl", children: /* @__PURE__ */ jsx2(
            Box2,
            {
              sx: (theme) => ({
                borderRadius: 6,
                border: "1px solid",
                borderColor: alpha2(theme.palette.primary.main, 0.12),
                bgcolor: alpha2(theme.palette.primary.main, 0.03),
                p: { xs: 3, md: 4 }
              }),
              children: /* @__PURE__ */ jsxs2(Stack2, { spacing: 2.5, children: [
                /* @__PURE__ */ jsx2(
                  SectionHeading,
                  {
                    eyebrow: "Workflow",
                    title: "Designed around the way healthcare teams actually work.",
                    description: "The product follows the patient journey and the operational handoffs that clinics and hospitals already use."
                  }
                ),
                /* @__PURE__ */ jsxs2(
                  Box2,
                  {
                    sx: {
                      display: "flex",
                      flexWrap: "wrap",
                      gap: 1.5,
                      alignItems: "stretch"
                    },
                    children: [
                      /* @__PURE__ */ jsx2(WorkflowStep, { label: "Patient arrives", detail: "Registration, verification and the first operational handoff." }),
                      /* @__PURE__ */ jsx2(WorkflowStep, { label: "Reception", detail: "Scheduling, queueing and route-to-care coordination." }),
                      /* @__PURE__ */ jsx2(WorkflowStep, { label: "Consultation", detail: "Doctor-led clinical work, prescriptions and orders." }),
                      /* @__PURE__ */ jsx2(WorkflowStep, { label: "Investigation / Pharmacy", detail: "Lab processing, dispensing and related operations." }),
                      /* @__PURE__ */ jsx2(WorkflowStep, { label: "Billing", detail: "Payments, reconciliation and administrative closure." }),
                      /* @__PURE__ */ jsx2(WorkflowStep, { label: "Follow-up", detail: "Reminders, engagement and the next-care loop." })
                    ]
                  }
                )
              ] })
            }
          ) }) }),
          /* @__PURE__ */ jsx2(Box2, { sx: { py: { xs: 4, md: 6 } }, children: /* @__PURE__ */ jsx2(Container, { maxWidth: "xl", children: /* @__PURE__ */ jsxs2(
            Box2,
            {
              sx: {
                display: "grid",
                gridTemplateColumns: { xs: "1fr", lg: "1.05fr 0.95fr" },
                gap: { xs: 3, md: 4 },
                alignItems: "start"
              },
              children: [
                /* @__PURE__ */ jsx2(
                  Paper,
                  {
                    elevation: 0,
                    sx: (theme) => ({
                      p: { xs: 3, md: 4 },
                      borderRadius: 6,
                      border: "1px solid",
                      borderColor: alpha2(theme.palette.primary.main, 0.12)
                    }),
                    children: /* @__PURE__ */ jsxs2(Stack2, { spacing: 2, children: [
                      /* @__PURE__ */ jsx2(
                        SectionHeading,
                        {
                          eyebrow: "AI positioning",
                          title: "AI when you want it. Manual workflows when you don't.",
                          description: "Jeevanam Healthcare supports optional AI-assisted workflows while keeping core clinical and operational processes usable without AI."
                        }
                      ),
                      /* @__PURE__ */ jsx2(Stack2, { spacing: 1.5, children: [
                        "AI supports, but does not replace, clinicians and operational teams.",
                        "Manual workflows remain fully usable without enabling AI.",
                        "Use AI selectively for assistance, review and productivity."
                      ].map((item) => /* @__PURE__ */ jsxs2(Stack2, { direction: "row", spacing: 1.25, alignItems: "flex-start", children: [
                        /* @__PURE__ */ jsx2(AutoAwesomeRounded, { color: "primary", sx: { mt: 0.25, fontSize: 20 } }),
                        /* @__PURE__ */ jsx2(Typography2, { variant: "body2", color: "text.secondary", sx: { lineHeight: 1.65 }, children: item })
                      ] }, item)) })
                    ] })
                  }
                ),
                /* @__PURE__ */ jsx2(
                  Paper,
                  {
                    elevation: 0,
                    sx: (theme) => ({
                      p: { xs: 3, md: 4 },
                      borderRadius: 6,
                      border: "1px solid",
                      borderColor: alpha2(theme.palette.primary.main, 0.12)
                    }),
                    children: /* @__PURE__ */ jsxs2(Stack2, { spacing: 2, children: [
                      /* @__PURE__ */ jsx2(
                        SectionHeading,
                        {
                          eyebrow: "Platform foundations",
                          title: "Security and control built in.",
                          description: "Healthcare operations need clear access boundaries and reliable auditability."
                        }
                      ),
                      /* @__PURE__ */ jsx2(Stack2, { spacing: 1.35, children: [
                        "Role-based access",
                        "Multi-tenant architecture",
                        "Audit-ready workflows",
                        "Configurable modules",
                        "Controlled approvals",
                        "Secure operational access"
                      ].map((item) => /* @__PURE__ */ jsxs2(Stack2, { direction: "row", spacing: 1.25, alignItems: "flex-start", children: [
                        /* @__PURE__ */ jsx2(ShieldRounded, { color: "primary", sx: { mt: 0.25, fontSize: 20 } }),
                        /* @__PURE__ */ jsx2(Typography2, { variant: "body2", color: "text.secondary", sx: { lineHeight: 1.65 }, children: item })
                      ] }, item)) })
                    ] })
                  }
                )
              ]
            }
          ) }) }),
          /* @__PURE__ */ jsx2(Box2, { id: "ecosystem", sx: { py: { xs: 4, md: 6 } }, children: /* @__PURE__ */ jsxs2(Container, { maxWidth: "xl", children: [
            /* @__PURE__ */ jsx2(
              SectionHeading,
              {
                eyebrow: "Ecosystem",
                title: "One connected healthcare ecosystem",
                description: "Discover helps people find care. Connect helps providers manage their presence. Care supports the patient journey. Healthcare runs the clinical and administrative workspace."
              }
            ),
            /* @__PURE__ */ jsx2(
              Box2,
              {
                sx: {
                  mt: 3,
                  display: "grid",
                  gridTemplateColumns: { xs: "1fr", md: "repeat(2, minmax(0, 1fr))", xl: "repeat(4, minmax(0, 1fr))" },
                  gap: 2
                },
                children: ecosystemCards.map((card) => /* @__PURE__ */ jsx2(
                  EcosystemCardView,
                  {
                    ...card,
                    cta: card.title === "Jeevanam Connect" ? /* @__PURE__ */ jsx2(
                      Button,
                      {
                        component: "a",
                        href: providerWorkspaceUrl,
                        variant: "contained",
                        size: "small",
                        endIcon: /* @__PURE__ */ jsx2(ArrowForwardRounded, {}),
                        children: "Provider workspace"
                      }
                    ) : /* @__PURE__ */ jsx2(Typography2, { variant: "caption", color: "text.secondary", sx: { fontWeight: 700 }, children: "Product surface" })
                  },
                  card.title
                ))
              }
            )
          ] }) }),
          /* @__PURE__ */ jsx2(Box2, { sx: { py: { xs: 4, md: 6 } }, children: /* @__PURE__ */ jsx2(Container, { maxWidth: "xl", children: /* @__PURE__ */ jsx2(
            Paper,
            {
              elevation: 0,
              sx: (theme) => ({
                borderRadius: 6,
                p: { xs: 3, md: 4 },
                border: "1px solid",
                borderColor: alpha2(theme.palette.primary.main, 0.12),
                bgcolor: alpha2(theme.palette.primary.main, 0.03),
                textAlign: "center"
              }),
              children: /* @__PURE__ */ jsxs2(Stack2, { spacing: 2, alignItems: "center", children: [
                /* @__PURE__ */ jsx2(Typography2, { variant: "h4", sx: { fontWeight: 950, letterSpacing: -0.8 }, children: "Ready to manage your healthcare operations in one place?" }),
                /* @__PURE__ */ jsx2(Typography2, { variant: "body1", color: "text.secondary", sx: { maxWidth: 760, lineHeight: 1.75 }, children: "Sign in to the existing Healthcare workspace to continue into your operational shell, tenant context and role-based workflows." }),
                /* @__PURE__ */ jsxs2(Stack2, { direction: "row", spacing: 1.5, flexWrap: "wrap", justifyContent: "center", useFlexGap: true, children: [
                  /* @__PURE__ */ jsx2(Button, { component: Link, to: "/login", size: "large", variant: "contained", endIcon: /* @__PURE__ */ jsx2(ArrowForwardRounded, {}), children: "Sign in to Healthcare" }),
                  /* @__PURE__ */ jsx2(Button, { component: Link, to: "#capabilities", size: "large", variant: "outlined", children: "Explore capabilities" })
                ] })
              ] })
            }
          ) }) })
        ] }),
        /* @__PURE__ */ jsx2(Box2, { component: "footer", sx: { py: 3 }, children: /* @__PURE__ */ jsx2(Container, { maxWidth: "xl", children: /* @__PURE__ */ jsxs2(Stack2, { spacing: 0.75, alignItems: "center", textAlign: "center", children: [
          /* @__PURE__ */ jsx2(Typography2, { variant: "subtitle2", sx: { fontWeight: 900 }, children: "Jeevanam Healthcare" }),
          /* @__PURE__ */ jsx2(Typography2, { variant: "caption", color: "text.secondary", children: "Intelligent Healthcare Platform" })
        ] }) }) })
      ]
    }
  );
}
export {
  AuthContext,
  HealthcareLandingPage
};
