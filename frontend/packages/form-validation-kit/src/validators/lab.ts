import { z } from "zod";

const toTrimmedOptionalString = (value: unknown) => {
  if (value == null) return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

const toTrimmedRequiredString = (value: unknown) => {
  if (typeof value !== "string") return "";
  return value.trim();
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

const hasLetterOrNumber = (value: string) => /[A-Za-z0-9]/.test(value);
const hasAllowedCodeChars = (value: string) => /^[A-Za-z0-9/_-]+$/.test(value);
const hasAtMostTwoDecimals = (value: number) => Math.round(value * 100) === value * 100;

export const labCategoryValues = [
  "HEMATOLOGY",
  "BIOCHEMISTRY",
  "MICROBIOLOGY",
  "PATHOLOGY",
  "RADIOLOGY",
  "CARDIOLOGY",
  "IMMUNOLOGY",
  "SEROLOGY",
  "ENDOCRINOLOGY",
  "VIROLOGY",
  "MOLECULAR",
  "CYTOLOGY",
  "HISTOPATHOLOGY",
  "OTHER",
] as const;

export const labOrderStatusValues = [
  "ORDERED",
  "PAYMENT_PENDING",
  "PAID",
  "READY_FOR_COLLECTION",
  "SAMPLE_COLLECTED",
  "PROCESSING",
  "RESULT_ENTERED",
  "REPORT_READY",
  "REPORT_GENERATED",
  "DOCTOR_REVIEWED",
  "DELIVERED",
  "CANCELLED",
] as const;

export const labReviewDecisionValues = ["APPROVE", "SEND_BACK"] as const;
export const labOrderOriginValues = [
  "CONSULTATION",
  "WALK_IN",
  "DOCTOR_REFERRAL",
  "HEALTH_PACKAGE",
  "CORPORATE",
  "HOME_COLLECTION",
  "FOLLOW_UP",
] as const;

export function labRequiredNamedText(maxLength: number, message: string) {
  return z.preprocess(
    toTrimmedRequiredString,
    z.string().min(1, message).max(maxLength, message).refine(hasLetterOrNumber, message),
  );
}

export function labOptionalNamedText(maxLength: number, message: string) {
  return z.preprocess(
    toTrimmedOptionalString,
    z.string().max(maxLength, message).refine((value) => value == null || value === "" || hasLetterOrNumber(value), message).optional(),
  );
}

export function labOptionalPlainText(maxLength: number, message: string) {
  return z.preprocess(
    toTrimmedOptionalString,
    z.string().max(maxLength, message).optional(),
  );
}

export function labOptionalCodeText(maxLength: number, message: string) {
  return z.preprocess(
    toTrimmedOptionalString,
    z.string().max(maxLength, message).refine((value) => value == null || value === "" || hasAllowedCodeChars(value), message).optional(),
  );
}

export function labOptionalIntegerText(maxValue: number, message: string) {
  return z.preprocess(
    toTrimmedOptionalString,
    z.string()
      .regex(/^\d+$/, message)
      .refine((value) => value == null || value === "" || Number(value) <= maxValue, message)
      .optional(),
  );
}

export function labOptionalPositiveMoney(max: number, message: string) {
  return z.preprocess(
    toOptionalNumber,
    z.number().min(0, message).max(max, message).refine(hasAtMostTwoDecimals, message).optional(),
  );
}

export function labRequiredPositiveMoney(max: number, message: string) {
  return z.preprocess(
    toOptionalNumber,
    z.number().min(0, message).max(max, message).refine(hasAtMostTwoDecimals, message),
  );
}

export function labRequiredWholeNumber(min: number, max: number, message: string) {
  return z.preprocess(
    toOptionalNumber,
    z.number().int(message).min(min, message).max(max, message),
  );
}
