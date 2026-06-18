import { z } from "zod";

import { email, password } from "../validators/common.js";
import { indianMobileNumber } from "../validators/india.js";

export const loginSchema = z.object({
  email: email(),
  password: password(),
});

export const otpRequestSchema = z.object({
  mobile: indianMobileNumber(),
});

export const otpVerifySchema = z.object({
  mobile: indianMobileNumber(),
  otp: z.string().trim().regex(/^\d{4,8}$/, "Enter a valid OTP."),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type OtpRequestValues = z.infer<typeof otpRequestSchema>;
export type OtpVerifyValues = z.infer<typeof otpVerifySchema>;
