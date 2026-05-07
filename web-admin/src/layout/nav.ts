export type NavItem = {
  key: string;
  label?: string;
  path?: string;
  section?: string;
  platformOnly?: boolean;
  requiresTenant?: boolean;
  rolesAny?: string[];
};

export const NAV: NavItem[] = [
  { key: "platform", section: "Platform", platformOnly: true },
  { key: "platform-dashboard", label: "Platform Dashboard", path: "/", platformOnly: true },
  { key: "platform-tenants", label: "Tenants", path: "/platform/tenants", platformOnly: true },
  { key: "platform-plans", label: "Plans / Modules", path: "/platform/plans", platformOnly: true },
  { key: "platform-users", label: "Users / Admins", path: "/platform/users", platformOnly: true },
  { key: "core", section: "Clinic" },
  { key: "dashboard", label: "Dashboard", path: "/", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "BILLING_USER", "AUDITOR", "LAB_ASSISTANT", "PHARMACIST"] },
  { key: "patients", label: "Patients", path: "/patients", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"] },
  { key: "appointments", label: "Appointments", path: "/appointments", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST"] },
  { key: "queue", label: "My Queue", path: "/queue", requiresTenant: true, rolesAny: ["DOCTOR"] },
  { key: "queue-ops", label: "Queue", path: "/queue", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST"] },
  { key: "consultations", label: "Consultations", path: "/consultations", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "AUDITOR"] },
  { key: "prescriptions", label: "Prescriptions", path: "/prescriptions", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "AUDITOR", "PHARMACIST"] },
  { key: "billing", label: "Billing", path: "/billing", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "BILLING_USER", "AUDITOR"] },
  { key: "notifications", label: "Notifications", path: "/notifications", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR"] },
  { key: "vaccinations", label: "Vaccinations", path: "/vaccinations", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "RECEPTIONIST", "AUDITOR"] },
  { key: "inventory", label: "Inventory", path: "/inventory", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMACIST"] },
  { key: "reports", label: "Reports", path: "/reports", requiresTenant: true, rolesAny: ["CLINIC_ADMIN", "BILLING_USER", "AUDITOR"] },
  { key: "settings-section", section: "Settings" },
  { key: "settings-profile", label: "Clinic Profile", path: "/settings/clinic-profile", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
  { key: "settings-users-roles", label: "Users & Roles", path: "/settings/users-roles", requiresTenant: true, rolesAny: ["CLINIC_ADMIN"] },
];
