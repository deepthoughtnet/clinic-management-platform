import { z } from "zod";

import { optionalString, requiredString } from "../validators/common.js";
import { doctorDateOfBirth } from "../validators/healthcare.js";
import { requiredIndianMobileNumber } from "../validators/india.js";

const MAX_QUALIFICATION_LENGTH = 256;
const MAX_CONSULTATION_ROOM_LENGTH = 128;
const MAX_SLUG_LENGTH = 192;
const MAX_REGISTRATION_NUMBER_LENGTH = 128;
const MAX_FEE = 9999999.99;
const MAX_EXPERIENCE_YEARS = 80;
const MAX_DOCTOR_AGE_YEARS = 100;
const PUBLIC_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const REGISTRATION_NUMBER_PATTERN = /^[A-Z0-9][A-Z0-9\/._-]{2,127}$/i;

const toRequiredNumber = (value: unknown) => {
  if (value == null) return undefined;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return undefined;
    const parsed = Number(trimmed);
    return Number.isNaN(parsed) ? value : parsed;
  }
  return value;
};

const toRequiredText = (value: unknown) => {
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

function moneyField(requiredMessage: string, invalidMessage: string) {
  return z.preprocess(
    toRequiredNumber,
    z.number({
      error: (issue) => (issue.input === undefined ? requiredMessage : invalidMessage),
    })
      .min(0, invalidMessage)
      .max(MAX_FEE, invalidMessage)
      .refine((value) => Math.round(value * 100) === value * 100, invalidMessage),
  );
}

function optionalMoneyField(invalidMessage: string) {
  return z.preprocess(
    toOptionalNumber,
    z.number({ error: invalidMessage })
      .min(0, invalidMessage)
      .max(MAX_FEE, invalidMessage)
      .refine((value) => Math.round(value * 100) === value * 100, invalidMessage)
      .optional(),
  );
}

function requiredWholeNumber(requiredMessage: string, invalidMessage: string) {
  return z.preprocess(
    toRequiredNumber,
    z.number({
      error: (issue) => (issue.input === undefined ? requiredMessage : invalidMessage),
    })
      .int(invalidMessage)
      .min(0, invalidMessage)
      .max(MAX_EXPERIENCE_YEARS, invalidMessage),
  );
}

function optionalPositiveWholeNumber(invalidMessage: string) {
  return z.preprocess(
    toOptionalNumber,
    z.number({ error: invalidMessage })
      .int(invalidMessage)
      .min(0, invalidMessage)
      .max(MAX_EXPERIENCE_YEARS, invalidMessage)
      .optional(),
  );
}

function requiredRegistrationNumber(requiredMessage: string, invalidMessage: string) {
  return z.preprocess(
    toRequiredText,
    z.string()
      .min(1, requiredMessage)
      .max(MAX_REGISTRATION_NUMBER_LENGTH, invalidMessage)
      .regex(REGISTRATION_NUMBER_PATTERN, invalidMessage),
  );
}

const optionalPublicSlug = z.preprocess((value) => {
  if (value == null) return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim().toLowerCase();
  return trimmed === "" ? undefined : trimmed;
}, z.string()
  .max(MAX_SLUG_LENGTH, `Public slug must be ${MAX_SLUG_LENGTH} characters or fewer.`)
  .regex(PUBLIC_SLUG_PATTERN, "Enter a valid public slug.")
  .optional());

const doctorBaseSchema = z.object({
  doctorName: optionalString(),
  specialization: optionalString(),
  specializations: z.array(requiredString("Specialization is required.")).min(1, "Select at least one specialization.").max(10),
  mobile: requiredIndianMobileNumber("Mobile number is required."),
  email: optionalString(),
  registrationNumber: requiredRegistrationNumber("Registration number is required.", "Enter a valid doctor registration number."),
  consultationRoom: optionalString().refine((value) => value == null || value.length <= MAX_CONSULTATION_ROOM_LENGTH, `Consultation room must be ${MAX_CONSULTATION_ROOM_LENGTH} characters or fewer.`),
  qualification: z.preprocess(
    toRequiredText,
    z.string()
      .min(1, "Qualification is required.")
      .max(MAX_QUALIFICATION_LENGTH, `Qualification must be ${MAX_QUALIFICATION_LENGTH} characters or fewer.`),
  ),
  consultationFee: optionalMoneyField("Enter a valid consultation fee."),
  opdFee: moneyField("OPD fee is required.", "Enter a valid OPD fee."),
  followUpFee: moneyField("Follow-up fee is required.", "Enter a valid follow-up fee."),
  emergencyFee: moneyField("Emergency fee is required.", "Enter a valid emergency fee."),
  yearsOfExperience: requiredWholeNumber("Years of experience is required.", "Enter a valid number of years of experience."),
  age: optionalPositiveWholeNumber("Enter a valid age."),
  dateOfBirth: doctorDateOfBirth("Date of birth is required."),
  active: z.boolean().optional().default(true),
  publicListingEnabled: z.boolean().optional().default(false),
  slug: optionalPublicSlug,
}).superRefine((value, ctx) => {
  if (!value.specializations.length) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["specializations"], message: "Select at least one specialization." });
  }
  if (value.dateOfBirth) {
    const dob = new Date(`${value.dateOfBirth}T00:00:00.000Z`);
    if (!Number.isNaN(dob.getTime())) {
      const now = new Date();
      let age = now.getUTCFullYear() - dob.getUTCFullYear();
      const monthDiff = now.getUTCMonth() - dob.getUTCMonth();
      if (monthDiff < 0 || (monthDiff === 0 && now.getUTCDate() < dob.getUTCDate())) {
        age -= 1;
      }
      if (age < 18 || age > MAX_DOCTOR_AGE_YEARS) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["dateOfBirth"], message: "Enter a valid date of birth." });
      }
      if (value.yearsOfExperience != null && value.yearsOfExperience > Math.max(0, age - 18)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["yearsOfExperience"],
          message: "Years of experience cannot exceed the doctor's possible professional experience based on date of birth.",
        });
      }
    }
  }
});

export const doctorCreateSchema = doctorBaseSchema;
export const doctorUpdateSchema = doctorBaseSchema;

export type DoctorCreateValues = z.infer<typeof doctorCreateSchema>;
export type DoctorUpdateValues = z.infer<typeof doctorUpdateSchema>;
