import { z } from "zod";

import { dateString, optionalEmail, optionalString } from "../validators/common.js";
import { bloodGroupSchema, genderSchema, patientCode } from "../validators/healthcare.js";
import { indianMobileNumber, optionalIndianMobileNumber, indianPincode } from "../validators/india.js";

function optionalDateString() {
  return z.preprocess(
    (value) => (value == null || value === "" ? undefined : value),
    dateString().optional(),
  );
}

function optionalBloodGroup() {
  return z.preprocess(
    (value) => (value == null || value === "" ? undefined : value),
    bloodGroupSchema.optional(),
  );
}

function patientNameSchema(message: string) {
  const schema = z.string().min(1, message).refine(
    (value) => /^[A-Za-z][A-Za-z\s'-]*$/.test(value),
    "Enter first name using letters, spaces, hyphen or apostrophe only.",
  );
  return z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    schema,
  );
}

function ageYearsSchema() {
  return z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      if (typeof value === "string") {
        const trimmed = value.trim();
        if (trimmed === "") return undefined;
        const parsed = Number(trimmed);
        return Number.isNaN(parsed) ? value : parsed;
      }
      return value;
    },
    z.number().int("Enter valid age between 0 and 120.").min(0, "Enter valid age between 0 and 120.").max(120, "Enter valid age between 0 and 120.").optional(),
  );
}

function addFutureDateOfBirthIssue<T extends { dateOfBirth?: unknown }>(value: T, context: z.RefinementCtx) {
  const dateOfBirth = typeof value.dateOfBirth === "string" ? value.dateOfBirth : "";
  const today = new Date().toISOString().slice(0, 10);
  if (dateOfBirth && dateOfBirth > today) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["dateOfBirth"],
      message: "Enter date in valid format and not in the future.",
    });
  }
}

const patientBaseSchema = z.object({
  firstName: patientNameSchema("First name is required."),
  lastName: optionalString(),
  mobile: indianMobileNumber(),
  email: optionalEmail(),
  gender: genderSchema.optional(),
  dateOfBirth: optionalDateString(),
  ageYears: ageYearsSchema(),
  bloodGroup: optionalBloodGroup(),
  patientCode: patientCode().optional(),
  addressLine1: optionalString(),
  addressLine2: optionalString(),
  city: optionalString(),
  state: optionalString(),
  country: optionalString(),
  postalCode: optionalString(),
  emergencyContactName: optionalString(),
  emergencyContactMobile: optionalIndianMobileNumber(),
  notes: optionalString(),
  active: z.boolean().optional(),
});

function refineIndianPostalCode<T extends z.ZodTypeAny>(schema: T) {
  return schema.superRefine((value, context) => {
    const country = typeof value === "object" && value && "country" in value ? String((value as { country?: unknown }).country ?? "") : "";
    const postalCode = typeof value === "object" && value && "postalCode" in value ? (value as { postalCode?: unknown }).postalCode : undefined;
    if (country.trim().toLowerCase() !== "india" || !postalCode) {
      if (typeof value === "object" && value) {
        addFutureDateOfBirthIssue(value as { dateOfBirth?: unknown }, context);
      }
      return;
    }
    const parsed = indianPincode().safeParse(postalCode);
    if (!parsed.success) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["postalCode"],
        message: parsed.error.issues[0]?.message || "Enter a valid 6-digit PIN code.",
      });
    }
    addFutureDateOfBirthIssue(value as { dateOfBirth?: unknown }, context);
  });
}

export const patientRegistrationSchema = refineIndianPostalCode(patientBaseSchema);

export const patientProfileSchema = refineIndianPostalCode(patientBaseSchema.partial().extend({
  mobile: indianMobileNumber().optional(),
  emergencyContactMobile: optionalIndianMobileNumber(),
}));

export const patientQuickRegisterSchema = z.object({
  mobile: indianMobileNumber(),
  firstName: patientNameSchema("First name is required."),
  lastName: optionalString(),
  gender: genderSchema,
  dateOfBirth: optionalDateString(),
  ageYears: ageYearsSchema(),
  email: optionalEmail(),
  addressLine1: optionalString(),
  addressLine2: optionalString(),
  city: optionalString(),
  state: optionalString(),
  country: optionalString(),
  postalCode: optionalString(),
  emergencyContactName: optionalString(),
  emergencyContactMobile: optionalIndianMobileNumber(),
}).superRefine((value, context) => {
  if (!value.dateOfBirth && value.ageYears == null) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["dateOfBirth"],
      message: "Date of birth or age is required.",
    });
  }
  addFutureDateOfBirthIssue(value, context);
  if ((value.country || "").trim().toLowerCase() === "india" && value.postalCode) {
    const result = indianPincode().safeParse(value.postalCode);
    if (!result.success) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["postalCode"],
        message: result.error.issues[0]?.message || "Enter a valid 6-digit PIN code.",
      });
    }
  }
});

export const patientOtpLoginSchema = z.object({
  mobile: indianMobileNumber(),
  otp: z.string().trim().regex(/^\d{6}$/, "Enter a valid 6-digit OTP."),
});

export type PatientRegistrationValues = z.infer<typeof patientRegistrationSchema>;
export type PatientProfileValues = z.infer<typeof patientProfileSchema>;
export type PatientQuickRegisterValues = z.infer<typeof patientQuickRegisterSchema>;
export type PatientOtpLoginValues = z.infer<typeof patientOtpLoginSchema>;
