import type { AuthContextValue } from "./AuthContext";

type ModuleFlag = "carePilot" | "aiCopilot";

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
  "LAB_ASSISTANT",
  "PLATFORM_TENANT_SUPPORT",
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
  LAB_ASSISTANT: "Lab Assistant",
  PLATFORM_TENANT_SUPPORT: "Platform Support",
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
  auth: Pick<AuthContextValue, "tenantId" | "tenantModules" | "activeTenantMemberships">,
  moduleKey: ModuleFlag,
): boolean {
  if (!auth.tenantId) return false;
  if (typeof auth.tenantModules?.[moduleKey] === "boolean") {
    return auth.tenantModules[moduleKey] === true;
  }
  const membership = auth.activeTenantMemberships.find((item) => item.tenantId === auth.tenantId);
  return Boolean(membership?.modules?.[moduleKey]);
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
