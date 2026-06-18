import { z } from "zod";

import { optionalEmail, optionalString, requiredString } from "../validators/common.js";
import { doctorRegistrationNumber } from "../validators/healthcare.js";
import { optionalIndianMobileNumber } from "../validators/india.js";

export const doctorCreateSchema = z.object({
  doctorName: requiredString(),
  specialization: requiredString("Speciality is required."),
  mobile: optionalIndianMobileNumber(),
  email: optionalEmail(),
  registrationNumber: doctorRegistrationNumber().optional(),
  consultationRoom: optionalString(),
  qualification: optionalString(),
  consultationFee: z.coerce.number().nonnegative().optional(),
  yearsOfExperience: z.coerce.number().nonnegative().optional(),
  age: z.coerce.number().nonnegative().optional(),
  active: z.boolean().optional().default(true),
  publicListingEnabled: z.boolean().optional().default(false),
  slug: optionalString(),
});

export const doctorUpdateSchema = doctorCreateSchema.partial().extend({
  active: z.boolean().optional(),
  publicListingEnabled: z.boolean().optional(),
});

export type DoctorCreateValues = z.infer<typeof doctorCreateSchema>;
export type DoctorUpdateValues = z.infer<typeof doctorUpdateSchema>;
