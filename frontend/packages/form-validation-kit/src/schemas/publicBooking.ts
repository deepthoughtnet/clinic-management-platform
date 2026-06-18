import { z } from "zod";

import { dateString, optionalString, requiredString } from "../validators/common.js";

export const bookAppointmentSchema = z.object({
  doctorId: requiredString("Doctor is required."),
  appointmentDate: dateString("Appointment date is required."),
  slot: requiredString("Slot is required."),
  reason: optionalString(),
  appointmentType: optionalString(),
});

export type BookAppointmentValues = z.infer<typeof bookAppointmentSchema>;
