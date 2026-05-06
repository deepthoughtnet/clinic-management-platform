export type NavItem = {
  key: string;
  label?: string;
  path?: string;
  section?: string;
};

export const NAV: NavItem[] = [
  { key: "core", section: "Clinic" },
  { key: "dashboard", label: "Dashboard", path: "/" },
  { key: "patients", label: "Patients", path: "/patients" },
  { key: "appointments", label: "Appointments", path: "/appointments" },
  { key: "queue", label: "Queue", path: "/queue" },
  { key: "consultations", label: "Consultations", path: "/consultations" },
  { key: "prescriptions", label: "Prescriptions", path: "/prescriptions" },
  { key: "billing", label: "Billing", path: "/billing" },
  { key: "notifications", label: "Notifications", path: "/notifications" },
  { key: "vaccinations", label: "Vaccinations", path: "/vaccinations" },
  { key: "inventory", label: "Inventory", path: "/inventory" },
  { key: "reports", label: "Reports", path: "/reports" },
  { key: "settings-section", section: "Settings" },
  { key: "settings-profile", label: "Clinic Profile", path: "/settings/clinic-profile" },
  { key: "settings-users-roles", label: "Users & Roles", path: "/settings/users-roles" },
];
