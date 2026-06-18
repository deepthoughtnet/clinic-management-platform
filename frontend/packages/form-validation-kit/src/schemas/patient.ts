import { z } from "zod";

import { dateString, optionalEmail, optionalString, requiredString, nonNegativeNumber } from "../validators/common.js";
import { bloodGroupSchema, genderSchema, patientCode } from "../validators/healthcare.js";
import { indianMobileNumber, optionalIndianMobileNumber, indianPincode } from "../validators/india.js";

function optionalDateString() {
  return z.preprocess(
    (value) => (value == null || value === "" ? undefined : value),
    dateString().optional(),
  );
}

const patientBaseSchema = z.object({
  firstName: requiredString(),
  lastName: optionalString(),
  mobile: indianMobileNumber(),
  email: optionalEmail(),
  gender: genderSchema.optional(),
  dateOfBirth: optionalDateString(),
  ageYears: nonNegativeNumber().optional(),
  bloodGroup: bloodGroupSchema.optional(),
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
  });
}

export const patientRegistrationSchema = refineIndianPostalCode(patientBaseSchema);

export const patientProfileSchema = refineIndianPostalCode(patientBaseSchema.partial().extend({
  mobile: indianMobileNumber().optional(),
  emergencyContactMobile: optionalIndianMobileNumber(),
}));

export const patientQuickRegisterSchema = z.object({
  mobile: indianMobileNumber(),
  firstName: requiredString("First name is required."),
  lastName: optionalString(),
  gender: genderSchema,
  dateOfBirth: optionalDateString(),
  ageYears: nonNegativeNumber().optional(),
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
