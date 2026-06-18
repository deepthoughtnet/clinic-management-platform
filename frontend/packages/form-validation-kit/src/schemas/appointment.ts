import { z } from "zod";

import { dateString, optionalString, requiredString, timeString } from "../validators/common.js";
import { appointmentStatusSchema } from "../validators/healthcare.js";

export const appointmentCreateSchema = z.object({
  patientId: requiredString("Patient is required."),
  doctorUserId: requiredString("Doctor is required."),
  appointmentDate: dateString("Appointment date is required."),
  appointmentTime: timeString("Appointment time is required."),
  reason: optionalString(),
  type: z.string().optional(),
  status: appointmentStatusSchema.optional(),
  priority: z.string().optional(),
  allowAdHocBooking: z.boolean().optional(),
});

export const appointmentRescheduleSchema = z.object({
  appointmentId: requiredString(),
  appointmentDate: dateString("Appointment date is required."),
  appointmentTime: timeString("Appointment time is required."),
  reason: optionalString(),
  type: z.string().optional(),
  status: appointmentStatusSchema.optional(),
});

export type AppointmentCreateValues = z.infer<typeof appointmentCreateSchema>;
export type AppointmentRescheduleValues = z.infer<typeof appointmentRescheduleSchema>;
