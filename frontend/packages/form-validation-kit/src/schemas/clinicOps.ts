import { z } from "zod";

import {
  money,
  optionalNonNegativeInteger,
  optionalText,
  positiveInteger,
  requiredText,
  trimmedSearch,
} from "../validators/clinicOps.js";
import { dateString, optionalString, timeString } from "../validators/common.js";

export const vaccinationMasterSchema = z.object({
  vaccineName: requiredText(60, "Vaccine name is required and must contain a letter or number."),
  description: optionalText(250, "Description must be 250 characters or fewer."),
  ageGroup: optionalText(60, "Age group must be 60 characters or fewer."),
  recommendedGapDays: optionalNonNegativeInteger(3650, "Gap days must be 0 or greater."),
  defaultPrice: money(999999, "Default price must be zero or greater and use at most 2 decimals."),
  active: z.boolean(),
});

export const vaccinationRecordSchema = z.object({
  patientId: requiredText(60, "Patient is required."),
  vaccineId: requiredText(60, "Vaccine is required."),
  doseNumber: optionalNonNegativeInteger(99, "Dose number must be 0 or greater."),
  givenDate: dateString("Given date is required."),
  nextDueDate: z.preprocess(
    (value) => (value == null || value === "" ? undefined : value),
    dateString("Next due date must be a valid date.").optional(),
  ),
  batchNumber: optionalText(60, "Batch number must be 60 characters or fewer."),
  notes: optionalText(250, "Notes must be 250 characters or fewer."),
  administeredByUserId: optionalText(60, "Administered by must be 60 characters or fewer."),
  addToBill: z.boolean().optional(),
  billId: optionalText(60, "Bill ID must be 60 characters or fewer."),
  billItemUnitPrice: z.preprocess(
    (value) => (value == null || value === "" ? undefined : value),
    money(999999, "Bill item unit price must be zero or greater and use at most 2 decimals.").optional(),
  ),
}).superRefine((value, ctx) => {
  if (value.addToBill && value.billItemUnitPrice == null) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["billItemUnitPrice"], message: "Bill item unit price is required when adding to bill." });
  }
});

export const doctorAvailabilitySchema = z.object({
  dayOfWeek: z.enum(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]),
  startTime: timeString("Start time is required."),
  endTime: timeString("End time is required."),
  breakStartTime: z.preprocess((value) => (value == null || value === "" ? undefined : value), timeString("Break start time must be valid.").optional()),
  breakEndTime: z.preprocess((value) => (value == null || value === "" ? undefined : value), timeString("Break end time must be valid.").optional()),
  consultationDurationMinutes: positiveInteger(480, "Duration must be a positive integer."),
  maxPatientsPerSlot: positiveInteger(999, "Capacity must be a positive integer."),
  active: z.boolean(),
}).superRefine((value, ctx) => {
  if (value.startTime >= value.endTime) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["endTime"], message: "End time must be after start time." });
  }
  if ((value.breakStartTime && !value.breakEndTime) || (!value.breakStartTime && value.breakEndTime)) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["breakEndTime"], message: "Break start and end time must be set together." });
  }
  if (value.breakStartTime && value.breakEndTime && value.breakStartTime >= value.breakEndTime) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["breakEndTime"], message: "Break end time must be after break start time." });
  }
});

export const doctorUnavailabilitySchema = z.object({
  startAt: z.string().trim().min(1, "Start date/time is required."),
  endAt: z.string().trim().min(1, "End date/time is required."),
  type: z.enum(["LEAVE", "HOLIDAY", "UNAVAILABLE", "EMERGENCY_BLOCK"]),
  reason: optionalText(60, "Reason must be 60 characters or fewer."),
  active: z.boolean(),
}).superRefine((value, ctx) => {
  if (value.startAt >= value.endAt) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["endAt"], message: "End date/time must be after start date/time." });
  }
});

export const notificationsFilterSchema = z.object({
  search: trimmedSearch(60, "Search text must be 60 characters or fewer."),
  status: optionalText(20, "Status must be 20 characters or fewer."),
  eventType: optionalText(40, "Event type must be 40 characters or fewer."),
  channel: optionalText(20, "Channel must be 20 characters or fewer."),
  patientId: optionalText(60, "Patient ID must be 60 characters or fewer."),
  from: z.preprocess((value) => (value == null || value === "" ? undefined : value), dateString("From date must be valid.").optional()),
  to: z.preprocess((value) => (value == null || value === "" ? undefined : value), dateString("To date must be valid.").optional()),
}).superRefine((value, ctx) => {
  if (value.from && value.to && value.from > value.to) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["to"], message: "To date must be on or after from date." });
  }
});

export const dashboardFilterSchema = z.object({
  startDate: dateString("Start date is required."),
  endDate: dateString("End date is required."),
  doctorUserId: optionalText(60, "Doctor must be 60 characters or fewer."),
}).superRefine((value, ctx) => {
  if (value.startDate > value.endDate) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["endDate"], message: "End date must be on or after start date." });
  }
});

export const dayBoardQuickCreateSchema = z.object({
  patientId: requiredText(60, "Patient is required."),
  doctorUserId: requiredText(60, "Doctor is required."),
  appointmentType: z.enum(["SCHEDULED", "FOLLOW_UP", "VACCINATION", "WALK_IN"]),
  appointmentDate: dateString("Appointment date is required."),
  appointmentTime: z.string().trim().max(5, "Appointment time must be valid.").optional().nullable(),
  reason: optionalText(250, "Reason must be 250 characters or fewer."),
});

export const consultationTabSchema = z.enum(["consultation", "prescription", "history", "investigations", "lab-orders", "ai-assist"]);

export const doctorAvailabilitySearchSchema = z.object({
  search: trimmedSearch(60, "Search text must be 60 characters or fewer."),
});

export type VaccinationMasterValues = z.infer<typeof vaccinationMasterSchema>;
export type VaccinationRecordValues = z.infer<typeof vaccinationRecordSchema>;
export type DoctorAvailabilityValues = z.infer<typeof doctorAvailabilitySchema>;
export type DoctorUnavailabilityValues = z.infer<typeof doctorUnavailabilitySchema>;
export type NotificationsFilterValues = z.infer<typeof notificationsFilterSchema>;
export type DashboardFilterValues = z.infer<typeof dashboardFilterSchema>;
export type DayBoardQuickCreateValues = z.infer<typeof dayBoardQuickCreateSchema>;
