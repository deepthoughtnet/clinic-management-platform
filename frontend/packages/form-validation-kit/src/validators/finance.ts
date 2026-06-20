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

const toOptionalInteger = (value: unknown) => {
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

export const billingDiscountTypeValues = ["NONE", "FLAT", "PERCENT"] as const;
export const billingSourceValues = ["MANUAL_BILLING", "CONSULTATION", "APPOINTMENT", "PHARMACY", "LAB", "OTHER"] as const;
export const billingLineTypeValues = [
  "CONSULTATION",
  "REGISTRATION",
  "FOLLOW_UP",
  "EMERGENCY",
  "LAB_TEST",
  "MEDICINE",
  "PROCEDURE",
  "PACKAGE",
  "OTHER_CHARGE",
  "TEST",
  "VACCINATION",
  "SERVICE",
  "OTHER",
] as const;
export const billingPaymentModeValues = ["CASH", "CARD", "UPI", "INSURANCE", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"] as const;

export function trimmedText(maxLength: number, message: string) {
  return z.preprocess(
    toRequiredTrimmedString,
    z.string().min(1, message).max(maxLength, message).refine(hasLetterOrNumber, message),
  );
}

export function optionalTrimmedText(maxLength: number, message: string) {
  return z.preprocess(
    toOptionalTrimmedString,
    z.string().max(maxLength, message).refine((value) => value == null || value === "" || hasLetterOrNumber(value), message).optional(),
  );
}

export function moneyAmount(max: number, message: string, allowZero = true) {
  const schema = allowZero ? z.number().min(0, message) : z.number().positive(message);
  return z.preprocess(
    toOptionalNumber,
    schema.max(max, message).refine(hasAtMostTwoDecimals, message),
  );
}

export function wholeNumber(min: number, max: number, message: string) {
  return z.preprocess(
    toOptionalInteger,
    z.number().int(message).min(min, message).max(max, message),
  );
}

export function percentage(max = 100, message = "Percentage must be between 0 and 100.") {
  return z.preprocess(
    toOptionalNumber,
    z.number().min(0, message).max(max, message).refine(hasAtMostTwoDecimals, message),
  );
}

export function optionalDateString(message: string) {
  return z.preprocess(
    toOptionalTrimmedString,
    z.string().regex(/^\d{4}-\d{2}-\d{2}$/, message).optional(),
  );
}

export function requiredDateString(message: string) {
  return z.preprocess(
    toRequiredTrimmedString,
    z.string().regex(/^\d{4}-\d{2}-\d{2}$/, message),
  );
}

export function billingDateValue(value: string) {
  return new Date(`${value}T00:00:00.000Z`).getTime();
}

export function normalizeBillingDiscountType(value: string | null | undefined) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized === "FLAT") return "AMOUNT";
  if (normalized === "PERCENT") return "PERCENTAGE";
  return normalized;
}
