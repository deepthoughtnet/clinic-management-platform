import { z } from "zod";

import { optionalEmail, optionalString, requiredString } from "../validators/common.js";
import { optionalIndianMobileNumber, indianPincode } from "../validators/india.js";

const optionalTextField = optionalString();

function isIndiaCountry(country?: string | null) {
  return (country || "").trim().toLowerCase() === "india";
}

export const clinicProfileSchema = z
  .object({
    clinicName: requiredString("Clinic name is required."),
    displayName: optionalTextField,
    phone: optionalIndianMobileNumber(),
    email: optionalEmail(),
    addressLine1: optionalTextField,
    addressLine2: optionalTextField,
    city: optionalTextField,
    state: optionalTextField,
    country: optionalTextField,
    postalCode: optionalTextField,
    registrationNumber: optionalTextField,
    gstNumber: optionalTextField,
    logoDocumentId: optionalTextField,
    active: z.boolean().optional(),
    publicListingEnabled: z.boolean().optional(),
    slug: optionalTextField,
  })
  .superRefine((value, context) => {
    if (isIndiaCountry(value.country) && value.postalCode) {
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

export type ClinicProfileValues = z.infer<typeof clinicProfileSchema>;
