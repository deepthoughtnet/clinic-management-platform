const DEFAULT_TIME_ZONE = "UTC";

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

export function formatCarePilotDateTimeInput(value, timeZone) {
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

export function parseCarePilotDateTimeInput(value, timeZone) {
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
