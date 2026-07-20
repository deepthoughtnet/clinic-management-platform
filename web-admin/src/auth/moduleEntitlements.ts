import type { AuthContextValue } from "./AuthContext";
import { hasEnabledTenantModule, type TenantModuleCode } from "../modules/moduleRegistry";

type ModuleFlag = "carePilot" | "aiCopilot" | TenantModuleCode;

const ROLE_PRIORITY = [
  "PLATFORM_ADMIN",
  "CLINIC_ADMIN",
  "DOCTOR",
  "RECEPTIONIST",
  "BILLING_USER",
  "AUDITOR",
  "PHARMACIST",
  "PHARMACY",
  "PHARMA",
  "PHARMACY_INVENTORY_MANAGER",
  "PHARMACY_POS_USER",
  "LAB_FRONT_DESK",
  "LAB_TECHNICIAN",
  "LAB_ASSISTANT",
  "LAB_APPROVER",
  "PLATFORM_TENANT_SUPPORT",
  "ENGAGE_MANAGER",
  "ENGAGE_EXECUTIVE",
  "SERVICE_AGENT",
] as const;

const ROLE_LABELS: Record<string, string> = {
  PLATFORM_ADMIN: "Platform Admin",
  CLINIC_ADMIN: "Clinic Admin",
  DOCTOR: "Doctor",
  RECEPTIONIST: "Receptionist",
  BILLING_USER: "Billing User",
  AUDITOR: "Auditor",
  PHARMACIST: "Pharmacist",
  PHARMACY: "Pharmacy",
  PHARMA: "Pharma",
  PHARMACY_INVENTORY_MANAGER: "Pharmacy Inventory Manager",
  PHARMACY_POS_USER: "Pharmacy POS User",
  LAB_FRONT_DESK: "Lab Front Desk",
  LAB_TECHNICIAN: "Lab Technician",
  LAB_ASSISTANT: "Lab Assistant",
  LAB_APPROVER: "Lab Approver",
  PLATFORM_TENANT_SUPPORT: "Platform Support",
  ENGAGE_MANAGER: "Engage Manager",
  ENGAGE_EXECUTIVE: "Engage Executive",
  SERVICE_AGENT: "Service Agent",
};

function normalizeRole(role: string | null | undefined): string {
  return (role || "")
    .trim()
    .replace(/[-\s]+/g, "_")
    .replace(/^ROLE_/, "")
    .toUpperCase();
}

function isInternalRole(role: string | null | undefined): boolean {
  const normalized = normalizeRole(role);
  return normalized.startsWith("DEFAULT_ROLES")
    || normalized === "OFFLINE_ACCESS"
    || normalized === "UMA_AUTHORIZATION"
    || normalized === "";
}

function labelForRole(role: string): string {
  return ROLE_LABELS[role] || role.toLowerCase().replace(/_/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
}

export function hasTenantModule(
  auth: Pick<AuthContextValue, "tenantId" | "tenantModules" | "enabledTenantModules" | "activeTenantMemberships">,
  moduleKey: ModuleFlag,
): boolean {
  if (moduleKey === "carePilot") {
    return hasEnabledTenantModule(auth, "CAREPILOT");
  }
  if (moduleKey === "aiCopilot") {
    return hasEnabledTenantModule(auth, "AI_COPILOT");
  }
  if (!auth.tenantId) return false;
  return hasEnabledTenantModule(auth, moduleKey);
}

export function friendlyRoleLabel(auth: Pick<AuthContextValue, "tenantRole" | "rolesUpper" | "activeTenantMemberships" | "tenantId">): string {
  const candidates = [
    auth.tenantRole,
    ...(auth.rolesUpper || []),
    ...(auth.activeTenantMemberships || [])
      .filter((membership) => auth.tenantId ? membership.tenantId === auth.tenantId : true)
      .map((membership) => membership.role || ""),
  ];

  const meaningfulRoles = candidates
    .map((role) => normalizeRole(role))
    .filter((role) => !isInternalRole(role));

  const prioritized = ROLE_PRIORITY.find((role) => meaningfulRoles.includes(role));
  if (prioritized) {
    return labelForRole(prioritized);
  }

  const fallback = meaningfulRoles[0];
  return fallback ? labelForRole(fallback) : "User";
}
