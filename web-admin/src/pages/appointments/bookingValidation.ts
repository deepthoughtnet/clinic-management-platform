import type { DoctorAvailabilitySlot } from "../../api/clinicApi";

export const BOOKING_TIME_GRACE_MINUTES = 0;
export const PAST_SLOT_MESSAGE = "Selected time has already passed. Please choose a current or future slot.";

function resolveTimeZone(timeZone?: string | null) {
  return timeZone && timeZone.trim() ? timeZone.trim() : "Asia/Kolkata";
}

function resolveClinicNow(clinicNow?: string | null) {
  if (!clinicNow || !clinicNow.trim()) {
    return new Date();
  }
  const parsed = new Date(clinicNow);
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
}

function currentClinicParts(timeZone?: string | null, clinicNow?: string | null) {
  const zone = resolveTimeZone(timeZone);
  const instant = resolveClinicNow(clinicNow);
  try {
    const formatter = new Intl.DateTimeFormat("en-CA", {
      timeZone: zone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    });
    const parts = formatter.formatToParts(instant).reduce<Record<string, string>>((acc, part) => {
      if (part.type !== "literal") {
        acc[part.type] = part.value;
      }
      return acc;
    }, {});
    const year = parts.year || "1970";
    const month = parts.month || "01";
    const day = parts.day || "01";
    const hour = parts.hour || "00";
    const minute = parts.minute || "00";
    return {
      dateKey: `${year}-${month}-${day}`,
      timeKey: `${hour}:${minute}`,
      minutes: Number(hour) * 60 + Number(minute),
    };
  } catch {
    const fallback = resolveClinicNow(clinicNow);
    return {
      dateKey: `${fallback.getUTCFullYear()}-${String(fallback.getUTCMonth() + 1).padStart(2, "0")}-${String(fallback.getUTCDate()).padStart(2, "0")}`,
      timeKey: `${String(fallback.getUTCHours()).padStart(2, "0")}:${String(fallback.getUTCMinutes()).padStart(2, "0")}`,
      minutes: fallback.getUTCHours() * 60 + fallback.getUTCMinutes(),
    };
  }
}

export function getClinicClockParts(timeZone?: string | null, clinicNow?: string | null) {
  return currentClinicParts(timeZone, clinicNow);
}

export function getClinicDateKey(timeZone?: string | null, clinicNow?: string | null) {
  return currentClinicParts(timeZone, clinicNow).dateKey;
}

export function formatClinicClockLabel(timeZone?: string | null, clinicNow?: string | null) {
  const zone = resolveTimeZone(timeZone);
  const instant = resolveClinicNow(clinicNow);
  try {
    const formatter = new Intl.DateTimeFormat("en-GB", {
      timeZone: zone,
      hour: "2-digit",
      minute: "2-digit",
      day: "2-digit",
      month: "short",
      year: "numeric",
      hourCycle: "h23",
    });
    const parts = formatter.formatToParts(instant).reduce<Record<string, string>>((acc, part) => {
      if (part.type !== "literal") {
        acc[part.type] = part.value;
      }
      return acc;
    }, {});
    return `Clinic time: ${parts.hour || "00"}:${parts.minute || "00"}, ${parts.day || "01"} ${parts.month || "Jan"} ${parts.year || "1970"}`;
  } catch {
    const now = resolveClinicNow(clinicNow);
    const hour = String(now.getUTCHours()).padStart(2, "0");
    const minute = String(now.getUTCMinutes()).padStart(2, "0");
    const day = String(now.getUTCDate()).padStart(2, "0");
    const month = now.toLocaleString(undefined, { month: "short", timeZone: "UTC" });
    const year = now.getUTCFullYear();
    return `Clinic time: ${hour}:${minute}, ${day} ${month} ${year}`;
  }
}

function toDateTime(date: string, time: string | null | undefined) {
  if (!date) {
    return null;
  }
  const normalizedTime = time && time.trim() ? time.slice(0, 5) : "23:59";
  const [year, month, day] = date.split("-").map((part) => Number(part));
  const [hour, minute] = normalizedTime.split(":").map((part) => Number(part));
  const candidate = new Date(Date.UTC(year, month - 1, day, hour, minute, 0));
  return Number.isNaN(candidate.getTime()) ? null : candidate;
}

export function isBookingTimePast(
  date: string,
  time: string | null | undefined,
  graceMinutes = BOOKING_TIME_GRACE_MINUTES,
  timeZone?: string | null,
  clinicNow?: string | null,
) {
  void graceMinutes;
  if (!date) {
    return false;
  }
  const candidateTime = time && time.trim() ? time.slice(0, 5) : "23:59";
  const candidateMinutes = Number(candidateTime.slice(0, 2)) * 60 + Number(candidateTime.slice(3, 5));
  const now = currentClinicParts(timeZone, clinicNow);
  if (date < now.dateKey) {
    return true;
  }
  if (date > now.dateKey) {
    return false;
  }
  return candidateMinutes <= now.minutes;
}

export function isSlotExpired(
  date: string,
  slot: DoctorAvailabilitySlot,
  graceMinutes = BOOKING_TIME_GRACE_MINUTES,
  timeZone?: string | null,
  clinicNow?: string | null,
) {
  void graceMinutes;
  if (typeof slot.past === "boolean") {
    return slot.past;
  }
  return isBookingTimePast(date, slot.slotEndTime, graceMinutes, timeZone, clinicNow);
}

export function isCurrentSlot(
  date: string,
  slot: DoctorAvailabilitySlot,
  graceMinutes = BOOKING_TIME_GRACE_MINUTES,
  timeZone?: string | null,
  clinicNow?: string | null,
) {
  void graceMinutes;
  if (typeof slot.current === "boolean") {
    return slot.current;
  }
  if (!date) {
    return false;
  }
  const now = currentClinicParts(timeZone, clinicNow);
  if (date !== now.dateKey) {
    return false;
  }
  const startMinutes = Number(slot.slotTime.slice(0, 2)) * 60 + Number(slot.slotTime.slice(3, 5));
  const endMinutes = Number(slot.slotEndTime.slice(0, 2)) * 60 + Number(slot.slotEndTime.slice(3, 5));
  return startMinutes <= now.minutes && now.minutes < endMinutes;
}

export function isTimeWithinSlot(date: string, time: string | null | undefined, slot: DoctorAvailabilitySlot, graceMinutes = BOOKING_TIME_GRACE_MINUTES) {
  const candidate = toDateTime(date, time);
  const start = toDateTime(date, slot.slotTime);
  const end = toDateTime(date, slot.slotEndTime);
  if (!candidate || !start || !end) {
    return false;
  }
  return start.getTime() <= candidate.getTime() && candidate.getTime() < end.getTime() + (graceMinutes * 60_000);
}

export function findSlotForTime(date: string, time: string | null | undefined, slots: DoctorAvailabilitySlot[]) {
  return slots.find((slot) => isTimeWithinSlot(date, time, slot)) || null;
}
