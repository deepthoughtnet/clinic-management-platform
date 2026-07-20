const DEFAULT_TIME_ZONE = "UTC";

const PROVIDER_LABELS: Record<string, string> = {
  "carepilot-email-smtp": "Email SMTP",
  "carepilot email smtp": "Email SMTP",
  "carepilot_email_smtp": "Email SMTP",
  "email smtp": "Email SMTP",
  "carepilot-email": "Email",
  "carepilot_email": "Email",
  "carepilot sms": "SMS",
};

function normalizeTimeZone(timeZone?: string | null) {
  if (!timeZone || !timeZone.trim()) {
    return DEFAULT_TIME_ZONE;
  }
  const trimmed = timeZone.trim();
  try {
    new Intl.DateTimeFormat("en-GB", { timeZone: trimmed }).format(new Date());
    return trimmed;
  } catch {
    return DEFAULT_TIME_ZONE;
  }
}

function formatOffsetLabel(timeZone: string, date: Date) {
  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone,
      timeZoneName: "shortOffset",
    }).formatToParts(date);
    const rawOffset = parts.find((part) => part.type === "timeZoneName")?.value || "GMT";
    const normalized = rawOffset.replace(/^GMT/i, "UTC");
    return normalized === "UTC" ? "UTC+00:00" : normalized.replace(/^UTC([+-])(\d{1,2})(?::(\d{2}))?$/, (_match, sign, hour, minute) => {
      const paddedHour = String(hour).padStart(2, "0");
      const paddedMinute = String(minute || "00").padStart(2, "0");
      return `UTC${sign}${paddedHour}:${paddedMinute}`;
    });
  } catch {
    return "UTC+00:00";
  }
}

function formatZoneLabel(timeZone: string, date: Date) {
  if (timeZone === "Asia/Kolkata") {
    return `IST (${formatOffsetLabel(timeZone, date)})`;
  }

  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone,
      timeZoneName: "short",
    }).formatToParts(date);
    const shortName = parts.find((part) => part.type === "timeZoneName")?.value?.trim() || timeZone;
    if (/^(GMT|UTC)/i.test(shortName)) {
      return `${timeZone} (${formatOffsetLabel(timeZone, date)})`;
    }
    return `${shortName} (${formatOffsetLabel(timeZone, date)})`;
  } catch {
    return `${timeZone} (${formatOffsetLabel(timeZone, date)})`;
  }
}

export function formatCarePilotDateTime(value: string | null | undefined, timeZone?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
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
  return `${formatted} ${formatZoneLabel(zone, date)}`.replace(/\s+/g, " ").trim();
}

export function formatCarePilotDateOnly(value: string | null | undefined, timeZone?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const zone = normalizeTimeZone(timeZone);
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: zone,
  }).format(date);
}

export function formatCarePilotDurationMinutes(value: number | null | undefined) {
  if (value == null || value < 0) return "—";
  return `${value}m`;
}

export function providerLabel(value: string | null | undefined) {
  if (!value) return "-";
  const normalized = value.trim().toLowerCase().replace(/[-_\s]+/g, " ");
  if (PROVIDER_LABELS[normalized]) {
    return PROVIDER_LABELS[normalized];
  }
  return value.replace(/[-_]/g, " ").toLowerCase().replace(/(^|\s)\w/g, (m) => m.toUpperCase());
}

export function getCarePilotTenantTimeZone(timeZone?: string | null) {
  return normalizeTimeZone(timeZone);
}

export function humanizeCarePilotCode(value: string | null | undefined) {
  if (!value) return "-";
  return value
    .replace(/[-_]/g, " ")
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase());
}

export function formatCarePilotAssigneeLabel(
  user: { displayName?: string | null; username?: string | null } | null | undefined,
  assignedToAppUserId?: string | null
) {
  if (!assignedToAppUserId) {
    return "Unassigned";
  }
  if (!user) {
    return "Unavailable user";
  }
  const displayName = user.displayName?.trim();
  if (displayName) {
    return displayName;
  }
  const username = user.username?.trim();
  if (username) {
    return username;
  }
  return "Unavailable user";
}
