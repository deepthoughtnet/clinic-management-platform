export const TENANT_BRANDING_UPDATED_EVENT = "tenant-branding:updated";

export function notifyTenantBrandingUpdated() {
  window.dispatchEvent(new Event(TENANT_BRANDING_UPDATED_EVENT));
}
