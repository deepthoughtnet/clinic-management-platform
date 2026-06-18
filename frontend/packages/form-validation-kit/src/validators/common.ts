import { z } from "zod";

import { en } from "../messages/en.js";

const toRequiredString = (value: unknown) => {
  if (typeof value !== "string") return "";
  return value.trim();
};

const toOptionalString = (value: unknown) => {
  if (value == null) return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
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

export function requiredString(message: string = en.required) {
  return z.preprocess(toRequiredString, z.string().min(1, message));
}

export function trimmedString(message: string = en.required) {
  return requiredString(message);
}

export function optionalString() {
  return z.preprocess(toOptionalString, z.string().optional());
}

export function email(message: string = en.invalidEmail) {
  return z.preprocess(toRequiredString, z.string().email(message));
}

export function optionalEmail(message: string = en.invalidEmail) {
  return z.preprocess(toOptionalString, z.string().email(message).optional());
}

export function password(message: string = en.invalidPassword) {
  return z
    .preprocess(toRequiredString, z.string().min(8, message))
    .refine((value) => /[A-Za-z]/.test(value), message)
    .refine((value) => /[0-9]/.test(value), message);
}

export function uuid(message: string = en.invalidUuid) {
  return z.preprocess(toRequiredString, z.string().uuid(message));
}

export function positiveNumber(message: string = en.invalidPositiveNumber) {
  return z.preprocess(toOptionalNumber, z.number().positive(message));
}

export function nonNegativeNumber(message: string = en.invalidNonNegativeNumber) {
  return z.preprocess(toOptionalNumber, z.number().nonnegative(message));
}

export function dateString(message: string = en.invalidDate) {
  return z
    .preprocess(toRequiredString, z.string().regex(/^\d{4}-\d{2}-\d{2}$/, message))
    .refine((value) => {
      const parsed = new Date(`${value}T00:00:00.000Z`);
      return !Number.isNaN(parsed.getTime()) && parsed.toISOString().startsWith(value);
    }, message);
}

export function timeString(message: string = en.invalidTime) {
  return z.preprocess(toRequiredString, z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, message));
}

export function url(message: string = en.invalidUrl) {
  return z.preprocess(toRequiredString, z.string().url(message));
}

export function enumValue<const Values extends readonly [string, ...string[]]>(values: Values) {
  return z.enum(values);
}

export const validators = {
  requiredString,
  optionalString,
  trimmedString,
  email,
  optionalEmail,
  password,
  uuid,
  positiveNumber,
  nonNegativeNumber,
  dateString,
  timeString,
  url,
  enumValue,
};
