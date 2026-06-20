import { z } from "zod";

const toOptionalTrimmedString = (value: unknown) => {
  if (value == null) return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

const toRequiredTrimmedString = (value: unknown) => {
  if (typeof value !== "string") return "";
  return value.trim();
};

const toOptionalNumber = (value: unknown) => {
  if (value == null || value === "") return undefined;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed === "") return undefined;
    const parsed = Number(trimmed);
    return Number.isNaN(parsed) ? value : parsed;
  }
  return value;
};

const hasLetterOrNumber = (value: string) => /[A-Za-z0-9]/.test(value);
const hasAtMostTwoDecimals = (value: number) => Math.round(value * 100) === value * 100;

export function requiredText(maxLength: number, message: string) {
  return z.preprocess(
    toRequiredTrimmedString,
    z.string().min(1, message).max(maxLength, message).refine(hasLetterOrNumber, message),
  );
}

export function optionalText(maxLength: number, message: string) {
  return z.preprocess(
    toOptionalTrimmedString,
    z.string().max(maxLength, message).refine((value) => value == null || value === "" || hasLetterOrNumber(value), message).optional(),
  );
}

export function optionalEmail(message: string) {
  return z.preprocess(
    toOptionalTrimmedString,
    z.string().email(message).optional(),
  );
}

export function mobileNumber(message: string) {
  return z.preprocess(
    toOptionalTrimmedString,
    z.string().regex(/^\d{10}$/, message),
  );
}

export function money(max: number, message: string, allowZero = true) {
  const schema = allowZero ? z.number().min(0, message) : z.number().positive(message);
  return z.preprocess(
    toOptionalNumber,
    schema.max(max, message).refine(hasAtMostTwoDecimals, message),
  );
}

export function positiveInteger(max: number, message: string) {
  return z.preprocess(
    toOptionalNumber,
    z.number().int(message).min(1, message).max(max, message),
  );
}

export function optionalNonNegativeInteger(max: number, message: string) {
  return z.preprocess(
    toOptionalNumber,
    z.number().int(message).min(0, message).max(max, message).optional(),
  );
}

export function trimmedSearch(maxLength: number, message: string) {
  return z.preprocess(
    toOptionalTrimmedString,
    z.string().max(maxLength, message).optional(),
  );
}

