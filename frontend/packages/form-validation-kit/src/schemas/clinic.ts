import { z } from "zod";

import { optionalString, requiredString } from "../validators/common.js";
import { normalizeIndianMobileInput, optionalGstin, indianPincode } from "../validators/india.js";

const optionalTextField = optionalString();
const MAX_CLINIC_NAME_LENGTH = 256;
const MAX_DISPLAY_NAME_LENGTH = 256;
const MAX_EMAIL_LENGTH = 256;
const MAX_ADDRESS_LENGTH = 256;
const MAX_CITY_LENGTH = 128;
const MAX_STATE_LENGTH = 128;
const MAX_COUNTRY_LENGTH = 128;
const MAX_POSTAL_CODE_LENGTH = 32;
const MAX_REGISTRATION_NUMBER_LENGTH = 128;
const MAX_GST_LENGTH = 15;
const MAX_SLUG_LENGTH = 192;
const CONTROL_CHARACTERS = /[\u0000-\u001F\u007F]/;
const PUBLIC_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function isIndiaCountry(country?: string | null) {
  return (country || "").trim().toLowerCase() === "india";
}

function requiredBusinessString(label: string, maxLength: number) {
  return z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    z
      .string()
      .min(1, `${label} is required.`)
      .max(maxLength, `${label} must be ${maxLength} characters or fewer.`)
      .refine((value) => !CONTROL_CHARACTERS.test(value), `${label} must not contain control characters.`),
  );
}

function optionalBusinessString(maxLength: number, label: string) {
  return optionalTextField
    .refine((value) => value == null || !CONTROL_CHARACTERS.test(value), `${label} must not contain control characters.`)
    .refine((value) => value == null || value.length <= maxLength, `${label} must be ${maxLength} characters or fewer.`);
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

export const clinicProfileSchema = z
  .object({
    clinicName: requiredBusinessString("Clinic name", MAX_CLINIC_NAME_LENGTH),
    displayName: requiredBusinessString("Display name", MAX_DISPLAY_NAME_LENGTH),
    phone: z
      .preprocess(
        (value) => (typeof value === "string" ? value.trim() : ""),
        z.string().min(1, "Phone is required."),
      )
      .transform((value) => normalizeIndianMobileInput(value))
      .refine((value) => typeof value === "string" && /^[6-9]\d{9}$/.test(value), "Enter a valid 10-digit Indian mobile number."),
    email: requiredString("Email is required.")
      .pipe(z.string().email("Enter a valid email address.").max(MAX_EMAIL_LENGTH, `Email must be ${MAX_EMAIL_LENGTH} characters or fewer.`)),
    addressLine1: requiredBusinessString("Address line 1", MAX_ADDRESS_LENGTH),
    addressLine2: optionalBusinessString(MAX_ADDRESS_LENGTH, "Address line 2"),
    city: requiredBusinessString("City", MAX_CITY_LENGTH),
    state: requiredBusinessString("State", MAX_STATE_LENGTH),
    country: requiredBusinessString("Country", MAX_COUNTRY_LENGTH),
    postalCode: requiredBusinessString("Postal code", MAX_POSTAL_CODE_LENGTH),
    registrationNumber: requiredBusinessString("Registration number", MAX_REGISTRATION_NUMBER_LENGTH),
    gstNumber: optionalGstin().refine((value) => value == null || value.length <= MAX_GST_LENGTH, `GST number must be ${MAX_GST_LENGTH} characters or fewer.`),
    logoDocumentId: optionalTextField,
    active: z.boolean().optional(),
    publicListingEnabled: z.boolean().optional(),
    slug: optionalPublicSlug,
  })
  .superRefine((value, context) => {
    if (isIndiaCountry(value.country)) {
      const result = indianPincode().safeParse(value.postalCode);
      if (!result.success) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["postalCode"],
          message: result.error.issues[0]?.message || "Enter a valid 6-digit PIN code.",
        });
      }
    } else if (value.postalCode && !/^[A-Za-z0-9][A-Za-z0-9\s-]{0,31}$/.test(value.postalCode)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["postalCode"],
        message: "Enter a valid postal code.",
      });
    }
  });

export type ClinicProfileValues = z.infer<typeof clinicProfileSchema>;
