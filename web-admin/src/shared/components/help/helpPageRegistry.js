import { matchPath } from "react-router-dom";

export const HELP_PAGE_ROUTES = [
  { path: "/patients/new", pageKey: "PATIENT_MASTER", cmsPageKey: "PATIENTS", title: "Patients" },
  { path: "/patients/:id/edit", pageKey: "PATIENT_DETAILS", cmsPageKey: "PATIENT_DETAILS", title: "Patient Details" },
  { path: "/patients/:id", pageKey: "PATIENT_DETAILS", cmsPageKey: "PATIENT_DETAILS", title: "Patient Details" },
  { path: "/patients", pageKey: "PATIENT_MASTER", cmsPageKey: "PATIENTS", title: "Patients" },
  { path: "/doctors/availability", pageKey: "DOCTOR_AVAILABILITY", cmsPageKey: "DOCTOR_AVAILABILITY", title: "Doctor Availability" },
  { path: "/doctors/:id", pageKey: "DOCTORS", cmsPageKey: "DOCTORS", title: "Doctors" },
  { path: "/appointments/day-board", pageKey: "QUEUE", cmsPageKey: "QUEUE", title: "Queue" },
  { path: "/appointments", pageKey: "APPOINTMENTS", cmsPageKey: "APPOINTMENTS", title: "Appointments" },
  { path: "/queue", pageKey: "QUEUE", cmsPageKey: "QUEUE", title: "Queue" },
  { path: "/consultations/:id", pageKey: "CONSULTATION", cmsPageKey: "CONSULTATION", title: "Consultation" },
  { path: "/consultations", pageKey: "CONSULTATION", cmsPageKey: "CONSULTATION", title: "Consultation" },
  { path: "/billing", pageKey: "BILLING", cmsPageKey: "BILLING", title: "Billing" },
  { path: "/finance/payments", pageKey: "PAYMENTS", cmsPageKey: "PAYMENTS", title: "Payments" },
  { path: "/finance/refunds", pageKey: "REFUNDS", cmsPageKey: "REFUNDS", title: "Refunds" },
  { path: "/pharmacy/dashboard", pageKey: "PHARMACY_DASHBOARD", cmsPageKey: "PHARMACY_DASHBOARD", title: "Pharmacy Dashboard" },
  { path: "/inventory", pageKey: "PHARMACY_INVENTORY", cmsPageKey: "PHARMACY_INVENTORY", title: "Inventory" },
  { path: "/pharmacy/inventory", pageKey: "PHARMACY_INVENTORY", cmsPageKey: "PHARMACY_INVENTORY", title: "Inventory" },
  { path: "/pharmacy/medicine-master", pageKey: "PHARMACY_MEDICINE_MASTER", cmsPageKey: "MEDICINE_MASTER", title: "Medicine Master" },
  { path: "/pharmacy/medicines", pageKey: "PHARMACY_MEDICINE_MASTER", cmsPageKey: "MEDICINE_MASTER", title: "Medicine Master" },
  { path: "/pharmacy/stock-movements", pageKey: "STOCK_MOVEMENTS", cmsPageKey: "STOCK_MOVEMENTS", title: "Stock Movements" },
  { path: "/pharmacy/dispensing", pageKey: "PHARMACY_DISPENSING", cmsPageKey: "DISPENSING", title: "Dispensing" },
  { path: "/dispensing", pageKey: "PHARMACY_DISPENSING", cmsPageKey: "DISPENSING", title: "Dispensing" },
  { path: "/pharmacy/pos", pageKey: "PHARMACY_POS", cmsPageKey: "PHARMACY_POS", title: "Pharmacy POS" },
  { path: "/pharmacy/operations", pageKey: "PHARMACY_OPERATIONS", cmsPageKey: "PHARMACY_OPERATIONS", title: "Pharmacy Operations" },
  { path: "/reports", pageKey: "REPORTS", cmsPageKey: "REPORTS", title: "Reports" },
  { path: "/lab", pageKey: "LAB", cmsPageKey: "LAB", title: "Lab" },
  { path: "/platform/tenants", pageKey: "TENANT_MANAGEMENT", cmsPageKey: "TENANT_MANAGEMENT", title: "Tenant Management" },
  { path: "/platform/users", pageKey: "USERS", cmsPageKey: "USERS", title: "Users / Admins" },
  { path: "/platform/help", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/settings/clinic-profile", pageKey: "CLINIC_PROFILE", cmsPageKey: "CLINIC_PROFILE", title: "Clinic Profile" },
  { path: "/settings/users-roles", pageKey: "USERS", cmsPageKey: "USERS", title: "Users / Admins" },
  { path: "/carepilot/campaigns", pageKey: "CAMPAIGNS", cmsPageKey: "CAMPAIGNS", title: "Campaigns" },
  { path: "/carepilot/leads", pageKey: "LEADS", cmsPageKey: "LEADS", title: "Leads" },
  { path: "/admin/templates", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/notification-settings", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/integrations", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/ai-ops", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/platform-ops", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/realtime-ai", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
];

export function resolveHelpPageMeta(pathname) {
  for (const route of HELP_PAGE_ROUTES) {
    if (matchPath({ path: route.path, end: true }, pathname)) {
      return route;
    }
  }
  return { path: pathname, pageKey: "UNKNOWN_PAGE", cmsPageKey: "UNKNOWN_PAGE", title: "Help" };
}

export function resolveHelpRouteByPageKey(pageKey) {
  const normalized = String(pageKey || "").trim().toUpperCase();
  if (normalized === "PHARMACY") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/pharmacy/dashboard") || null;
  }
  if (normalized === "FINANCE_BILLING" || normalized === "BILL_BUILDER") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/billing") || null;
  }
  if (normalized === "FINANCE_REPORTS" || normalized === "TENANT_REPORTS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/reports") || null;
  }
  if (normalized === "POS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/pharmacy/pos") || null;
  }
  return HELP_PAGE_ROUTES.find((route) => route.pageKey.toUpperCase() === normalized || route.cmsPageKey.toUpperCase() === normalized) || null;
}
