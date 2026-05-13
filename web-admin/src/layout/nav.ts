export type NavItem = {
  key: string;
  label: string;
  path?: string;
  platformOnly?: boolean;
  requiresTenant?: boolean;
  rolesAny?: string[];
  badge?: string;
  disabled?: boolean;
  future?: boolean;
};

export type NavGroup = {
  key: string;
  label: string;
  platformOnly?: boolean;
  requiresTenant?: boolean;
  rolesAny?: string[];
  defaultExpanded?: boolean;
  items: NavItem[];
};

export const NAV_GROUPS: NavGroup[] = [
  {
    key: "operations",
    label: "Operations",
    requiresTenant: true,
    defaultExpanded: true,
    items: [
      { key: "dashboard", label: "Dashboard", path: "/dashboard", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "BILLING_USER", "AUDITOR", "LAB_ASSISTANT", "PHARMA", "PHARMACY", "PHARMACIST"] },
      { key: "day-board", label: "Day Board", path: "/appointments/day-board", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST"] },
      { key: "appointments", label: "Appointments", path: "/appointments", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST"] },
      { key: "queue", label: "My Queue", path: "/queue", requiresTenant: true, rolesAny: ["DOCTOR"] },
      { key: "queue-ops", label: "Queue", path: "/queue", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST"] },
      { key: "doctor-availability", label: "Doctor Availability", path: "/doctors/availability", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "AUDITOR"] },
    ],
  },
  {
    key: "clinical",
    label: "Clinical",
    requiresTenant: true,
    defaultExpanded: true,
    items: [
      { key: "patients", label: "Patients", path: "/patients", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"] },
      { key: "consultations", label: "Consultations", path: "/consultations", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "AUDITOR"] },
      { key: "prescriptions", label: "Prescriptions", path: "/prescriptions", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST"] },
      { key: "vaccinations", label: "Vaccinations", path: "/vaccinations", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "AUDITOR"] },
    ],
  },
  {
    key: "pharmacy",
    label: "Pharmacy",
    requiresTenant: true,
    items: [
      { key: "inventory", label: "Inventory", path: "/inventory", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST"] },
      { key: "dispensing", label: "Dispensing", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "PHARMA", "PHARMACY", "PHARMACIST", "RECEPTIONIST"] },
      { key: "stock-movements", label: "Stock Movements", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST"] },
      { key: "medicine-master", label: "Medicine Master", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "PHARMA", "PHARMACY", "PHARMACIST"] },
    ],
  },
  {
    key: "finance",
    label: "Finance",
    requiresTenant: true,
    items: [
      { key: "billing", label: "Billing", path: "/billing", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"] },
      { key: "payments", label: "Payments", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"] },
      { key: "refunds", label: "Refunds", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"] },
      { key: "reports", label: "Reports", path: "/reports", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "BILLING_USER", "AUDITOR"] },
    ],
  },
  {
    key: "carepilot",
    label: "CarePilot",
    requiresTenant: true,
    defaultExpanded: false,
    rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"],
    items: [
      { key: "campaigns", label: "Campaigns", path: "/carepilot/campaigns", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR"] },
      { key: "analytics", label: "Analytics", path: "/carepilot/analytics", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR"] },
      { key: "ops-console", label: "Ops Console", path: "/carepilot/ops", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR"] },
      { key: "messaging", label: "Messaging", path: "/carepilot/messaging", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"] },
      { key: "reminders", label: "Reminders", path: "/carepilot/reminders", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR"] },
      { key: "patient-engagement", label: "Patient Engagement", path: "/carepilot/engagement", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR"] },
      { key: "leads", label: "Leads", path: "/carepilot/leads", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "AUDITOR"] },
      { key: "webinar-automation", label: "Webinar Automation", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
      { key: "ai-calls", label: "AI Calls", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
    ],
  },
  {
    key: "administration",
    label: "Administration",
    requiresTenant: true,
    items: [
      { key: "settings-profile", label: "Clinic Profile", path: "/settings/clinic-profile", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
      { key: "settings-users-roles", label: "Users & Roles", path: "/settings/users-roles", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
      { key: "templates", label: "Templates", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
      { key: "notification-settings", label: "Notification Settings", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
      { key: "integrations", label: "Integrations", disabled: true, future: true, badge: "Coming soon", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
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
      { key: "platform-subscriptions", label: "Subscriptions", disabled: true, future: true, badge: "Coming soon", platformOnly: true },
      { key: "platform-analytics", label: "Platform Analytics", disabled: true, future: true, badge: "Coming soon", platformOnly: true },
      { key: "platform-health", label: "System Health", disabled: true, future: true, badge: "Coming soon", platformOnly: true },
      { key: "platform-plans", label: "Plans / Modules", path: "/platform/plans", platformOnly: true },
      { key: "platform-users", label: "Users / Admins", path: "/platform/users", platformOnly: true },
    ],
  },
];
