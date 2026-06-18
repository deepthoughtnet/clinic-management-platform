import { z } from "zod";

import { en } from "../messages/en.js";

const stripNonDigits = (value: unknown) => {
  if (typeof value !== "string") return value;
  return value.replace(/[^\d+]/g, "");
};

const normalizeIndianMobile = (value: unknown) => {
  if (typeof value !== "string") return value;
  let digits = value.replace(/[^\d]/g, "");
  if (digits.startsWith("91") && digits.length === 12) digits = digits.slice(2);
  if (digits.startsWith("0") && digits.length === 11) digits = digits.slice(1);
  return digits;
};

const toUpperTrimmed = (value: unknown) => {
  if (typeof value !== "string") return value;
  return value.trim().toUpperCase();
};

export function indianMobileNumber(message: string = en.invalidIndianMobile) {
  return z.preprocess(
    normalizeIndianMobile,
    z.string().regex(/^[6-9]\d{9}$/, message),
  );
}

export function optionalIndianMobileNumber(message: string = en.invalidIndianMobile) {
  return z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      return normalizeIndianMobile(value);
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
  indianMobileNumber,
  optionalIndianMobileNumber,
  indianPincode,
  optionalGstin,
  optionalPan,
};
