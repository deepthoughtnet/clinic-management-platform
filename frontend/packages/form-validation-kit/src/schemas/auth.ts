import { z } from "zod";

import { email, password, requiredString } from "../validators/common.js";
import { indianMobileNumber } from "../validators/india.js";

export const loginSchema = z.object({
  email: email(),
  password: password(),
});

export const otpRequestSchema = z.object({
  tenantCode: requiredString("Clinic code is required."),
  mobile: indianMobileNumber(),
});

export const otpVerifySchema = z.object({
  tenantCode: requiredString("Clinic code is required."),
  mobile: indianMobileNumber(),
  otp: z.string().trim().regex(/^\d{6}$/, "Enter a valid 6-digit OTP."),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type OtpRequestValues = z.infer<typeof otpRequestSchema>;
export type OtpVerifyValues = z.infer<typeof otpVerifySchema>;
