import type { PatientPortalDoctorSlotResponse } from "../api/patientPortal";

export type BookingSlotGroup = {
  date: string;
  label: string;
  slots: PatientPortalDoctorSlotResponse[];
};

export function isFutureSelectableSlot(
  slot: PatientPortalDoctorSlotResponse | null | undefined,
  now?: Date,
): boolean;
export function formatSlotGroupLabel(dateValue: string, now?: Date): string;
export function groupAvailableSlotsByDate(
  slots: PatientPortalDoctorSlotResponse[],
  now?: Date,
): BookingSlotGroup[];
