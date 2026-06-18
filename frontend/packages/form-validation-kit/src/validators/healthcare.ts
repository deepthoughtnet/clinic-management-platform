import { z } from "zod";

import { en } from "../messages/en.js";

const toOptionalUpperTrimmed = (value: unknown) => {
  if (value == null) return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed.toUpperCase();
};

const patientCodePattern = /^[A-Z0-9][A-Z0-9_-]{2,31}$/i;
const doctorRegistrationPattern = /^[A-Z0-9][A-Z0-9\/._-]{2,31}$/i;

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

export const healthcareValidators = {
  patientCode,
  doctorRegistrationNumber,
  bloodGroupSchema,
  genderSchema,
  appointmentStatusSchema,
  consultationStatusSchema,
};
