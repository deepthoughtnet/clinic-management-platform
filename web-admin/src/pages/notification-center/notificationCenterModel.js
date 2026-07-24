import { getNotificationActionPresentation as resolveNotificationActionPresentation } from "./notificationActionRegistry.runtime.js";

const DEFAULT_TIME_ZONE = "UTC";

export const NOTIFICATION_CENTER_TAB_OPTIONS = [
  { key: "all", label: "All" },
  { key: "unread", label: "Unread" },
  { key: "requires-action", label: "Requires Action" },
];

export const NOTIFICATION_CENTER_CATEGORY_OPTIONS = [
  { value: "", label: "All categories" },
  { value: "APPOINTMENT", label: "Appointments" },
  { value: "CLINICAL", label: "Clinical" },
  { value: "LAB", label: "Laboratory" },
  { value: "BILLING", label: "Billing" },
  { value: "PHARMACY", label: "Pharmacy" },
  { value: "SYSTEM", label: "System" },
  { value: "PLATFORM", label: "Platform" },
  { value: "ENGAGE", label: "Engage" },
  { value: "AI", label: "AI" },
];

export const NOTIFICATION_CENTER_PRIORITY_OPTIONS = [
  { value: "", label: "All priorities" },
  { value: "LOW", label: "Low" },
  { value: "NORMAL", label: "Normal" },
  { value: "HIGH", label: "High" },
  { value: "CRITICAL", label: "Critical" },
];

export const NOTIFICATION_CENTER_QUERY_KEYS = {
  root(tenantId, userId) {
    return ["notification-center", tenantId || "", userId || ""];
  },
  unreadCount(tenantId, userId) {
    return ["notification-center", "unread-count", tenantId || "", userId || ""];
  },
  preview(tenantId, userId) {
    return ["notification-center", "preview", tenantId || "", userId || ""];
  },
  summary(tenantId, userId) {
    return ["notification-center", "summary", tenantId || "", userId || ""];
  },
  list(tenantId, userId, filters) {
    return ["notification-center", "list", tenantId || "", userId || "", JSON.stringify(filters || {})];
  },
  detail(tenantId, userId, notificationId) {
    return ["notification-center", "detail", tenantId || "", userId || "", notificationId || ""];
  },
};

export const NOTIFICATION_CENTER_REFRESH_EVENT = "notification-center:refresh";

const CATEGORY_PRESENTATIONS = {
  APPOINTMENT: { label: "Appointments", iconKey: "calendar", tone: "primary" },
  CLINICAL: { label: "Clinical", iconKey: "clinical", tone: "secondary" },
  LAB: { label: "Laboratory", iconKey: "lab", tone: "secondary" },
  PHARMACY: { label: "Pharmacy", iconKey: "pharmacy", tone: "success" },
  BILLING: { label: "Billing", iconKey: "billing", tone: "warning" },
  SYSTEM: { label: "System", iconKey: "system", tone: "error" },
  PLATFORM: { label: "Platform", iconKey: "system", tone: "error" },
  ENGAGE: { label: "Engage", iconKey: "engage", tone: "info" },
  AI: { label: "AI", iconKey: "ai", tone: "info" },
  OTHER: { label: "Other", iconKey: "other", tone: "default" },
};

const PRIORITY_PRESENTATIONS = {
  LOW: { label: "Low", iconKey: "low", tone: "default" },
  NORMAL: { label: "Normal", iconKey: "normal", tone: "default" },
  HIGH: { label: "High", iconKey: "high", tone: "warning" },
  CRITICAL: { label: "Critical", iconKey: "critical", tone: "error" },
};

function normalizeTimeZone(timeZone) {
  if (!timeZone || !String(timeZone).trim()) {
    return DEFAULT_TIME_ZONE;
  }
  const trimmed = String(timeZone).trim();
  try {
    new Intl.DateTimeFormat("en-GB", { timeZone: trimmed }).format(new Date());
    return trimmed;
  } catch {
    return DEFAULT_TIME_ZONE;
  }
}

function formatDateParts(date, timeZone) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(date);

  const valueFor = (type) => parts.find((part) => part.type === type)?.value || "";
  return {
    year: valueFor("year"),
    month: valueFor("month"),
    day: valueFor("day"),
    hour: valueFor("hour"),
    minute: valueFor("minute"),
  };
}

function parseOffsetMinutes(timeZone, date) {
  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone,
      timeZoneName: "shortOffset",
    }).formatToParts(date);
    const rawOffset = parts.find((part) => part.type === "timeZoneName")?.value || "GMT";
    const normalized = rawOffset.replace(/^GMT/i, "UTC");
    const match = normalized.match(/^UTC([+-])(\d{1,2})(?::(\d{2}))?$/);
    if (!match) {
      return 0;
    }
    const sign = match[1] === "-" ? -1 : 1;
    const hours = Number(match[2] || "0");
    const minutes = Number(match[3] || "0");
    return sign * ((hours * 60) + minutes);
  } catch {
    return 0;
  }
}

function normalizeEnumValue(value) {
  return String(value || "").trim().toUpperCase().replace(/-/g, "_");
}

function normalizeDateInput(value) {
  const text = String(value || "").trim();
  return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : "";
}

function parseDateInputToIsoOffsetDateTime(value, timeZone, boundary = "start") {
  const input = normalizeDateInput(value);
  if (!input) {
    return null;
  }
  const match = input.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const zone = normalizeTimeZone(timeZone);
  const utcGuess = boundary === "end"
    ? Date.UTC(year, month - 1, day, 23, 59, 59, 999)
    : Date.UTC(year, month - 1, day, 0, 0, 0, 0);
  let candidate = utcGuess;
  for (let index = 0; index < 3; index += 1) {
    const offset = parseOffsetMinutes(zone, new Date(candidate));
    candidate = utcGuess - (offset * 60_000);
    const candidateParts = formatDateParts(new Date(candidate), zone);
    const hour = boundary === "end" ? (candidateParts.hour || "23") : "00";
    const minute = boundary === "end" ? (candidateParts.minute || "59") : "00";
    if (
      candidateParts.year === match[1]
      && candidateParts.month === match[2]
      && candidateParts.day === match[3]
      && (boundary === "end" ? Number(hour) >= 0 : true)
      && (boundary === "start" ? Number(hour) >= 0 : true)
      && minute !== ""
    ) {
      break;
    }
  }
  return new Date(candidate).toISOString();
}

function formatMonthDay(date, timeZone, includeYear = false) {
  const options = {
    month: "short",
    day: "numeric",
    timeZone,
  };
  if (includeYear) {
    options.year = "numeric";
  }
  return new Intl.DateTimeFormat("en-US", options).format(date).replace(/\s+/g, " ").trim();
}

export function formatNotificationDateKey(value, timeZone) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const zone = normalizeTimeZone(timeZone);
  const parts = formatDateParts(date, zone);
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function sameCalendarDate(left, right, timeZone) {
  const leftParts = formatDateParts(left, timeZone);
  const rightParts = formatDateParts(right, timeZone);
  return leftParts.year === rightParts.year && leftParts.month === rightParts.month && leftParts.day === rightParts.day;
}

function dayDifference(left, right, timeZone) {
  const leftParts = formatDateParts(left, timeZone);
  const rightParts = formatDateParts(right, timeZone);
  const leftKey = Date.UTC(Number(leftParts.year), Number(leftParts.month) - 1, Number(leftParts.day));
  const rightKey = Date.UTC(Number(rightParts.year), Number(rightParts.month) - 1, Number(rightParts.day));
  return Math.round((rightKey - leftKey) / 86_400_000);
}

export function normalizeNotificationCategory(value) {
  const normalized = normalizeEnumValue(value);
  return CATEGORY_PRESENTATIONS[normalized] ? normalized : "OTHER";
}

export function normalizeNotificationPriority(value) {
  const normalized = normalizeEnumValue(value);
  return PRIORITY_PRESENTATIONS[normalized] ? normalized : "NORMAL";
}

export function getNotificationCategoryPresentation(value) {
  const normalized = normalizeNotificationCategory(value);
  return CATEGORY_PRESENTATIONS[normalized] || CATEGORY_PRESENTATIONS.OTHER;
}

export function getNotificationPriorityPresentation(value) {
  const normalized = normalizeNotificationPriority(value);
  return PRIORITY_PRESENTATIONS[normalized] || PRIORITY_PRESENTATIONS.NORMAL;
}

export function getNotificationActionPresentation(actionRoute, actionLabel, actionTargetId) {
  return resolveNotificationActionPresentation(actionRoute, actionLabel, actionTargetId);
}

function normalizeNotificationCount(value) {
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? Math.trunc(number) : 0;
}

function normalizeNotificationItems(items) {
  return Array.isArray(items) ? items.filter(Boolean) : [];
}

export function normalizeNotificationSummary(summary) {
  if (!summary) {
    return {
      unreadCount: 0,
      requiresActionCount: 0,
      criticalCount: 0,
      todayCount: 0,
    };
  }
  return {
    unreadCount: normalizeNotificationCount(summary.unreadCount),
    requiresActionCount: normalizeNotificationCount(summary.requiresActionCount),
    criticalCount: normalizeNotificationCount(summary.criticalCount),
    todayCount: normalizeNotificationCount(summary.todayCount),
  };
}

export function normalizeNotificationPreview(preview) {
  return {
    items: normalizeNotificationItems(preview?.items),
  };
}

export function normalizeNotificationPage(page) {
  return {
    items: normalizeNotificationItems(page?.items),
    page: normalizeNotificationCount(page?.page),
    size: Math.max(1, normalizeNotificationCount(page?.size) || 20),
    totalElements: normalizeNotificationCount(page?.totalElements),
    totalPages: normalizeNotificationCount(page?.totalPages),
  };
}

export function formatNotificationExactTimestamp(value, timeZone) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const zone = normalizeTimeZone(timeZone);
  const formatted = new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
    timeZone: zone,
  }).format(date);
  return formatted.replace(/\b(am|pm)\b/gi, (match) => match.toUpperCase());
}

export function formatNotificationRelativeTime(value, timeZone, nowValue) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const zone = normalizeTimeZone(timeZone);
  const now = nowValue ? new Date(nowValue) : new Date();
  if (Number.isNaN(now.getTime())) {
    return "";
  }

  const diffMs = now.getTime() - date.getTime();
  const absoluteMs = Math.abs(diffMs);
  if (absoluteMs < 60_000) {
    return "Now";
  }
  if (absoluteMs < 3_600_000) {
    return `${Math.max(1, Math.floor(absoluteMs / 60_000))} min ago`;
  }
  if (absoluteMs < 86_400_000) {
    return `${Math.max(1, Math.floor(absoluteMs / 3_600_000))} hr ago`;
  }

  const dayDiff = dayDifference(date, now, zone);
  if (dayDiff === 1) {
    return "Yesterday";
  }
  if (dayDiff > 1 && dayDiff < 7) {
    return `${dayDiff} days ago`;
  }
  const sameYear = formatDateParts(date, zone).year === formatDateParts(now, zone).year;
  return formatMonthDay(date, zone, !sameYear);
}

export function buildNotificationCenterInboxQuery(filters, timeZone) {
  return {
    readState: filters?.tab === "unread" ? "UNREAD" : filters?.tab === "requires-action" ? "ALL" : "ALL",
    category: filters?.category || null,
    priority: filters?.priority || null,
    requiresAction: filters?.tab === "requires-action" ? true : null,
    search: filters?.search?.trim() || null,
    from: parseDateInputToIsoOffsetDateTime(filters?.from, timeZone, "start"),
    to: parseDateInputToIsoOffsetDateTime(filters?.to, timeZone, "end"),
    page: Math.max(0, Number(filters?.page ?? 0)),
    size: Math.min(100, Math.max(1, Number(filters?.size ?? 20))),
  };
}

export function parseNotificationCenterRouteState(searchParams) {
  const readString = (key) => String(searchParams?.get(key) || "").trim();
  const tab = readString("tab");
  const normalizedTab = NOTIFICATION_CENTER_TAB_OPTIONS.some((option) => option.key === tab) ? tab : "all";
  return {
    tab: normalizedTab,
    search: searchParams?.get("search") || "",
    category: searchParams?.get("category") || "",
    priority: searchParams?.get("priority") || "",
    from: normalizeDateInput(searchParams?.get("from")),
    to: normalizeDateInput(searchParams?.get("to")),
    page: Math.max(0, Number(searchParams?.get("page") || 0)),
    size: Math.min(100, Math.max(1, Number(searchParams?.get("size") || 20))),
    notificationId: searchParams?.get("notificationId") || "",
  };
}

export function buildNotificationCenterSearchParams(state) {
  const params = new URLSearchParams();
  if (state?.tab && state.tab !== "all") params.set("tab", state.tab);
  if (state?.search) params.set("search", state.search);
  if (state?.category) params.set("category", state.category);
  if (state?.priority) params.set("priority", state.priority);
  if (state?.from) params.set("from", state.from);
  if (state?.to) params.set("to", state.to);
  if (typeof state?.page === "number" && state.page > 0) params.set("page", String(state.page));
  if (typeof state?.size === "number" && state.size !== 20) params.set("size", String(state.size));
  if (state?.notificationId) params.set("notificationId", state.notificationId);
  return params;
}

export function formatNotificationCenterPageTitle() {
  return "My Notifications";
}
