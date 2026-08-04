export type PublicAddressParts = {
  addressLine1?: string | null;
  addressLine2?: string | null;
  address?: string | null;
  area?: string | null;
  city?: string | null;
  state?: string | null;
  country?: string | null;
  postalCode?: string | null;
  pinCode?: string | null;
};

export type PublicAddressView = {
  lines: string[];
  compact: string;
  singleLine: string;
  localityLabel: string | null;
};

export type PublicWeeklyInterval = {
  day?: string | null;
  dayOfWeek?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  start?: string | null;
  end?: string | null;
  open?: string | null;
  close?: string | null;
  closed?: boolean | null;
  label?: string | null;
};

export type WeeklyTimingRow = {
  dayOfWeek: string;
  day: string;
  sessions: string[];
  hours: string;
  closed: boolean;
};

export type PublicWeeklyTimingsView = {
  timezone: string | null;
  rows: WeeklyTimingRow[];
  warnings: string[];
  skippedCount: number;
};

const WEEKDAY_ORDER = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"] as const;

function cleanText(value: unknown): string {
  return typeof value === "string" ? value.replace(/\s+/g, " ").trim() : "";
}

function joinComma(parts: Array<string | null | undefined>) {
  return parts.map((part) => cleanText(part)).filter(Boolean).join(", ");
}

function joinSpace(parts: Array<string | null | undefined>) {
  return parts.map((part) => cleanText(part)).filter(Boolean).join(" ");
}

function normalizeDay(value: unknown) {
  const day = cleanText(value).toLowerCase();
  return WEEKDAY_ORDER.find((item) => item === day) ?? null;
}

function appendUnique(list: string[], value: string | null | undefined) {
  const text = cleanText(value);
  if (!text) {
    return;
  }
  if (list.some((item) => item.toLowerCase() === text.toLowerCase())) {
    return;
  }
  list.push(text);
}

export function buildPublicAddressView(parts: PublicAddressParts | null | undefined): PublicAddressView {
  if (!parts) {
    return { lines: [], compact: "", singleLine: "", localityLabel: null };
  }
  const structuredValues = [
    parts.addressLine1,
    parts.addressLine2,
    parts.area,
    parts.city,
    parts.state,
    parts.country,
    parts.postalCode,
    parts.pinCode,
  ];
  const hasStructured = structuredValues.some((item) => Boolean(cleanText(item)));
  const lines: string[] = [];
  if (hasStructured) {
    appendUnique(lines, parts.addressLine1);
    appendUnique(lines, parts.addressLine2);
    appendUnique(lines, joinComma([parts.area, parts.city]));
    appendUnique(lines, joinSpace([parts.state, parts.postalCode ?? parts.pinCode]));
    appendUnique(lines, parts.country);
  } else {
    appendUnique(lines, parts.address);
  }
  const localityLabel = joinComma([parts.area, parts.city]) || null;
  const compact = localityLabel || lines[0] || cleanText(parts.address) || "";
  const singleLine = lines.length ? lines.join(", ") : compact;
  return {
    lines,
    compact,
    singleLine,
    localityLabel,
  };
}

export function normalizeDisplayList(values: Array<string | null | undefined> | null | undefined) {
  const seen = new Set<string>();
  const result: string[] = [];
  (values ?? []).forEach((value) => {
    const text = cleanText(value);
    if (!text) {
      return;
    }
    const key = text.toLowerCase();
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    result.push(text);
  });
  return result;
}

export function formatPublicAddressLines(parts: PublicAddressParts | null | undefined) {
  return buildPublicAddressView(parts).lines;
}

export function formatPublicAddressInline(parts: PublicAddressParts | null | undefined) {
  return buildPublicAddressView(parts).singleLine;
}

export function formatPublicAddressCompact(parts: PublicAddressParts | null | undefined) {
  return buildPublicAddressView(parts).compact;
}

export function parseFiniteExperienceYears(value: unknown): number | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const numeric = typeof value === "number" ? value : typeof value === "string" ? Number(value.trim()) : Number(value);
  if (!Number.isFinite(numeric) || numeric < 0) {
    return null;
  }
  return numeric;
}

export function formatExperienceLabel(value: unknown) {
  const years = parseFiniteExperienceYears(value);
  return years == null ? "Not provided" : `${years} years experience`;
}

export function resolveClinicEstablishedYear(value: unknown, registrationNumber?: unknown, fallback?: number | null) {
  const parsed = parseFiniteExperienceYears(value);
  if (parsed != null && parsed >= 1900 && parsed <= 2100) {
    return parsed;
  }
  if (typeof registrationNumber === "string") {
    const match = registrationNumber.match(/\b(19\d{2}|20\d{2})\b/);
    if (match) {
      return Number(match[1]);
    }
  }
  return fallback != null && Number.isFinite(fallback) ? fallback : null;
}

function parseTime(value: unknown) {
  const text = cleanText(value);
  if (!text) {
    return null;
  }
  const match = text.match(/^([01]?\d|2[0-3]):([0-5]\d)$/);
  if (match) {
    return `${match[1].padStart(2, "0")}:${match[2]}`;
  }
  return text;
}

function dayLabel(day: string) {
  return day.charAt(0).toUpperCase() + day.slice(1);
}

function normalizeTimingEntries(value: unknown): Array<PublicWeeklyInterval | { dayOfWeek?: string | null; day?: string | null; intervals?: unknown[] }> {
  if (Array.isArray(value)) {
    return value as Array<PublicWeeklyInterval | { dayOfWeek?: string | null; day?: string | null; intervals?: unknown[] }>;
  }
  if (value && typeof value === "object") {
    const objectValue = value as { intervals?: unknown[]; weekly?: unknown[] };
    if (Array.isArray(objectValue.intervals)) {
      return objectValue.intervals as Array<PublicWeeklyInterval | { dayOfWeek?: string | null; day?: string | null; intervals?: unknown[] }>;
    }
    if (Array.isArray(objectValue.weekly)) {
      return objectValue.weekly as Array<PublicWeeklyInterval | { dayOfWeek?: string | null; day?: string | null; intervals?: unknown[] }>;
    }
  }
  return [];
}

function normalizeTimingLabel(interval: PublicWeeklyInterval, inheritedDay?: string | null) {
  const day = normalizeDay(interval.dayOfWeek ?? interval.day ?? inheritedDay);
  if (!day || interval.closed) {
    return null;
  }
  const start = parseTime(interval.startTime ?? interval.start ?? interval.open);
  const end = parseTime(interval.endTime ?? interval.end ?? interval.close);
  if (!start || !end) {
    return null;
  }
  const startMinutes = minutes(start);
  const endMinutes = minutes(end);
  if (startMinutes < 0 || endMinutes <= startMinutes) {
    return null;
  }
  return { day, label: `${start}–${end}`, startMinutes, endMinutes };
}

function minutes(value: string) {
  const normalized = value.match(/^(\d{1,2}):(\d{2})\s*(AM|PM)?$/i);
  if (!normalized) {
    const compact = value.match(/^([01]?\d|2[0-3]):([0-5]\d)$/);
    if (!compact) {
      return -1;
    }
    return Number(compact[1]) * 60 + Number(compact[2]);
  }
  let hour = Number(normalized[1]);
  const minute = Number(normalized[2]);
  const suffix = normalized[3]?.toUpperCase();
  if (suffix === "PM" && hour < 12) hour += 12;
  if (suffix === "AM" && hour === 12) hour = 0;
  return hour * 60 + minute;
}

export function formatWeeklyTimings(value: unknown, timezone?: string | null): PublicWeeklyTimingsView {
  const warnings: string[] = [];
  const grouped = new Map<string, Array<{ label: string; startMinutes: number; endMinutes: number }>>();
  let skippedCount = 0;

  normalizeTimingEntries(value).forEach((entry) => {
    if (!entry || typeof entry !== "object") {
      skippedCount += 1;
      return;
    }
    if (Array.isArray((entry as { intervals?: unknown[] }).intervals)) {
      const parent = entry as { dayOfWeek?: string | null; day?: string | null; intervals?: unknown[] };
      (parent.intervals ?? []).forEach((interval) => {
        const normalized = normalizeTimingLabel(interval as PublicWeeklyInterval, parent.dayOfWeek ?? parent.day ?? null);
        if (!normalized) {
          skippedCount += 1;
          return;
        }
        const sessions = grouped.get(normalized.day) ?? [];
        if (sessions.some((session) => session.label === normalized.label)) {
          skippedCount += 1;
          return;
        }
        sessions.push(normalized);
        grouped.set(normalized.day, sessions);
      });
      return;
    }
    const normalized = normalizeTimingLabel(entry as PublicWeeklyInterval);
    if (!normalized) {
      skippedCount += 1;
      return;
    }
    const sessions = grouped.get(normalized.day) ?? [];
    if (sessions.some((session) => session.label === normalized.label)) {
      skippedCount += 1;
      return;
    }
    sessions.push(normalized);
    grouped.set(normalized.day, sessions);
  });

  const rows = WEEKDAY_ORDER.map((day) => {
    const sessions = (grouped.get(day) ?? []).sort((left, right) => left.startMinutes - right.startMinutes);
    const uniqueSessions = sessions.filter((session, index, current) => index === current.findIndex((candidate) => candidate.label === session.label));
    return {
      dayOfWeek: day.toUpperCase(),
      day: dayLabel(day),
      sessions: uniqueSessions.map((session) => session.label),
      hours: uniqueSessions.length ? uniqueSessions.map((session) => session.label).join(" · ") : "Closed",
      closed: uniqueSessions.length === 0,
    };
  });

  if (skippedCount > 0) {
    warnings.push(`${skippedCount} timing entr${skippedCount === 1 ? "y" : "ies"} could not be read.`);
  }

  return {
    timezone: cleanText(timezone) || null,
    rows,
    warnings,
    skippedCount,
  };
}
