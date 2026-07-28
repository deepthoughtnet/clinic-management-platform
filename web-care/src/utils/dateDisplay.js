function pad2(value) {
  return String(value).padStart(2, "0");
}

function isDateOnlyString(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value.trim());
}

function toDate(value) {
  if (!value) {
    return null;
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }
  const trimmed = String(value).trim();
  if (!trimmed) {
    return null;
  }
  const parsed = new Date(isDateOnlyString(trimmed) ? `${trimmed}T00:00:00.000Z` : trimmed);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function formatDisplayDate(value) {
  const date = toDate(value);
  if (!date) {
    return value || "Not available yet";
  }
  return `${pad2(date.getUTCDate())}/${pad2(date.getUTCMonth() + 1)}/${date.getUTCFullYear()}`;
}

export function formatDisplayTime(value) {
  if (!value) {
    return "Time to be confirmed";
  }
  const trimmed = String(value).trim();
  if (!trimmed) {
    return "Time to be confirmed";
  }
  const timeMatch = trimmed.match(/^([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?$/);
  if (timeMatch) {
    const hour = Number(timeMatch[1]);
    const minute = Number(timeMatch[2]);
    const meridiem = hour >= 12 ? "PM" : "AM";
    const displayHour = hour % 12 || 12;
    return `${pad2(displayHour)}:${pad2(minute)} ${meridiem}`;
  }
  const date = toDate(trimmed);
  if (!date) {
    return trimmed;
  }
  const hours = date.getHours();
  const minutes = date.getMinutes();
  const meridiem = hours >= 12 ? "PM" : "AM";
  const displayHour = hours % 12 || 12;
  return `${pad2(displayHour)}:${pad2(minutes)} ${meridiem}`;
}

export function formatDisplayDateTime(value) {
  const date = toDate(value);
  if (!date) {
    return value || "Not available yet";
  }
  const hours = date.getHours();
  const minutes = date.getMinutes();
  const meridiem = hours >= 12 ? "PM" : "AM";
  const displayHour = hours % 12 || 12;
  return `${pad2(date.getDate())}/${pad2(date.getMonth() + 1)}/${date.getFullYear()} ${pad2(displayHour)}:${pad2(minutes)} ${meridiem}`;
}

export function formatDisplayDateTimeFromParts(dateValue, timeValue) {
  const date = toDate(dateValue);
  if (!date || !timeValue) {
    return `${formatDisplayDate(dateValue)} ${formatDisplayTime(timeValue)}`.trim();
  }
  const timeMatch = String(timeValue).trim().match(/^([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?$/);
  if (!timeMatch) {
    return `${formatDisplayDate(dateValue)} ${formatDisplayTime(timeValue)}`.trim();
  }
  const hours = Number(timeMatch[1]);
  const minutes = Number(timeMatch[2]);
  const meridiem = hours >= 12 ? "PM" : "AM";
  const displayHour = hours % 12 || 12;
  return `${pad2(date.getUTCDate())}/${pad2(date.getUTCMonth() + 1)}/${date.getUTCFullYear()} ${pad2(displayHour)}:${pad2(minutes)} ${meridiem}`;
}
