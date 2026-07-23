import type {
  NotificationOperationsChannelRow,
  NotificationOperationsDeliveryRow,
  NotificationOperationsPeriod,
  NotificationOperationsQuery,
  NotificationOperationsSummaryResponse,
} from "../../api/clinicApi";

export type NotificationOperationsTab =
  | "overview"
  | "deliveries"
  | "failures"
  | "providers"
  | "analytics"
  | "audit";

export const NOTIFICATION_OPERATION_TABS: Array<{ key: NotificationOperationsTab; label: string }> = [
  { key: "overview", label: "Overview" },
  { key: "deliveries", label: "Deliveries" },
  { key: "failures", label: "Failures & Retries" },
  { key: "providers", label: "Providers" },
  { key: "analytics", label: "Analytics" },
  { key: "audit", label: "Audit" },
];

export const NOTIFICATION_OPERATION_PERIODS: Array<{ key: NotificationOperationsPeriod; label: string }> = [
  { key: "TODAY", label: "Today" },
  { key: "LAST_7_DAYS", label: "Last 7 days" },
  { key: "LAST_30_DAYS", label: "Last 30 days" },
  { key: "CUSTOM", label: "Custom range" },
];

export const CHANNEL_ORDER = ["IN_APP", "EMAIL", "SMS", "WHATSAPP"] as const;

export type ChannelTone = "success" | "warning" | "error" | "neutral";

const SMS_NOTIFICATIONS_DISABLED = "SMS notifications disabled";
const WHATSAPP_NOTIFICATIONS_DISABLED = "WhatsApp notifications disabled";

export function channelLabel(channel: string | null | undefined) {
  switch (normalizeChannel(channel)) {
    case "IN_APP":
      return "In-App";
    case "EMAIL":
      return "Email";
    case "SMS":
      return "SMS";
    case "WHATSAPP":
      return "WhatsApp";
    default:
      return "Unknown";
  }
}

export function normalizeChannel(channel: string | null | undefined) {
  if (!channel) {
    return "UNKNOWN";
  }
  const value = channel.trim().toUpperCase();
  if (value === "IN-APP" || value === "IN_APP") return "IN_APP";
  if (value === "EMAIL") return "EMAIL";
  if (value === "SMS") return "SMS";
  if (value === "WHATSAPP") return "WHATSAPP";
  return value;
}

export function normalizeNotificationReason(channel: string, reason: string | null | undefined) {
  if (!reason) {
    return null;
  }
  const compact = reason.replace(/\s+/g, " ").trim();
  const lower = compact.toLowerCase();
  if (lower.includes("clinic.carepilot.messaging.sms.enabled=false") || lower.includes("clinic.carepilot.messaging.whatsapp.enabled=false")) {
    const normalizedChannel = normalizeChannel(channel);
    if (normalizedChannel === "SMS") {
      return SMS_NOTIFICATIONS_DISABLED;
    }
    if (normalizedChannel === "WHATSAPP") {
      return WHATSAPP_NOTIFICATIONS_DISABLED;
    }
    return `${channelLabel(channel)} notifications disabled`;
  }
  if (lower.includes("patient record unavailable") || lower.includes("no active recipient available")) {
    return "Patient record unavailable";
  }
  if (lower.includes("patient email unavailable")) {
    return "Patient email unavailable";
  }
  if (lower.includes("invalid patient email")) {
    return "Invalid patient email";
  }
  if (lower.includes("patient mobile unavailable")) {
    return "Patient mobile unavailable";
  }
  if (lower.includes("invalid patient mobile")) {
    return "Invalid patient mobile";
  }
  if (lower.includes("email provider not configured")) {
    return "Email provider not configured";
  }
  if (lower.includes("sms provider not configured")) {
    return "SMS provider not configured";
  }
  if (lower.includes("whatsapp provider not configured")) {
    return "WhatsApp provider not configured";
  }
  if (lower.includes("patient opted out")) {
    return "Patient opted out";
  }
  if (lower.startsWith("skipped:")) {
    return normalizeNotificationReason(channel, compact.slice(compact.indexOf(":") + 1).trim());
  }
  if (lower.startsWith("provider disabled:")) {
    return normalizeNotificationReason(channel, compact.slice(compact.indexOf(":") + 1).trim());
  }
  if (lower.includes("notifications disabled")) {
    const normalizedChannel = normalizeChannel(channel);
    if (normalizedChannel === "SMS") {
      return SMS_NOTIFICATIONS_DISABLED;
    }
    if (normalizedChannel === "WHATSAPP") {
      return WHATSAPP_NOTIFICATIONS_DISABLED;
    }
    return `${channelLabel(channel)} notifications disabled`;
  }
  if (lower.includes("provider not configured") || lower.includes("provider unavailable")) {
    return `${channelLabel(channel)} provider not configured`;
  }
  return compact;
}

export function channelDisplayStatus(channel: string, delivery?: NotificationOperationsChannelRow | null) {
  if (!delivery) {
    return { statusLabel: "Not enabled", tone: "neutral" as ChannelTone, title: "Not enabled", reason: null as string | null };
  }

  const normalizedReason = normalizeNotificationReason(channel, delivery.failureReason);
  const status = delivery.status.toUpperCase();
  if (status === "SENT" || status === "DELIVERED") {
    return { statusLabel: "Sent", tone: "success" as ChannelTone, title: normalizedReason ? `Sent — ${normalizedReason}` : "Sent", reason: normalizedReason };
  }
  if (status === "PENDING" || status === "QUEUED" || status === "RETRYING") {
    return { statusLabel: "Pending", tone: "warning" as ChannelTone, title: normalizedReason ? `Pending — ${normalizedReason}` : "Pending", reason: normalizedReason };
  }
  if (status === "FAILED") {
    return { statusLabel: "Failed", tone: "error" as ChannelTone, title: normalizedReason ? `Failed — ${normalizedReason}` : "Failed", reason: normalizedReason };
  }
  if (status === "SKIPPED" && normalizedReason?.toLowerCase().includes("notifications disabled")) {
    return { statusLabel: "Disabled", tone: "neutral" as ChannelTone, title: `Disabled — ${normalizedReason}`, reason: normalizedReason };
  }
  if (status === "SKIPPED") {
    return { statusLabel: "Skipped", tone: "neutral" as ChannelTone, title: normalizedReason ? `Skipped — ${normalizedReason}` : "Skipped", reason: normalizedReason };
  }
  return { statusLabel: status.charAt(0) + status.slice(1).toLowerCase(), tone: "neutral" as ChannelTone, title: normalizedReason ? `${status.charAt(0) + status.slice(1).toLowerCase()} — ${normalizedReason}` : status, reason: normalizedReason };
}

export function channelBadgeLabel(channel: string, delivery?: NotificationOperationsChannelRow | null) {
  const presentation = channelDisplayStatus(channel, delivery);
  return `${channelLabel(channel)} ${presentation.statusLabel}`;
}

export function channelBadgeTitle(channel: string, delivery?: NotificationOperationsChannelRow | null) {
  return channelDisplayStatus(channel, delivery).title;
}

export function providerStatusLabel(status: string | null | undefined) {
  const value = String(status || "").trim().toLowerCase();
  if (!value) return "Unknown";
  if (value.includes("healthy") || value.includes("ready")) return "Healthy";
  if (value.includes("degraded")) return "Degraded";
  if (value.includes("disabled")) return "Disabled";
  if (value.includes("not configured") || value.includes("missing")) return "Not Configured";
  if (value.includes("unknown")) return "Unknown";
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export function providerConfigurationLabel(row: { enabled?: boolean; configured?: boolean; configurationStatus?: string; readinessStatus?: string }) {
  if (!row.configured) return "Not Configured";
  if (!row.enabled) return "Disabled";
  if (String(row.configurationStatus || row.readinessStatus || "").toLowerCase().includes("ready")) return "Configuration Ready";
  return "Configuration Ready";
}

export function humanizeAuditActor(value: string | null | undefined) {
  if (!value) return "System";
  const compact = value.trim();
  const normalized = compact.toLowerCase();
  if (/^[0-9a-f-]{32,36}$/i.test(compact)) return "Background Worker";
  if (normalized.includes("notification scheduler")) return "Notification Scheduler";
  if (normalized.includes("reminder scheduler")) return "Reminder Scheduler";
  if (normalized.includes("notification engine")) return "Notification Engine";
  if (normalized.includes("background worker")) return "Background Worker";
  if (normalized.includes("system")) return "System";
  if (normalized.includes("aiva")) return "AIVA";
  if (normalized.includes("platform admin")) return "Platform Admin";
  if (normalized.includes("clinic admin")) return "Clinic Admin";
  return compact
    .replace(/[._-]+/g, " ")
    .replace(/\s+/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

export function humanizeAuditAction(value: string | null | undefined) {
  if (!value) return "-";
  return value
    .trim()
    .replace(/[._-]+/g, " ")
    .replace(/\s+/g, " ")
    .split(" ")
    .filter(Boolean)
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(" ");
}

export function overallStatusLabel(status: NotificationOperationsDeliveryRow["overallStatus"] | NotificationOperationsSummaryResponse["scheduler"] | string) {
  switch (String(status)) {
    case "DELIVERED":
      return "Delivered";
    case "PARTIAL":
      return "Partial";
    case "PENDING":
      return "Pending";
    case "FAILED":
      return "Failed";
    case "NOT_DELIVERED":
      return "Not delivered";
    default:
      return String(status);
  }
}

export function overallStatusColor(status: NotificationOperationsDeliveryRow["overallStatus"] | string) {
  switch (String(status)) {
    case "DELIVERED":
      return "success";
    case "PARTIAL":
      return "warning";
    case "PENDING":
      return "info";
    case "FAILED":
      return "error";
    case "NOT_DELIVERED":
      return "default";
    default:
      return "default";
  }
}

export function eventLabel(eventType: string) {
  if (!eventType) {
    return "-";
  }
  return eventType
    .toLowerCase()
    .split("_")
    .filter(Boolean)
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(" ");
}

export function categoryLabel(eventType: string) {
  if (eventType.startsWith("APPOINTMENT")) return "Appointments";
  if (eventType.startsWith("BILL") || eventType.startsWith("PAYMENT") || eventType.startsWith("RECEIPT") || eventType.startsWith("REFUND")) return "Billing";
  if (eventType.startsWith("PRESCRIPTION")) return "Clinical";
  if (eventType.startsWith("LAB")) return "Laboratory";
  if (eventType.startsWith("FOLLOW_UP")) return "Clinical";
  if (eventType.startsWith("VACCINATION")) return "Vaccination";
  return "System";
}

export function channelRowOrder(rows: NotificationOperationsChannelRow[]) {
  return [...rows].sort((left, right) => CHANNEL_ORDER.indexOf(normalizeChannel(left.channel) as typeof CHANNEL_ORDER[number]) - CHANNEL_ORDER.indexOf(normalizeChannel(right.channel) as typeof CHANNEL_ORDER[number]));
}

export function channelSummaryLabel(row: NotificationOperationsDeliveryRow) {
  return channelRowOrder(row.deliveries)
    .map((entry) => `${channelLabel(entry.channel)}: ${channelDisplayStatus(entry.channel, entry).statusLabel}`)
    .join(" · ");
}

export function formatTimestamp(value: string | null | undefined) {
  if (!value) {
    return "-";
  }
  return new Date(value).toLocaleString();
}

export function formatDateOnly(value: string | null | undefined) {
  if (!value) {
    return "-";
  }
  return new Date(value).toLocaleDateString();
}

export function formatRatio(positive: number, total: number) {
  if (!total) {
    return "0.0%";
  }
  return `${((positive / total) * 100).toFixed(1)}%`;
}

export function compactMetricTone(value: number | null | undefined, kind: "success-rate" | "healthy-providers" | "failures" | "pending") {
  switch (kind) {
    case "success-rate":
      return (value ?? 0) >= 95 ? "success" : (value ?? 0) >= 80 ? "warning" : "error";
    case "healthy-providers":
      return (value ?? 0) > 0 ? "success" : "warning";
    case "failures":
      return (value ?? 0) > 0 ? "error" : "success";
    case "pending":
      return (value ?? 0) > 0 ? "warning" : "success";
    default:
      return "neutral";
  }
}
