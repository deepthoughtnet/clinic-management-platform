import { formatDisplayDate } from "./dateDisplay.js";

function pad2(value) {
  return String(value).padStart(2, "0");
}

function localDateKey(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

function toSlotDateTime(slot) {
  if (!slot?.appointmentDate || !slot?.slotTime) {
    return null;
  }
  const candidate = new Date(`${slot.appointmentDate}T${slot.slotTime}:00`);
  return Number.isNaN(candidate.getTime()) ? null : candidate;
}

export function isFutureSelectableSlot(slot, now = new Date()) {
  if (!slot?.selectable) {
    return false;
  }
  const slotDateTime = toSlotDateTime(slot);
  return slotDateTime ? slotDateTime.getTime() >= now.getTime() : false;
}

export function formatSlotGroupLabel(dateValue, now = new Date()) {
  const today = localDateKey(now);
  const tomorrow = localDateKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1));
  if (dateValue === today) {
    return "Today";
  }
  if (dateValue === tomorrow) {
    return "Tomorrow";
  }
  return formatDisplayDate(dateValue);
}

export function groupAvailableSlotsByDate(slots, now = new Date()) {
  const grouped = new Map();
  slots
    .filter((slot) => isFutureSelectableSlot(slot, now))
    .sort((left, right) => {
      const leftDateTime = toSlotDateTime(left)?.getTime() ?? 0;
      const rightDateTime = toSlotDateTime(right)?.getTime() ?? 0;
      return leftDateTime - rightDateTime;
    })
    .forEach((slot) => {
      const key = slot.appointmentDate;
      if (!grouped.has(key)) {
        grouped.set(key, []);
      }
      grouped.get(key).push(slot);
    });

  return Array.from(grouped.entries()).map(([date, items]) => ({
    date,
    label: formatSlotGroupLabel(date, now),
    slots: items,
  }));
}
