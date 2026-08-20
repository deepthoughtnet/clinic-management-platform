import { z } from "zod";

import { en } from "../messages/en.js";

const stripNonDigits = (value: unknown) => {
  if (typeof value !== "string") return value;
  return value.replace(/[^\d+]/g, "");
};

export function normalizeIndianMobileInput(value: unknown) {
  if (typeof value !== "string") return value;
  const digits = value.replace(/[^\d]/g, "");
  if (digits.length === 12 && digits.startsWith("91")) {
    return digits.slice(2);
  }
  return digits;
}

const toUpperTrimmed = (value: unknown) => {
  if (typeof value !== "string") return value;
  return value.trim().toUpperCase();
};

export function indianMobileNumber(message: string = en.invalidIndianMobile) {
  return z.preprocess(
    normalizeIndianMobileInput,
    z.string().regex(/^[6-9]\d{9}$/, message),
  );
}

export function requiredIndianMobileNumber(message: string = "Mobile number is required.") {
  return z.preprocess(
    (value) => {
      if (value == null) return "";
      if (typeof value !== "string") return value;
      const trimmed = value.trim();
      if (!trimmed) return "";
      return normalizeIndianMobileInput(trimmed);
    },
    z.string().min(1, message).regex(/^[6-9]\d{9}$/, "Enter a valid 10-digit mobile number."),
  );
}

export function optionalIndianMobileNumber(message: string = en.invalidIndianMobile) {
  return z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      return normalizeIndianMobileInput(value);
    },
    z.string().regex(/^[6-9]\d{9}$/, message).optional(),
  );
}

export function indianPincode(message: string = en.invalidIndianPincode) {
  return z.preprocess(stripNonDigits, z.string().regex(/^\d{6}$/, message));
}

export function optionalGstin(message: string = en.invalidGstin) {
  return z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      return toUpperTrimmed(value);
    },
    z.string().regex(/^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, message).optional(),
  );
}

export function optionalPan(message: string = en.invalidPan) {
  return z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      return toUpperTrimmed(value);
    },
    z.string().regex(/^[A-Z]{5}\d{4}[A-Z]$/, message).optional(),
  );
}

export const indiaValidators = {
  normalizeIndianMobileInput,
  indianMobileNumber,
  requiredIndianMobileNumber,
  optionalIndianMobileNumber,
  indianPincode,
  optionalGstin,
  optionalPan,
};
