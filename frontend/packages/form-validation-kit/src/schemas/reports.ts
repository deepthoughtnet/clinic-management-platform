import { z } from "zod";

import { dateString, optionalString, uuid } from "../validators/common.js";
import { validationMessages } from "../helpers/errorMessages.js";

const toOptionalString = (value: unknown) => {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

const reportDateValue = (value: string) => new Date(`${value}T00:00:00.000Z`).getTime();

export const reportDateRangeSchema = z.object({
  from: dateString().optional(),
  to: dateString().optional(),
}).superRefine((value, context) => {
  if (value.from && value.to && reportDateValue(value.from) > reportDateValue(value.to)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["to"],
      message: validationMessages.invalidDateRange,
    });
  }
});

export const reportFilterSchema = z.object({
  from: dateString().optional(),
  to: dateString().optional(),
  doctorUserId: uuid().optional(),
  patientId: uuid().optional(),
  status: z.preprocess(toOptionalString, z.string().max(40).optional()),
  paymentMode: z.preprocess(toOptionalString, z.string().max(20).optional()),
  source: z.preprocess(toOptionalString, z.string().max(40).optional()),
}).superRefine((value, context) => {
  if (value.from && value.to && reportDateValue(value.from) > reportDateValue(value.to)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["to"],
      message: validationMessages.invalidDateRange,
    });
  }
});

export const reportExportSchema = z.object({
  format: z.enum(["CSV", "PDF", "XLSX"]).default("CSV"),
  fileName: optionalString(),
});
