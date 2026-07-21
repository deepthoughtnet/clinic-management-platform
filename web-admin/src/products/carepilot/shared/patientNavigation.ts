import type { AuthContextValue } from "../../../auth/AuthContext";
import { hasTenantModule } from "../../../auth/moduleEntitlements";
import { isRouteAccessibleForAuth } from "../../../modules/moduleRegistry";

export function canOpenLinkedPatient(
  auth: Pick<AuthContextValue, "tenantId" | "tenantModules" | "enabledTenantModules" | "activeTenantMemberships" | "permissions" | "hasPermission" | "rolesUpper" | "tenantRole">,
) {
  if (!auth.tenantId) {
    return false;
  }
  if (!auth.hasPermission("patient.read")) {
    return false;
  }
  if (!hasTenantModule(auth, "APPOINTMENTS") && !hasTenantModule(auth, "CONSULTATION")) {
    return false;
  }
  return isRouteAccessibleForAuth(auth, "/patients/:id");
}

export function canViewLinkedPatientConsultationHistory(
  auth: Pick<AuthContextValue, "tenantId" | "tenantModules" | "enabledTenantModules" | "activeTenantMemberships" | "permissions" | "hasPermission" | "rolesUpper" | "tenantRole">,
) {
  if (!canOpenLinkedPatient(auth)) {
    return false;
  }
  if (!auth.hasPermission("consultation.read")) {
    return false;
  }
  return true;
}
