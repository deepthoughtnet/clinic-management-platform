import { matchPath } from "react-router-dom";

export const HELP_PAGE_ROUTES = [
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
  { path: "/pharmacy/procurement", pageKey: "PHARMACY_PROCUREMENT", cmsPageKey: "PHARMACY_PROCUREMENT", title: "Procurement" },
  { path: "/pharmacy/reconciliation", pageKey: "PHARMACY_RECONCILIATION", cmsPageKey: "PHARMACY_RECONCILIATION", title: "Reconciliation" },
  { path: "/pharmacy/operations", pageKey: "PHARMACY_OPERATIONS", cmsPageKey: "PHARMACY_OPERATIONS", title: "Procurement" },
  { path: "/reports", pageKey: "REPORTS", cmsPageKey: "REPORTS", title: "Reports" },
  { path: "/laboratory", pageKey: "LABORATORY", cmsPageKey: "LABORATORY", title: "Laboratory" },
  { path: "/lab", pageKey: "LABORATORY", cmsPageKey: "LABORATORY", title: "Laboratory" },
  { path: "/platform/tenants", pageKey: "TENANT_MANAGEMENT", cmsPageKey: "TENANT_MANAGEMENT", title: "Tenant Management" },
  { path: "/platform/users", pageKey: "USERS", cmsPageKey: "USERS", title: "Users / Admins" },
  { path: "/platform/help", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/settings/clinic-profile", pageKey: "CLINIC_PROFILE", cmsPageKey: "CLINIC_PROFILE", title: "Clinic Profile" },
  { path: "/settings/users-roles", pageKey: "USERS", cmsPageKey: "USERS", title: "Users / Admins" },
  { path: "/carepilot/campaigns", pageKey: "JEEVANAM_ENGAGE_CAMPAIGNS", cmsPageKey: "CAMPAIGNS", title: "Jeevanam Engage Campaigns" },
  { path: "/carepilot/leads", pageKey: "ENGAGE_LEADS", cmsPageKey: "ENGAGE_LEADS", title: "Jeevanam Engage Leads" },
  { path: "/carepilot/analytics", pageKey: "ENGAGE_ANALYTICS", cmsPageKey: "ENGAGE_ANALYTICS", title: "Jeevanam Engage Analytics" },
  { path: "/carepilot/ops", pageKey: "ENGAGE_OPS_CONSOLE", cmsPageKey: "ENGAGE_OPS_CONSOLE", title: "Jeevanam Engage Ops Console" },
  { path: "/carepilot/messaging", pageKey: "ENGAGE_MESSAGING", cmsPageKey: "ENGAGE_MESSAGING", title: "Jeevanam Engage Messaging" },
  { path: "/carepilot/reminders", pageKey: "ENGAGE_REMINDERS", cmsPageKey: "ENGAGE_REMINDERS", title: "Jeevanam Engage Reminders" },
  { path: "/carepilot/engagement", pageKey: "ENGAGE_PATIENT_ENGAGEMENT", cmsPageKey: "ENGAGE_PATIENT_ENGAGEMENT", title: "Jeevanam Engage Patient Engagement" },
  { path: "/carepilot/webinars", pageKey: "ENGAGE_WEBINAR_AUTOMATION", cmsPageKey: "ENGAGE_WEBINAR_AUTOMATION", title: "Jeevanam Engage Webinar Automation" },
  { path: "/carepilot/ai-calls", pageKey: "ENGAGE_AI_CALLS", cmsPageKey: "ENGAGE_AI_CALLS", title: "Jeevanam Engage AI Calls" },
  { path: "/carepilot/ai-receptionist/active-conversations", pageKey: "ENGAGE_AI_RECEPTIONIST_ACTIVE", cmsPageKey: "ENGAGE_AI_RECEPTIONIST_ACTIVE", title: "Jeevanam Engage AI Receptionist Active Conversations" },
  { path: "/carepilot/ai-receptionist/callback-queue", pageKey: "ENGAGE_AI_RECEPTIONIST_CALLBACK", cmsPageKey: "ENGAGE_AI_RECEPTIONIST_CALLBACK", title: "Jeevanam Engage AI Receptionist Callback Queue" },
  { path: "/carepilot/ai-receptionist/escalation-queue", pageKey: "ENGAGE_AI_RECEPTIONIST_ESCALATION", cmsPageKey: "ENGAGE_AI_RECEPTIONIST_ESCALATION", title: "Jeevanam Engage AI Receptionist Escalation Queue" },
  { path: "/carepilot/ai-receptionist/appointment-handoffs", pageKey: "ENGAGE_AI_RECEPTIONIST_APPOINTMENT_HANDOFF", cmsPageKey: "ENGAGE_AI_RECEPTIONIST_APPOINTMENT_HANDOFF", title: "Jeevanam Engage AI Receptionist Appointment Handoff" },
  { path: "/carepilot/receptionist-queue", pageKey: "ENGAGE_RECEPTIONIST_QUEUE", cmsPageKey: "ENGAGE_RECEPTIONIST_QUEUE", title: "Jeevanam Engage Receptionist Queue" },
  { path: "/admin/templates", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/notification-settings", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/integrations", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/ai-ops", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/platform-ops", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
  { path: "/admin/realtime-ai", pageKey: "PLATFORM_ADMIN", cmsPageKey: "PLATFORM_ADMIN", title: "Platform Admin" },
];

export function resolveHelpPageMeta(pathname) {
  const [cleanPath, search = ""] = String(pathname || "").split("?", 2);
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
    }
    return route;
  }
  return { path: cleanPath, pageKey: "UNKNOWN_PAGE", cmsPageKey: "UNKNOWN_PAGE", title: "Help" };
}

export function resolveHelpRouteByPageKey(pageKey) {
  const normalized = String(pageKey || "").trim().toUpperCase();
  if (normalized === "CAMPAIGNS" || normalized === "JEEVANAM_ENGAGE_CAMPAIGNS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/campaigns") || null;
  }
  if (normalized === "LEADS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/leads") || null;
  }
  if (normalized === "ANALYTICS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/analytics") || null;
  }
  if (normalized === "OPS_CONSOLE" || normalized === "OPS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/ops") || null;
  }
  if (normalized === "MESSAGING") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/messaging") || null;
  }
  if (normalized === "REMINDERS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/reminders") || null;
  }
  if (normalized === "PATIENT_ENGAGEMENT" || normalized === "ENGAGEMENT") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/engagement") || null;
  }
  if (normalized === "WEBINAR_AUTOMATION" || normalized === "WEBINARS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/webinars") || null;
  }
  if (normalized === "AI_CALLS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/ai-calls") || null;
  }
  if (normalized === "AI_RECEPTIONIST_ACTIVE") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/ai-receptionist/active-conversations") || null;
  }
  if (normalized === "AI_RECEPTIONIST_CALLBACK") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/ai-receptionist/callback-queue") || null;
  }
  if (normalized === "AI_RECEPTIONIST_ESCALATION") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/ai-receptionist/escalation-queue") || null;
  }
  if (normalized === "AI_RECEPTIONIST_APPOINTMENT_HANDOFF") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/ai-receptionist/appointment-handoffs") || null;
  }
  if (normalized === "RECEPTIONIST_QUEUE") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/carepilot/receptionist-queue") || null;
  }
  if (normalized === "PHARMACY") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/pharmacy/dashboard") || null;
  }
  if (normalized === "CLINIC_DASHBOARD") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/dashboard") || null;
  }
  if (normalized === "DAY_BOARD") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/appointments/day-board") || null;
  }
  if (normalized === "NOTIFICATIONS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/notifications") || null;
  }
  if (normalized === "VACCINATIONS") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/vaccinations") || null;
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
  if (normalized === "PHARMACY_PROCUREMENT" || normalized === "PROCUREMENT") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/pharmacy/procurement") || null;
  }
  if (normalized === "PHARMACY_RECONCILIATION" || normalized === "RECONCILIATION") {
    return HELP_PAGE_ROUTES.find((route) => route.path === "/pharmacy/reconciliation") || null;
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
