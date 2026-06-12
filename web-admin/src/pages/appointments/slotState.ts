import type { DoctorAvailabilitySlot } from "../../api/clinicApi";
import { isCurrentSlot, isSlotExpired } from "./bookingValidation";

export type AppointmentSlotUiState =
  | "AVAILABLE"
  | "CURRENT"
  | "PARTIALLY_BOOKED"
  | "FULL"
  | "PAST"
  | "LEAVE"
  | "HOLIDAY"
  | "UNAVAILABLE";

export type AppointmentSlotPresentation = {
  state: AppointmentSlotUiState;
  isPast: boolean;
  isCurrent: boolean;
  selectable: boolean;
  bookable: boolean;
  counterEligible: boolean;
  hidden: boolean;
  tooltip: string;
};

type Options = {
  hidePastSlots?: boolean;
};

function reasonText(slot: DoctorAvailabilitySlot) {
  return (slot.notBookableReason || slot.reason || "").trim().toLowerCase();
}

function isHolidaySlot(slot: DoctorAvailabilitySlot) {
  return slot.status === "UNAVAILABLE" && reasonText(slot).includes("holiday");
}

function tooltipForState(state: AppointmentSlotUiState, slot: DoctorAvailabilitySlot) {
  switch (state) {
    case "PAST":
      return "Past slot - booking closed";
    case "FULL":
      return "Capacity reached";
    case "LEAVE":
    case "HOLIDAY":
      return "Doctor unavailable";
    case "UNAVAILABLE":
      return "Not bookable";
    case "CURRENT":
      return slot.bookable ? "Current slot" : slot.notBookableReason || "Not bookable";
    case "PARTIALLY_BOOKED":
    case "AVAILABLE":
    default:
      return slot.notBookableReason || "Bookable slot";
  }
}

export function getAppointmentSlotPresentation(
  date: string,
  slot: DoctorAvailabilitySlot,
  timeZone?: string | null,
  clinicNow?: string | null,
  options: Options = {},
): AppointmentSlotPresentation {
  const isPast = typeof slot.past === "boolean" ? slot.past : isSlotExpired(date, slot, undefined, timeZone, clinicNow);
  const isCurrent = !isPast && (typeof slot.current === "boolean" ? slot.current : isCurrentSlot(date, slot, undefined, timeZone, clinicNow));
  const isHoliday = isHolidaySlot(slot);
  let state: AppointmentSlotUiState;

  if (isPast) {
    state = "PAST";
  } else if (isCurrent) {
    state = "CURRENT";
  } else if (slot.status === "FULL" || slot.bookedCount >= slot.maxPatientsPerSlot) {
    state = "FULL";
  } else if (slot.status === "PARTIALLY_BOOKED") {
    state = "PARTIALLY_BOOKED";
  } else if (slot.status === "LEAVE") {
    state = "LEAVE";
  } else if (isHoliday) {
    state = "HOLIDAY";
  } else if (slot.status === "BREAK" || slot.status === "UNAVAILABLE" || slot.status === "CONFLICTED") {
    state = "UNAVAILABLE";
  } else {
    state = "AVAILABLE";
  }

  const bookable = !isPast && Boolean(slot.bookable) && (state === "AVAILABLE" || state === "CURRENT" || state === "PARTIALLY_BOOKED");
  const selectable = bookable && !isPast;
  const counterEligible = !isPast && bookable && !["FULL", "LEAVE", "HOLIDAY", "UNAVAILABLE"].includes(state);
  const hidden = Boolean(options.hidePastSlots && isPast);

  return {
    state,
    isPast,
    isCurrent,
    selectable,
    bookable,
    counterEligible,
    hidden,
    tooltip: tooltipForState(state, slot),
  };
}

