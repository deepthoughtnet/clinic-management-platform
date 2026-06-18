import { z } from "zod";

import { dateString } from "../validators/common.js";
import { validationMessages } from "../helpers/errorMessages.js";

const toOptionalString = (value: unknown) => {
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

const toOptionalInteger = (value: unknown) => {
  const normalized = toOptionalNumber(value);
  if (normalized === undefined) return undefined;
  return normalized;
};

const localDateValue = (value: string) => new Date(`${value}T00:00:00.000Z`).getTime();

export const tableSearchSchema = z.object({
  search: z.preprocess(
    toOptionalString,
    z.string().max(120, validationMessages.invalidSearchQuery).optional(),
  ),
});

export const dateRangeFilterSchema = z.object({
  from: dateString().optional(),
  to: dateString().optional(),
}).superRefine((value, context) => {
  if (value.from && value.to && localDateValue(value.from) > localDateValue(value.to)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["to"],
      message: validationMessages.invalidDateRange,
    });
  }
});

export const paginationFilterSchema = z.object({
  page: z.preprocess(
    toOptionalInteger,
    z.number().int().min(0, validationMessages.invalidPage).optional(),
  ),
  pageSize: z.preprocess(
    toOptionalInteger,
    z.number().int().min(1, validationMessages.invalidPageSize).max(200, validationMessages.invalidPageSize).optional(),
  ),
});

export const sortFilterSchema = z.object({
  sortField: z.preprocess(toOptionalString, z.string().max(80).optional()),
  sortDirection: z.enum(["asc", "desc"]).optional(),
});
