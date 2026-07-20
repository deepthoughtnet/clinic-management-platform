import type { AuthContextValue } from "../auth/AuthContext";

export const TENANT_MODULE_CODES = [
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
  "CAREPILOT",
] as const;

export type TenantModuleCode = (typeof TENANT_MODULE_CODES)[number];

export type AppFeatureId =
  | "clinic-dashboard"
  | "pharmacy-dashboard"
  | "laboratory-dashboard"
  | "patients"
  | "appointments"
  | "day-board"
  | "queue"
  | "doctor-availability"
  | "notifications"
  | "consultations"
  | "prescriptions"
  | "billing"
  | "cash-counter"
  | "payments"
  | "refunds"
  | "vaccinations"
  | "inventory"
  | "pharmacy-dispensing"
  | "pharmacy-pos"
  | "pharmacy-medicines"
  | "pharmacy-procurement"
  | "pharmacy-reconciliation"
  | "pharmacy-operations"
  | "pharmacy-stock-movements"
  | "reports"
  | "laboratory"
  | "carepilot"
  | "ai-copilot";

type ModuleDefinition = {
  id: TenantModuleCode;
  displayName: string;
  defaultLandingPage: string;
  navigationEntries: string[];
  routes: string[];
  dashboardWidgets: string[];
  featureFlags: string[];
};

type FeatureRule = {
  moduleAny?: TenantModuleCode[];
  moduleAll?: TenantModuleCode[];
  alwaysVisible?: boolean;
};

const DEFAULT_CLINIC_MODULES: TenantModuleCode[] = [
  "APPOINTMENTS",
  "CONSULTATION",
  "PRESCRIPTION",
  "BILLING",
  "VACCINATION",
  "INVENTORY",
  "REPORTS",
];

export const MODULE_REGISTRY: Record<TenantModuleCode, ModuleDefinition> = {
  APPOINTMENTS: {
    id: "APPOINTMENTS",
    displayName: "Appointments",
    defaultLandingPage: "/dashboard",
    navigationEntries: ["dashboard", "day-board", "appointments", "queue", "doctor-availability", "notifications", "patients"],
    routes: ["/dashboard", "/appointments", "/appointments/day-board", "/queue", "/doctors/availability", "/notifications", "/patients"],
    dashboardWidgets: ["appointment-summary", "queue-summary", "doctor-slots"],
    featureFlags: ["appointment.manage", "appointment.read"],
  },
  CONSULTATION: {
    id: "CONSULTATION",
    displayName: "Consultation",
    defaultLandingPage: "/dashboard",
    navigationEntries: ["consultations"],
    routes: ["/consultations", "/consultations/:id"],
    dashboardWidgets: ["consultation-summary", "follow-up-summary"],
    featureFlags: ["consultation.manage", "consultation.read"],
  },
  PRESCRIPTION: {
    id: "PRESCRIPTION",
    displayName: "Prescription",
    defaultLandingPage: "/prescriptions",
    navigationEntries: ["prescription-register", "dispense-queue"],
    routes: ["/prescriptions", "/pharmacy/dispensing"],
    dashboardWidgets: ["prescription-summary", "dispense-queue"],
    featureFlags: ["prescription.read"],
  },
  BILLING: {
    id: "BILLING",
    displayName: "Billing",
    defaultLandingPage: "/billing",
    navigationEntries: ["billing", "cash-counter", "payments", "refunds", "pharmacy-pos"],
    routes: ["/billing", "/finance/cash-counter", "/finance/payments", "/finance/refunds", "/pharmacy/pos"],
    dashboardWidgets: ["billing-summary", "payment-summary"],
    featureFlags: ["billing.read", "billing.create", "payment.collect"],
  },
  VACCINATION: {
    id: "VACCINATION",
    displayName: "Vaccination",
    defaultLandingPage: "/vaccinations",
    navigationEntries: ["vaccinations"],
    routes: ["/vaccinations"],
    dashboardWidgets: ["vaccination-summary"],
    featureFlags: ["vaccination.read"],
  },
  INVENTORY: {
    id: "INVENTORY",
    displayName: "Inventory",
    defaultLandingPage: "/pharmacy/dashboard",
    navigationEntries: ["pharmacy-dashboard", "prescription-register", "dispense-queue", "pharmacy-pos", "pharmacy-procure", "pharmacy-reconcile", "medicine-master", "inventory", "reports-audit"],
    routes: ["/pharmacy/dashboard", "/prescriptions", "/pharmacy/dispensing", "/pharmacy/pos", "/inventory", "/pharmacy/inventory", "/pharmacy/medicines", "/pharmacy/procurement", "/pharmacy/reconciliation", "/pharmacy/operations", "/pharmacy/stock-movements"],
    dashboardWidgets: ["inventory-health", "expiring-stock", "stock-movements"],
    featureFlags: ["inventory.manage", "inventory.read"],
  },
  PHARMACY_POS: {
    id: "PHARMACY_POS",
    displayName: "Pharmacy POS",
    defaultLandingPage: "/pharmacy/pos",
    navigationEntries: ["pharmacy-pos"],
    routes: ["/pharmacy/pos"],
    dashboardWidgets: ["pharmacy-pos-summary"],
    featureFlags: ["billing.create", "payment.collect", "billing.read"],
  },
  LABORATORY: {
    id: "LABORATORY",
    displayName: "Laboratory",
    defaultLandingPage: "/lab",
    navigationEntries: ["laboratory"],
    routes: ["/lab"],
    dashboardWidgets: ["lab-orders", "lab-payments"],
    featureFlags: ["lab.order.read", "lab.order.manage"],
  },
  REPORTS: {
    id: "REPORTS",
    displayName: "Reports",
    defaultLandingPage: "/reports",
    navigationEntries: ["reports"],
    routes: ["/reports"],
    dashboardWidgets: ["reports-summary"],
    featureFlags: ["report.read"],
  },
  AI_COPILOT: {
    id: "AI_COPILOT",
    displayName: "AI Copilot",
    defaultLandingPage: "/admin/ai-ops",
    navigationEntries: ["ai-ops", "realtime-ai", "ai-reasoning-console"],
    routes: ["/admin/ai-ops", "/admin/realtime-ai", "/admin/ai-reasoning-console"],
    dashboardWidgets: [],
    featureFlags: ["ai.voice.test"],
  },
  CAREPILOT: {
    id: "CAREPILOT",
    displayName: "Engage",
    defaultLandingPage: "/carepilot/campaigns",
    navigationEntries: ["campaigns", "analytics", "ops-console", "messaging", "reminders", "patient-engagement", "leads", "webinar-automation", "ai-operations"],
    routes: ["/carepilot/campaigns", "/carepilot/analytics", "/carepilot/ops", "/carepilot/messaging", "/carepilot/reminders", "/carepilot/engagement", "/carepilot/leads", "/carepilot/webinars", "/carepilot/ai-operations", "/carepilot/ai-calls", "/carepilot/ai-receptionist/active-conversations", "/carepilot/ai-receptionist/callback-queue", "/carepilot/ai-receptionist/escalation-queue", "/carepilot/ai-receptionist/appointment-handoffs", "/carepilot/receptionist-queue"],
    dashboardWidgets: [],
    featureFlags: ["carepilot.access"],
  },
};

export const FEATURE_REGISTRY: Record<AppFeatureId, FeatureRule> = {
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
  "ai-copilot": { moduleAny: ["AI_COPILOT"] },
};

function normalizeModuleMap(modules: Record<string, boolean | null | undefined> | null | undefined) {
  if (!modules) return null;
  const normalized = Object.entries(modules).reduce<Record<string, boolean>>((acc, [key, value]) => {
    acc[key.trim().toUpperCase()] = value === true;
    return acc;
  }, {});
  return Object.keys(normalized).length ? normalized : null;
}

function legacyModuleFallback(auth: Pick<AuthContextValue, "tenantModules">) {
  const legacy = auth.tenantModules;
  if (!legacy) return null;
  const next: Record<string, boolean> = {};
  if (legacy.carePilot === true) next.CAREPILOT = true;
  if (legacy.aiCopilot === true) next.AI_COPILOT = true;
  return Object.keys(next).length ? next : null;
}

function addPharmacyPosFallback(source: Record<string, boolean>) {
  const hasStandalonePharmacyShape = source.INVENTORY === true
    && source.PRESCRIPTION === true
    && source.APPOINTMENTS !== true
    && source.CONSULTATION !== true
    && source.VACCINATION !== true
    && source.LABORATORY !== true;
  const hasExplicitPharmacyPosFlag = Object.prototype.hasOwnProperty.call(source, "PHARMACY_POS");
  if (hasStandalonePharmacyShape && !hasExplicitPharmacyPosFlag) {
    source.PHARMACY_POS = true;
  }
  return source;
}

export function resolveEnabledTenantModules(
  auth: Pick<AuthContextValue, "tenantId" | "tenantModules" | "enabledTenantModules" | "activeTenantMemberships">,
) {
  if (!auth.tenantId) return new Set<TenantModuleCode>();
  const membership = auth.activeTenantMemberships.find((item) => item.tenantId === auth.tenantId);
  const source = normalizeModuleMap(auth.enabledTenantModules)
    || normalizeModuleMap(membership?.enabledModules || null)
    || legacyModuleFallback(auth);
  if (!source) {
    return new Set<TenantModuleCode>(DEFAULT_CLINIC_MODULES);
  }
  const normalized = addPharmacyPosFallback({ ...source });
  const enabled = TENANT_MODULE_CODES.filter((code) => normalized[code] === true);
  return new Set<TenantModuleCode>(enabled);
}

export function hasEnabledTenantModule(
  auth: Pick<AuthContextValue, "tenantId" | "tenantModules" | "enabledTenantModules" | "activeTenantMemberships">,
  moduleCode: TenantModuleCode,
) {
  return resolveEnabledTenantModules(auth).has(moduleCode);
}

export function canAccessFeature(
  auth: Pick<AuthContextValue, "tenantId" | "tenantModules" | "enabledTenantModules" | "activeTenantMemberships">,
  featureId: AppFeatureId,
) {
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

export function resolveTenantLandingPage(
  auth: Pick<AuthContextValue, "tenantId" | "tenantModules" | "enabledTenantModules" | "activeTenantMemberships" | "tenantRole" | "rolesUpper" | "permissions">,
) {
  const enabled = resolveEnabledTenantModules(auth);
  const candidates = [
    enabled.has("INVENTORY") && (enabled.has("PRESCRIPTION") || enabled.has("BILLING")) && isPharmacyWorkspaceRole(auth) ? "/pharmacy/dashboard" : null,
    enabled.has("LABORATORY") && (isLabWorkspaceRole(auth) || hasLabReceptionAccess(auth)) ? "/lab" : null,
    enabled.has("BILLING") && isBillingWorkspaceRole(auth) ? "/billing" : null,
    (enabled.has("APPOINTMENTS") || enabled.has("CONSULTATION")) ? "/dashboard" : null,
    enabled.has("PRESCRIPTION") && isPharmacyWorkspaceRole(auth) ? "/prescriptions" : null,
    enabled.has("INVENTORY") && isPharmacyWorkspaceRole(auth) ? "/inventory" : null,
    enabled.has("REPORTS") ? "/reports" : null,
  ];
  for (const candidate of candidates) {
    if (candidate && isRouteAccessibleForAuth(auth, candidate)) return candidate;
  }
  return "/settings/clinic-profile";
}

function normalizeRoutePath(pathname: string): string {
  const normalized = pathname.split("?")[0].split("#")[0].trim();
  if (!normalized) return "/";
  return normalized.length > 1 ? normalized.replace(/\/+$/, "") : normalized;
}

function normalizeRoleValue(role: string | null | undefined): string {
  return (role || "").trim().replace(/[-\s]+/g, "_").toUpperCase();
}

function getActiveRoles(
  auth: Pick<AuthContextValue, "tenantRole" | "rolesUpper">,
) {
  const activeRoles = new Set((auth.rolesUpper || []).map((role) => normalizeRoleValue(role)));
  const tenantRole = normalizeRoleValue(auth.tenantRole);
  if (tenantRole) activeRoles.add(tenantRole);
  return activeRoles;
}

function hasAnyRole(activeRoles: Set<string>, ...roles: string[]) {
  return roles.some((role) => activeRoles.has(normalizeRoleValue(role)));
}

function hasLabReceptionAccess(
  auth: Pick<AuthContextValue, "tenantRole" | "rolesUpper" | "permissions">,
) {
  return auth.permissions.includes("lab.reception.access")
    || hasAnyRole(getActiveRoles(auth), "LAB_FRONT_DESK", "LAB_TECHNICIAN", "LAB_APPROVER", "LAB_ASSISTANT", "CLINIC_ADMIN", "PLATFORM_ADMIN");
}

export function isPharmacyWorkspaceRole(
  auth: Pick<AuthContextValue, "tenantRole" | "rolesUpper">,
) {
  return hasAnyRole(getActiveRoles(auth), "CLINIC_ADMIN", "PHARMA", "PHARMACY", "PHARMACIST", "PHARMACY_INVENTORY_MANAGER", "PHARMACY_POS_USER", "PLATFORM_ADMIN");
}

export function isLabWorkspaceRole(
  auth: Pick<AuthContextValue, "tenantRole" | "rolesUpper">,
) {
  return hasAnyRole(getActiveRoles(auth), "CLINIC_ADMIN", "LAB_FRONT_DESK", "LAB_TECHNICIAN", "LAB_APPROVER", "PLATFORM_ADMIN");
}

export function isBillingWorkspaceRole(
  auth: Pick<AuthContextValue, "tenantRole" | "rolesUpper">,
) {
  return hasAnyRole(getActiveRoles(auth), "CLINIC_ADMIN", "BILLING_USER", "AUDITOR", "PLATFORM_ADMIN");
}

export function isRouteAccessibleForAuth(
  auth: Pick<AuthContextValue, "tenantId" | "tenantRole" | "rolesUpper" | "tenantModules" | "enabledTenantModules" | "activeTenantMemberships" | "permissions">,
  pathname: string,
) {
  const path = normalizeRoutePath(pathname);
  const activeRoles = getActiveRoles(auth);
  const doctorRole = hasAnyRole(activeRoles, "DOCTOR");
  const billingRole = isBillingWorkspaceRole(auth);
  const pharmacyRole = isPharmacyWorkspaceRole(auth);
  const labRole = isLabWorkspaceRole(auth);
  const labReceptionRole = hasLabReceptionAccess(auth);
  const operationalRole = hasAnyRole(activeRoles, "CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "AUDITOR", "PLATFORM_ADMIN");

  if (path === "/" || path === "/dashboard") {
    return (canAccessFeature(auth, "clinic-dashboard") && operationalRole) || (!auth.tenantId && auth.rolesUpper.includes("PLATFORM_ADMIN"));
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
    if (path === "/platform/plans") return auth.rolesUpper.includes("PLATFORM_ADMIN") || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");
    if (path === "/platform/users") return auth.rolesUpper.includes("PLATFORM_ADMIN");
    return auth.rolesUpper.includes("PLATFORM_ADMIN");
  }
  if (path.startsWith("/carepilot/")) return canAccessFeature(auth, "carepilot");
  if (path.startsWith("/admin/") || path.startsWith("/ai/")) return true;
  if (path.startsWith("/doctors/")) return canAccessFeature(auth, "doctor-availability") || canAccessFeature(auth, "appointments");
  return true;
}
