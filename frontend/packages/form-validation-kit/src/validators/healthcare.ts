import { z } from "zod";

import { en } from "../messages/en.js";
import { requiredDateString } from "./finance.js";

const toOptionalUpperTrimmed = (value: unknown) => {
  if (value == null) return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed.toUpperCase();
};

const patientCodePattern = /^[A-Z0-9][A-Z0-9_-]{2,31}$/i;
const doctorRegistrationPattern = /^[A-Z0-9][A-Z0-9\/._-]{2,31}$/i;
const MIN_DOCTOR_AGE = 18;
const MAX_DOCTOR_AGE = 100;

function parseDate(value: string) {
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export const bloodGroupSchema = z.enum(["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"]);

export const genderSchema = z.enum(["MALE", "FEMALE", "OTHER", "UNKNOWN"]);

export const appointmentStatusSchema = z.enum([
  "BOOKED",
  "WAITING",
  "IN_CONSULTATION",
  "COMPLETED",
  "CANCELLED",
  "NO_SHOW",
]);

export const consultationStatusSchema = z.enum(["DRAFT", "COMPLETED", "CANCELLED"]);

export function patientCode(message: string = "Enter a valid patient code.") {
  return z.preprocess(
    toOptionalUpperTrimmed,
    z.string().regex(patientCodePattern, message).optional(),
  );
}

export function doctorRegistrationNumber(message: string = "Enter a valid doctor registration number.") {
  return z.preprocess(
    toOptionalUpperTrimmed,
    z.string().regex(doctorRegistrationPattern, message).optional(),
  );
}

export function doctorDateOfBirth(message: string = "Date of birth is required.") {
  return requiredDateString(message)
    .refine((value) => {
      const parsed = parseDate(value);
      return parsed != null && parsed.getTime() <= Date.now();
    }, "Date of birth cannot be in the future.")
    .refine((value) => {
      const parsed = parseDate(value);
      if (!parsed) return false;
      const now = new Date();
      let age = now.getUTCFullYear() - parsed.getUTCFullYear();
      const monthDiff = now.getUTCMonth() - parsed.getUTCMonth();
      if (monthDiff < 0 || (monthDiff === 0 && now.getUTCDate() < parsed.getUTCDate())) {
        age -= 1;
      }
      return age >= MIN_DOCTOR_AGE && age <= MAX_DOCTOR_AGE;
    }, "Enter a valid date of birth.");
}

export const healthcareValidators = {
  patientCode,
  doctorRegistrationNumber,
  doctorDateOfBirth,
  bloodGroupSchema,
  genderSchema,
  appointmentStatusSchema,
  consultationStatusSchema,
};
