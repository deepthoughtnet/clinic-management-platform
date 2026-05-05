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
  { key: "consultations", label: "Consultations", path: "/consultations" },
  { key: "prescriptions", label: "Prescriptions", path: "/prescriptions" },
  { key: "billing", label: "Billing", path: "/billing" },
  { key: "vaccinations", label: "Vaccinations", path: "/vaccinations" },
  { key: "inventory", label: "Inventory", path: "/inventory" },
  { key: "reports", label: "Reports", path: "/reports" },
  { key: "settings", label: "Settings", path: "/settings" },
];
