import { matchPath } from "react-router-dom";

export type HelpPageRoute = {
  path: string;
  pageKey: string;
  cmsPageKey: string;
  title: string;
};

export const HELP_PAGE_ROUTES: HelpPageRoute[] = [
  { path: "/patients/new", pageKey: "PATIENT_MASTER", cmsPageKey: "PATIENTS", title: "Patients" },
  { path: "/patients/:id/edit", pageKey: "PATIENT_DETAILS", cmsPageKey: "PATIENT_DETAILS", title: "Patient Details" },
  { path: "/patients/:id", pageKey: "PATIENT_DETAILS", cmsPageKey: "PATIENT_DETAILS", title: "Patient Details" },
  { path: "/patients", pageKey: "PATIENT_MASTER", cmsPageKey: "PATIENTS", title: "Patients" },
  { path: "/dashboard", pageKey: "CLINIC_DASHBOARD", cmsPageKey: "CLINIC_DASHBOARD", title: "Clinic Dashboard" },
  { path: "/doctors/availability", pageKey: "DOCTOR_AVAILABILITY", cmsPageKey: "DOCTOR_AVAILABILITY", title: "Doctor Availability" },
  { path: "/doctors/:id", pageKey: "DOCTORS", cmsPageKey: "DOCTORS", title: "Doctors" },
  { path: "/appointments/day-board", pageKey: "DAY_BOARD", cmsPageKey: "DAY_BOARD", title: "Day Board" },
  { path: "/appointments", pageKey: "APPOINTMENTS", cmsPageKey: "APPOINTMENTS", title: "Appointments" },
  { path: "/queue", pageKey: "QUEUE", cmsPageKey: "QUEUE", title: "Queue" },
  { path: "/consultations/:id", pageKey: "CONSULTATION_WORKSPACE", cmsPageKey: "CONSULTATION_WORKSPACE", title: "Consultation Workspace" },
  { path: "/consultations", pageKey: "CONSULTATION_WORKSPACE", cmsPageKey: "CONSULTATION_WORKSPACE", title: "Consultation Workspace" },
  { path: "/billing", pageKey: "BILLING", cmsPageKey: "BILLING", title: "Billing" },
  { path: "/finance/payments", pageKey: "PAYMENTS", cmsPageKey: "PAYMENTS", title: "Payments" },
  { path: "/finance/refunds", pageKey: "REFUNDS", cmsPageKey: "REFUNDS", title: "Refunds" },
  { path: "/pharmacy/dashboard", pageKey: "PHARMACY_DASHBOARD", cmsPageKey: "PHARMACY_DASHBOARD", title: "Pharmacy Dashboard" },
  { path: "/notifications", pageKey: "NOTIFICATIONS", cmsPageKey: "NOTIFICATIONS", title: "Notifications & Reminders" },
  { path: "/vaccinations", pageKey: "VACCINATIONS", cmsPageKey: "VACCINATIONS", title: "Vaccinations" },
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
  { path: "/laboratory", pageKey: "LABORATORY", cmsPageKey: "LABORATORY", title: "Laboratory" },
  { path: "/lab", pageKey: "LABORATORY", cmsPageKey: "LABORATORY", title: "Laboratory" },
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

export function resolveHelpPageMeta(pathname: string): HelpPageRoute {
  const [cleanPath, search = ""] = pathname.split("?", 2);
  const query = new URLSearchParams(search);
  for (const route of HELP_PAGE_ROUTES) {
    if (!matchPath({ path: route.path, end: true }, cleanPath)) {
      continue;
    }
    if (route.path.startsWith("/consultations/")) {
      const tab = query.get("tab")?.trim().toLowerCase();
      if (tab === "prescription") return { ...route, pageKey: "CONSULTATION_PRESCRIPTION", cmsPageKey: "CONSULTATION_PRESCRIPTION", title: "Prescription" };
      if (tab === "history") return { ...route, pageKey: "CONSULTATION_HISTORY", cmsPageKey: "CONSULTATION_HISTORY", title: "History" };
      if (tab === "investigations") return { ...route, pageKey: "CONSULTATION_INVESTIGATIONS", cmsPageKey: "CONSULTATION_INVESTIGATIONS", title: "Investigations" };
      if (tab === "lab-orders" || tab === "lab_orders") return { ...route, pageKey: "CONSULTATION_LAB_ORDERS", cmsPageKey: "CONSULTATION_LAB_ORDERS", title: "Lab Orders" };
      if (tab === "ai-assist" || tab === "ai_assist") return { ...route, pageKey: "CONSULTATION_AI_ASSIST", cmsPageKey: "CONSULTATION_AI_ASSIST", title: "AI Assist" };
      return route;
    }
    return route;
  }
  return {
    path: cleanPath,
    pageKey: "UNKNOWN_PAGE",
    cmsPageKey: "UNKNOWN_PAGE",
    title: "Help",
  };
}

export function resolveHelpRouteByPageKey(pageKey: string): HelpPageRoute | null {
  const normalized = pageKey.trim().toUpperCase();
  if (normalized === "PHARMACY") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/pharmacy/dashboard") || null;
  }
  if (normalized === "CLINIC_DASHBOARD") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/dashboard") || null;
  }
  if (normalized === "DAY_BOARD") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/appointments/day-board") || null;
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
  if (normalized === "NOTIFICATIONS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/notifications") || null;
  }
  if (normalized === "VACCINATIONS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/vaccinations") || null;
  }
  if (normalized === "CLINIC_DASHBOARD") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/dashboard") || null;
  }
  if (normalized === "LAB" || normalized === "LAB_OPERATIONS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/laboratory") || null;
  }
  if (normalized === "CONSULTATION" || normalized === "CONSULTATION_WORKSPACE") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/consultations/:id") || null;
  }
  if (normalized === "CONSULTATION_PRESCRIPTION") {
    return { path: "/consultations/:id?tab=prescription", pageKey: "CONSULTATION_PRESCRIPTION", cmsPageKey: "CONSULTATION_PRESCRIPTION", title: "Prescription" };
  }
  if (normalized === "CONSULTATION_HISTORY") {
    return { path: "/consultations/:id?tab=history", pageKey: "CONSULTATION_HISTORY", cmsPageKey: "CONSULTATION_HISTORY", title: "History" };
  }
  if (normalized === "CONSULTATION_INVESTIGATIONS") {
    return { path: "/consultations/:id?tab=investigations", pageKey: "CONSULTATION_INVESTIGATIONS", cmsPageKey: "CONSULTATION_INVESTIGATIONS", title: "Investigations" };
  }
  if (normalized === "CONSULTATION_LAB_ORDERS") {
    return { path: "/consultations/:id?tab=lab-orders", pageKey: "CONSULTATION_LAB_ORDERS", cmsPageKey: "CONSULTATION_LAB_ORDERS", title: "Lab Orders" };
  }
  if (normalized === "CONSULTATION_AI_ASSIST") {
    return { path: "/consultations/:id?tab=ai-assist", pageKey: "CONSULTATION_AI_ASSIST", cmsPageKey: "CONSULTATION_AI_ASSIST", title: "AI Assist" };
  }
  return HELP_PAGE_ROUTES.find((route) => route.pageKey.toUpperCase() === normalized || route.cmsPageKey.toUpperCase() === normalized) || null;
}
