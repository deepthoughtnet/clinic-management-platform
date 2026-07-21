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

function formatDateParts(date: Date, timeZone: string) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(date);

  const valueFor = (type: string) => parts.find((part) => part.type === type)?.value || "";
  return {
    year: valueFor("year"),
    month: valueFor("month"),
    day: valueFor("day"),
    hour: valueFor("hour"),
    minute: valueFor("minute"),
  };
}

function parseOffsetMinutes(timeZone: string, date: Date) {
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

function formatOffsetLabel(timeZone: string, date: Date) {
  try {
    const offset = parseOffsetMinutes(timeZone, date);
    const sign = offset < 0 ? "-" : "+";
    const total = Math.abs(offset);
    const paddedHour = String(Math.floor(total / 60)).padStart(2, "0");
    const paddedMinute = String(total % 60).padStart(2, "0");
    const normalized = `UTC${sign}${paddedHour}:${paddedMinute}`;
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

export function formatCarePilotDateTimeInput(value: string | null | undefined, timeZone?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const zone = normalizeTimeZone(timeZone);
  const parts = formatDateParts(date, zone);
  if (!parts.year || !parts.month || !parts.day || !parts.hour || !parts.minute) {
    return "";
  }
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

export function parseCarePilotDateTimeInput(value: string | null | undefined, timeZone?: string | null) {
  const input = typeof value === "string" ? value.trim() : "";
  if (!input) return null;
  const match = input.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const zone = normalizeTimeZone(timeZone);
  const utcGuess = Date.UTC(year, month - 1, day, hour, minute);
  let candidate = utcGuess;
  for (let i = 0; i < 3; i += 1) {
    const offset = parseOffsetMinutes(zone, new Date(candidate));
    candidate = utcGuess - (offset * 60_000);
    const candidateParts = formatDateParts(new Date(candidate), zone);
    if (
      candidateParts.year === match[1]
      && candidateParts.month === match[2]
      && candidateParts.day === match[3]
      && candidateParts.hour === match[4]
      && candidateParts.minute === match[5]
    ) {
      break;
    }
  }
  return new Date(candidate).toISOString();
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
