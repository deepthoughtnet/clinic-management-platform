import { z } from "zod";

import { optionalEmail, optionalString } from "../validators/common.js";
import { optionalIndianMobileNumber } from "../validators/india.js";
import { fileUploadSchema } from "./fileUpload.js";
import { validationMessages } from "../helpers/errorMessages.js";

const LEAD_STATUSES = ["NEW", "CONTACTED", "QUALIFIED", "FOLLOW_UP_REQUIRED", "APPOINTMENT_BOOKED", "CONVERTED", "LOST", "SPAM"] as const;
const LEAD_PRIORITIES = ["LOW", "MEDIUM", "HIGH"] as const;
const LEAD_SOURCES = ["WEBSITE", "WEBINAR", "WALK_IN", "PHONE_CALL", "WHATSAPP", "FACEBOOK", "GOOGLE_ADS", "REFERRAL", "CAMPAIGN", "MANUAL", "AI_RECEPTIONIST", "OTHER"] as const;

const toOptionalString = (value: unknown) => {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

const toOptionalDateTimeLocal = (value: unknown) => {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(trimmed) ? trimmed : value;
};

const leadCoreSchema = z.object({
  firstName: optionalString(),
  lastName: optionalString(),
  phone: optionalIndianMobileNumber(validationMessages.invalidIndianMobile),
  email: optionalEmail(),
  source: z.enum(LEAD_SOURCES).optional(),
  sourceDetails: optionalString(),
  status: z.enum(LEAD_STATUSES).optional(),
  priority: z.enum(LEAD_PRIORITIES).optional(),
  notes: optionalString(),
  tags: optionalString(),
  nextFollowUpAt: z.preprocess(
    toOptionalDateTimeLocal,
    z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/, validationMessages.invalidDate).optional(),
  ),
  campaignId: z.preprocess(toOptionalString, z.string().optional()),
  assignedToAppUserId: z.preprocess(toOptionalString, z.string().optional()),
});

export const leadCreateSchema = leadCoreSchema;
export const leadUpdateSchema = leadCoreSchema;

export const leadImportSchema = z.object({
  file: fileUploadSchema({
    required: true,
    allowedMimeTypes: ["text/csv", "application/csv", "application/vnd.ms-excel"],
    allowedExtensions: ["csv"],
    maxBytes: 5 * 1024 * 1024,
  }),
});

export const leadFilterSchema = z.object({
  search: z.preprocess(toOptionalString, z.string().max(120, validationMessages.invalidSearchQuery).optional()),
  status: z.enum(LEAD_STATUSES).optional(),
  source: z.enum(LEAD_SOURCES).optional(),
  priority: z.enum(LEAD_PRIORITIES).optional(),
  page: z.preprocess((value) => {
    if (value == null || value === "") return undefined;
    const parsed = Number(value);
    return Number.isNaN(parsed) ? value : parsed;
  }, z.number().int().min(0, validationMessages.invalidPage).optional()),
  size: z.preprocess((value) => {
    if (value == null || value === "") return undefined;
    const parsed = Number(value);
    return Number.isNaN(parsed) ? value : parsed;
  }, z.number().int().min(1, validationMessages.invalidPageSize).max(200, validationMessages.invalidPageSize).optional()),
});
