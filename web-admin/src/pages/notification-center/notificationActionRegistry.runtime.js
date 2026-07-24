const ACTION_REGISTRY = {
  "/dashboard": { label: "Open dashboard", route: "/dashboard" },
  "/appointments/day-board": { label: "Open appointment", route: "/appointments/day-board" },
  "/appointments": { label: "Open appointments", route: "/appointments" },
  "/queue": { label: "Open queue", route: "/queue" },
  "/consultations": { label: "Open consultations", route: "/consultations" },
  "/billing": { label: "Open billing", route: "/billing" },
  "/finance/payments": { label: "Open payments", route: "/finance/payments" },
  "/finance/refunds": { label: "Open refunds", route: "/finance/refunds" },
  "/lab": { label: "Open laboratory", route: "/lab" },
  "/prescriptions": { label: "Open prescriptions", route: "/prescriptions" },
  "/vaccinations": { label: "Open vaccinations", route: "/vaccinations" },
  "/patients": { label: "Open patient", route: "/patients" },
  "/notification-center": { label: "Open notification", route: "/notification-center" },
  OPEN_APPOINTMENT: { label: "Open appointment", route: "/appointments/day-board" },
  OPEN_CONSULTATION: { label: "Open consultations", route: "/consultations" },
  OPEN_PATIENT: { label: "Open patient", route: "/patients" },
  OPEN_QUEUE: { label: "Open queue", route: "/queue" },
  OPEN_LAB_RESULT: { label: "Open laboratory", route: "/lab" },
  OPEN_PRESCRIPTION: { label: "Open prescriptions", route: "/prescriptions" },
  OPEN_PAYMENT: { label: "Open payments", route: "/finance/payments" },
  OPEN_BILL: { label: "Open billing", route: "/billing" },
  OPEN_NOTIFICATION_DETAIL: { label: "Open notification", route: "/notification-center" },
};

export function normalizeNotificationActionRoute(routeKey) {
  const raw = String(routeKey || "").trim();
  if (!raw) {
    return "";
  }
  if (raw.startsWith("/")) {
    return raw.split("?")[0].replace(/\/+$/, "") || "/";
  }
  return raw.toUpperCase();
}

function resolveRoute(entry, actionTargetId) {
  if (!entry) {
    return "";
  }
  if (entry.route === "/notification-center" && actionTargetId) {
    return `/notification-center?notificationId=${encodeURIComponent(actionTargetId)}`;
  }
  if (entry.route === "/patients" && actionTargetId) {
    return `/patients/${encodeURIComponent(actionTargetId)}`;
  }
  return entry.route;
}

export function getNotificationActionPresentation(actionRoute, actionLabel, actionTargetId) {
  const normalizedRoute = normalizeNotificationActionRoute(actionRoute);
  if (!normalizedRoute) {
    return null;
  }
  const entry = ACTION_REGISTRY[normalizedRoute] || ACTION_REGISTRY[normalizedRoute.toUpperCase()];
  if (!entry) {
    return null;
  }
  return {
    label: actionLabel && String(actionLabel).trim() ? String(actionLabel).trim() : entry.label,
    route: resolveRoute(entry, actionTargetId || null),
    targetId: actionTargetId || null,
  };
}

export const NOTIFICATION_ACTION_REGISTRY = ACTION_REGISTRY;
