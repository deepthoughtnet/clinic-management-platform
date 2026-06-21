import { z } from "zod";

import { optionalEmail, optionalString } from "../validators/common.js";
import { optionalIndianMobileNumber, indianMobileNumber } from "../validators/india.js";
import { validationMessages } from "../helpers/errorMessages.js";

const toOptionalTrimmedString = (value: unknown) => {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

const toOptionalNumber = (value: unknown) => {
  if (value == null || value === "") return undefined;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return undefined;
    const parsed = Number(trimmed);
    return Number.isNaN(parsed) ? value : parsed;
  }
  return value;
};

const optionalText = (max: number, message: string) => z.preprocess(toOptionalTrimmedString, z.string().max(max, message).optional());
const requiredText = (max: number, message: string) => z.preprocess((value) => (typeof value === "string" ? value.trim() : ""), z.string().min(1, message).max(max, message));
const optionalPositiveInteger = (max: number, message: string) =>
  z.preprocess(toOptionalNumber, z.number().int(message).min(0, message).max(max, message).optional());

const toOptionalDateTimeLocal = (value: unknown) => {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

function dateTimeLocalSchema(message: string) {
  return z.preprocess(toOptionalDateTimeLocal, z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/, message));
}

function dateTimeRangeSchema(endKey: string, start: string | undefined, end: string | undefined, ctx: z.RefinementCtx) {
  if (start && end && start > end) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: [endKey], message: "End date/time must be on or after start date/time." });
  }
}

export const engageCampaignSchema = z.object({
  name: requiredText(60, "Campaign name is required and must be 60 characters or fewer."),
  description: optionalText(250, "Description must be 250 characters or fewer."),
  callType: z.enum(["EMAIL", "SMS", "WHATSAPP", "IN_APP", "APP_NOTIFICATION", "VOICE"]),
  status: z.enum(["DRAFT", "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED"]),
  templateId: optionalString(),
  retryEnabled: z.boolean().optional(),
  maxAttempts: optionalPositiveInteger(20, "Max attempts must be zero or greater."),
  escalationEnabled: z.boolean().optional(),
});

export const engageMessagingTestSendSchema = z.object({
  channel: z.enum(["EMAIL", "SMS", "WHATSAPP"]),
  recipient: z.preprocess(toOptionalTrimmedString, z.string().min(1, "Recipient is required.").optional()),
  subject: optionalText(60, "Subject must be 60 characters or fewer."),
  body: requiredText(250, "Message body is required and must be 250 characters or fewer."),
});

export const engageWebinarSchema = z.object({
  title: requiredText(60, "Title is required and must be 60 characters or fewer."),
  description: optionalText(250, "Description must be 250 characters or fewer."),
  webinarType: z.enum(["HEALTH_AWARENESS", "WELLNESS", "CLINIC_EVENT", "MARKETING", "EDUCATIONAL", "OTHER"]),
  campaignId: optionalString(),
  webinarUrl: optionalText(250, "Webinar URL must be 250 characters or fewer."),
  organizerName: optionalText(60, "Organizer name must be 60 characters or fewer."),
  organizerEmail: optionalEmail("Enter a valid email address."),
  scheduledStartAt: dateTimeLocalSchema("Start date/time is required."),
  scheduledEndAt: dateTimeLocalSchema("End date/time is required."),
  timezone: requiredText(60, "Timezone is required and must be 60 characters or fewer."),
  capacity: z.preprocess(toOptionalNumber, z.number().int(validationMessages.invalidPositiveNumber).min(0, validationMessages.invalidNonNegativeNumber).optional()),
  registrationEnabled: z.boolean().optional(),
  reminderEnabled: z.boolean().optional(),
  followupEnabled: z.boolean().optional(),
  tags: optionalText(250, "Tags must be 250 characters or fewer."),
}).superRefine((value, ctx) => {
  dateTimeRangeSchema("scheduledEndAt", value.scheduledStartAt, value.scheduledEndAt, ctx);
});

export const engageWebinarRegistrationSchema = z.object({
  attendeeName: requiredText(60, "Attendee name is required and must be 60 characters or fewer."),
  attendeeEmail: optionalEmail("Enter a valid email address."),
  attendeePhone: optionalIndianMobileNumber(),
  patientId: optionalString(),
  leadId: optionalString(),
});

export const engageAiCallCampaignSchema = z.object({
  name: requiredText(60, "Campaign name is required and must be 60 characters or fewer."),
  description: optionalText(250, "Description must be 250 characters or fewer."),
  callType: z.enum(["APPOINTMENT_REMINDER", "MISSED_APPOINTMENT_FOLLOW_UP", "FOLLOW_UP_REMINDER", "REFILL_REMINDER", "VACCINATION_REMINDER", "BILLING_REMINDER", "WELLNESS_MESSAGE", "CUSTOM"]),
  status: z.enum(["DRAFT", "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED"]),
  templateId: optionalString(),
  retryEnabled: z.boolean().optional(),
  maxAttempts: z.preprocess(toOptionalNumber, z.number().int(validationMessages.invalidPositiveNumber).min(1, validationMessages.invalidPositiveNumber).max(20, validationMessages.invalidPositiveNumber).optional()),
  escalationEnabled: z.boolean().optional(),
});

export const engageAiCallManualCallSchema = z.object({
  phoneNumber: indianMobileNumber(),
  patientId: optionalString(),
  leadId: optionalString(),
  templateId: optionalString(),
  callType: z.enum(["APPOINTMENT_REMINDER", "MISSED_APPOINTMENT_FOLLOW_UP", "FOLLOW_UP_REMINDER", "REFILL_REMINDER", "VACCINATION_REMINDER", "BILLING_REMINDER", "WELLNESS_MESSAGE", "CUSTOM"]),
  script: optionalText(250, "Script must be 250 characters or fewer."),
  scheduledAt: z.preprocess(toOptionalDateTimeLocal, z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/, "Enter a valid scheduled time.").optional()),
});

export const engageAiCallTriggerTargetSchema = z.object({
  patientId: optionalString(),
  leadId: optionalString(),
  phoneNumber: optionalIndianMobileNumber(),
  script: optionalText(250, "Script must be 250 characters or fewer."),
  scheduledAt: z.preprocess(toOptionalDateTimeLocal, z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/, "Enter a valid scheduled time.").optional()),
});

export const engageAiCallRescheduleSchema = z.object({
  scheduledAt: dateTimeLocalSchema("Reschedule time is required."),
  reason: optionalText(250, "Reason must be 250 characters or fewer."),
});

export const engageOpsConsoleFilterSchema = z.object({
  startDate: z.preprocess(toOptionalTrimmedString, z.string().regex(/^\d{4}-\d{2}-\d{2}$/, validationMessages.invalidDate).optional()),
  endDate: z.preprocess(toOptionalTrimmedString, z.string().regex(/^\d{4}-\d{2}-\d{2}$/, validationMessages.invalidDate).optional()),
  providerName: optionalText(60, "Provider name must be 60 characters or fewer."),
}).superRefine((value, ctx) => {
  if (value.startDate && value.endDate && value.startDate > value.endDate) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["endDate"], message: validationMessages.invalidDateRange });
  }
});
