import { branding } from "../branding";
import {
  ENGAGE_ANALYTICS_VIEW,
  ENGAGE_LEAD_VIEW,
  ENGAGE_LEAD_VIEW_ALL,
  ENGAGE_LEAD_VIEW_AUDIT,
  ENGAGE_WEBINAR_VIEW,
  ENGAGE_WEBINAR_VIEW_ANALYTICS,
  ENGAGE_WEBINAR_VIEW_AUDIT,
} from "../auth/permissions";
import type { TenantModuleCode } from "../modules/moduleRegistry";

export type NavItem = {
  key: string;
  label: string;
  path?: string;
  platformOnly?: boolean;
  requiresTenant?: boolean;
  rolesAny?: string[];
  permissionsAny?: string[];
  badge?: string;
  disabled?: boolean;
  future?: boolean;
  moduleAny?: TenantModuleCode[];
  moduleAll?: TenantModuleCode[];
};

export type NavGroup = {
  key: string;
  label: string;
  platformOnly?: boolean;
  requiresTenant?: boolean;
  rolesAny?: string[];
  defaultExpanded?: boolean;
  items: NavItem[];
  moduleAny?: TenantModuleCode[];
  moduleAll?: TenantModuleCode[];
};

export const NAV_GROUPS: NavGroup[] = [
  {
    key: "operations",
    label: "Operations",
    requiresTenant: true,
    defaultExpanded: true,
    items: [
      { key: "dashboard", label: "Dashboard", path: "/dashboard", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "BILLING_USER", "AUDITOR", "LAB_FRONT_DESK", "LAB_TECHNICIAN", "LAB_ASSISTANT", "LAB_APPROVER"], moduleAny: ["APPOINTMENTS", "CONSULTATION"] },
      { key: "day-board", label: "Day Board", path: "/appointments/day-board", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST"], moduleAny: ["APPOINTMENTS"] },
      { key: "appointments", label: "Appointments", path: "/appointments", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST"], moduleAny: ["APPOINTMENTS"] },
      { key: "notifications", label: "Notifications", path: "/notifications", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "TENANT_ADMIN", "RECEPTIONIST", "AUDITOR", "PLATFORM_ADMIN"], moduleAny: ["APPOINTMENTS"] },
      { key: "queue", label: "My Queue", path: "/queue", requiresTenant: true, rolesAny: ["DOCTOR"], moduleAny: ["APPOINTMENTS"] },
      { key: "queue-ops", label: "Queue", path: "/queue", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST"], moduleAny: ["APPOINTMENTS"] },
      { key: "doctor-availability", label: "Doctor Availability", path: "/doctors/availability", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "AUDITOR"], moduleAny: ["APPOINTMENTS"] },
    ],
  },
  {
    key: "clinical",
    label: "Clinical",
    requiresTenant: true,
    defaultExpanded: true,
    items: [
      { key: "patients", label: "Patients", path: "/patients", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"], moduleAny: ["APPOINTMENTS", "CONSULTATION"] },
      { key: "consultations", label: "Consultations", path: "/consultations", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "AUDITOR"], moduleAny: ["CONSULTATION"] },
      { key: "vaccinations", label: "Vaccinations", path: "/vaccinations", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "AUDITOR"], moduleAny: ["VACCINATION"] },
      { key: "laboratory", label: "Laboratory", path: "/lab", requiresTenant: true, rolesAny: ["PLATFORM_ADMIN", "CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "BILLING_USER", "AUDITOR", "LAB_FRONT_DESK", "LAB_TECHNICIAN", "LAB_ASSISTANT", "LAB_APPROVER"], moduleAny: ["LABORATORY"] },
    ],
  },
  {
    key: "pharmacy",
    label: "Pharmacy",
    requiresTenant: true,
    items: [
      { key: "pharmacy-dashboard", label: "Pharmacy Dashboard", path: "/pharmacy/dashboard", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST", "PHARMACY_INVENTORY_MANAGER", "PHARMACY_POS_USER"], moduleAny: ["INVENTORY", "PRESCRIPTION", "BILLING"] },
      { key: "prescription-register", label: "Prescription Register", path: "/prescriptions", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST"], moduleAny: ["PRESCRIPTION"] },
      { key: "dispense-queue", label: "Dispense Queue", path: "/pharmacy/dispensing", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "PHARMA", "PHARMACY", "PHARMACIST", "BILLING_USER", "AUDITOR", "PHARMACY_INVENTORY_MANAGER", "PHARMACY_POS_USER"], moduleAll: ["PRESCRIPTION", "INVENTORY"] },
      { key: "pharmacy-pos", label: "POS Sale", path: "/pharmacy/pos", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "PHARMA", "PHARMACY", "PHARMACIST", "AUDITOR", "PHARMACY_POS_USER", "PHARMACY_INVENTORY_MANAGER"], moduleAny: ["PHARMACY_POS"] },
      { key: "pharmacy-procure", label: "Procure", path: "/pharmacy/procure", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMACY_INVENTORY_MANAGER", "PHARMACIST"], moduleAny: ["INVENTORY"] },
      { key: "pharmacy-reconcile", label: "Reconcile", path: "/pharmacy/reconcile", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMACY_INVENTORY_MANAGER", "PHARMACIST"], moduleAny: ["INVENTORY"] },
      { key: "medicine-master", label: "Medicine Master", path: "/pharmacy/medicines", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST", "PHARMACY_INVENTORY_MANAGER", "PHARMACY_POS_USER"], moduleAny: ["INVENTORY"] },
      { key: "inventory", label: "Inventory", path: "/inventory", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST", "PHARMACY_INVENTORY_MANAGER", "PHARMACY_POS_USER"], moduleAny: ["INVENTORY"] },
      { key: "reports-audit", label: "Reports & Audit", path: "/pharmacy/stock-movements", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST", "BILLING_USER", "PHARMACY_INVENTORY_MANAGER", "PHARMACY_POS_USER"], moduleAny: ["INVENTORY"] },
    ],
  },
  {
    key: "finance",
    label: "Finance",
    requiresTenant: true,
    items: [
      { key: "billing", label: "Billing", path: "/billing", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"], moduleAny: ["BILLING"] },
      { key: "cash-counter", label: "Cash Counter", path: "/finance/cash-counter", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "BILLING_USER", "AUDITOR", "PLATFORM_ADMIN"], moduleAny: ["BILLING"] },
      { key: "payments", label: "Payments", path: "/finance/payments", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"], moduleAny: ["BILLING"] },
      { key: "refunds", label: "Refunds", path: "/finance/refunds", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"], moduleAny: ["BILLING"] },
      { key: "reports", label: "Reports", path: "/reports", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "BILLING_USER", "AUDITOR", "PLATFORM_ADMIN"], moduleAny: ["REPORTS"] },
    ],
  },
  {
    key: "carepilot",
    label: "ENGAGE",
    requiresTenant: true,
    defaultExpanded: false,
    rolesAny: ["CLINIC_ADMIN", "ENGAGE_MANAGER", "ENGAGE_EXECUTIVE", "RECEPTIONIST", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"],
    items: [
      { key: "campaigns", label: "Campaigns", path: "/carepilot/campaigns", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "ENGAGE_MANAGER", "AUDITOR"], moduleAny: ["CAREPILOT"] },
      { key: "analytics", label: "Analytics", path: "/carepilot/analytics", requiresTenant: true, permissionsAny: [ENGAGE_ANALYTICS_VIEW], moduleAny: ["CAREPILOT"] },
      { key: "ops-console", label: "Ops Console", path: "/carepilot/ops", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "ENGAGE_MANAGER", "ENGAGE_EXECUTIVE", "AUDITOR"], moduleAny: ["CAREPILOT"] },
      { key: "messaging", label: "Messaging", path: "/carepilot/messaging", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR"], moduleAny: ["CAREPILOT"] },
      { key: "reminders", label: "Reminders", path: "/carepilot/reminders", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "ENGAGE_MANAGER", "ENGAGE_EXECUTIVE", "AUDITOR"], moduleAny: ["CAREPILOT"] },
      { key: "patient-engagement", label: "Patient Engagement", path: "/carepilot/engagement", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "ENGAGE_MANAGER", "AUDITOR"], moduleAny: ["CAREPILOT"] },
      { key: "leads", label: "Leads", path: "/carepilot/leads", requiresTenant: true, permissionsAny: [ENGAGE_LEAD_VIEW, ENGAGE_LEAD_VIEW_ALL, ENGAGE_LEAD_VIEW_AUDIT], moduleAny: ["CAREPILOT"] },
      { key: "webinar-automation", label: "Webinar Automation", path: "/carepilot/webinars", requiresTenant: true, permissionsAny: [ENGAGE_WEBINAR_VIEW, ENGAGE_WEBINAR_VIEW_ANALYTICS, ENGAGE_WEBINAR_VIEW_AUDIT], moduleAny: ["CAREPILOT"] },
      { key: "ai-operations", label: "AI Operations", path: "/carepilot/ai-operations", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "ENGAGE_MANAGER", "ENGAGE_EXECUTIVE", "RECEPTIONIST", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"], moduleAny: ["CAREPILOT"] },
    ],
  },
  {
    key: "administration",
    label: "Administration",
    requiresTenant: true,
    items: [
      { key: "settings-profile", label: "Clinic Profile", path: "/settings/clinic-profile", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
      { key: "settings-users-roles", label: "Users & Roles", path: "/settings/users-roles", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "TENANT_ADMIN", "ADMIN", "PLATFORM_ADMIN"] },
      { key: "templates", label: "Templates", path: "/admin/templates", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"] },
      { key: "notification-settings", label: "Notification Settings", path: "/admin/notification-settings", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"] },
      { key: "integrations", label: "Integrations", path: "/admin/integrations", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"] },
      { key: "ai-ops", label: "AI Ops", path: "/admin/ai-ops", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"], moduleAny: ["AI_COPILOT"] },
      { key: "ai-reasoning-console", label: "AI Reasoning Console", path: "/admin/ai-reasoning-console", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "TENANT_ADMIN", "ADMIN", "PLATFORM_ADMIN"] },
      { key: "platform-ops", label: "Platform Ops", path: "/admin/platform-ops", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"] },
      { key: "realtime-ai", label: "Realtime AI", path: "/admin/realtime-ai", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"], moduleAny: ["AI_COPILOT"] },
      { key: "voice-test", label: "Voice Test", path: "/ai/voice-test", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "TENANT_ADMIN", "RECEPTIONIST", "PLATFORM_ADMIN"] },
    ],
  },
  {
    key: "platform",
    label: "Platform",
    platformOnly: true,
    defaultExpanded: false,
    items: [
      { key: "platform-dashboard", label: "Platform Dashboard", path: "/", platformOnly: true },
      { key: "platform-tenants", label: "Tenants", path: "/platform/tenants", platformOnly: true },
      { key: "platform-product-implementation", label: "Product Implementation", path: "/platform/product-implementation", platformOnly: true },
      { key: "platform-subscriptions", label: "Subscriptions", disabled: true, future: true, badge: "Coming soon", platformOnly: true },
      { key: "platform-analytics", label: "Platform Analytics", disabled: true, future: true, badge: "Coming soon", platformOnly: true },
      { key: "platform-health", label: "System Health", disabled: true, future: true, badge: "Coming soon", platformOnly: true },
      { key: "platform-plans", label: "Plans / Modules", path: "/platform/plans", platformOnly: true },
      { key: "platform-users", label: "Users / Admins", path: "/platform/users", platformOnly: true },
      { key: "platform-help", label: "Help CMS", path: "/platform/help", platformOnly: true },
    ],
  },
];
