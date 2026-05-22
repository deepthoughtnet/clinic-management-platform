import type { DoctorAvailabilitySlot } from "../../api/clinicApi";

export const BOOKING_TIME_GRACE_MINUTES = 15;

function toDateTime(date: string, time: string | null | undefined) {
  if (!date) {
    return null;
  }
  const normalizedTime = time && time.trim() ? time.slice(0, 5) : "23:59";
  const candidate = new Date(`${date}T${normalizedTime}:00`);
  return Number.isNaN(candidate.getTime()) ? null : candidate;
}

export function isBookingTimePast(date: string, time: string | null | undefined, graceMinutes = BOOKING_TIME_GRACE_MINUTES) {
  const candidate = toDateTime(date, time);
  if (!candidate) {
    return false;
  }
  return candidate.getTime() + (graceMinutes * 60_000) < Date.now();
}

export function isSlotExpired(date: string, slot: DoctorAvailabilitySlot, graceMinutes = BOOKING_TIME_GRACE_MINUTES) {
  return isBookingTimePast(date, slot.slotEndTime, graceMinutes);
}

export function isCurrentSlot(date: string, slot: DoctorAvailabilitySlot, graceMinutes = BOOKING_TIME_GRACE_MINUTES) {
  const start = toDateTime(date, slot.slotTime);
  const end = toDateTime(date, slot.slotEndTime);
  if (!start || !end) {
    return false;
  }
  const now = Date.now();
  return start.getTime() <= now && now <= end.getTime() + (graceMinutes * 60_000);
}

export function isTimeWithinSlot(date: string, time: string | null | undefined, slot: DoctorAvailabilitySlot, graceMinutes = BOOKING_TIME_GRACE_MINUTES) {
  const candidate = toDateTime(date, time);
  const start = toDateTime(date, slot.slotTime);
  const end = toDateTime(date, slot.slotEndTime);
  if (!candidate || !start || !end) {
    return false;
  }
  return start.getTime() <= candidate.getTime() && candidate.getTime() <= end.getTime() + (graceMinutes * 60_000);
}

export function findSlotForTime(date: string, time: string | null | undefined, slots: DoctorAvailabilitySlot[]) {
  return slots.find((slot) => isTimeWithinSlot(date, time, slot)) || null;
}
