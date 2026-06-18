import { z } from "zod";

import { dateString, optionalEmail, optionalString, requiredString } from "../validators/common.js";
import { bloodGroupSchema, genderSchema, patientCode } from "../validators/healthcare.js";
import { indianMobileNumber, optionalIndianMobileNumber } from "../validators/india.js";

export const patientRegistrationSchema = z.object({
  firstName: requiredString(),
  lastName: optionalString(),
  mobile: indianMobileNumber(),
  email: optionalEmail(),
  gender: genderSchema.optional(),
  dateOfBirth: dateString().optional(),
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

export const patientProfileSchema = patientRegistrationSchema.partial().extend({
  mobile: indianMobileNumber().optional(),
  emergencyContactMobile: optionalIndianMobileNumber(),
});

export const patientOtpLoginSchema = z.object({
  mobile: indianMobileNumber(),
  otp: z.string().trim().regex(/^\d{4,8}$/, "Enter a valid OTP."),
});

export type PatientRegistrationValues = z.infer<typeof patientRegistrationSchema>;
export type PatientProfileValues = z.infer<typeof patientProfileSchema>;
export type PatientOtpLoginValues = z.infer<typeof patientOtpLoginSchema>;
