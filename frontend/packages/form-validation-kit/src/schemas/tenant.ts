import { z } from "zod";

import { en } from "../messages/en.js";
import { email, optionalEmail, optionalString, requiredString } from "../validators/common.js";
import { optionalIndianMobileNumber } from "../validators/india.js";

export const createTenantSchema = z.object({
  clinicName: requiredString(en.requiredTenantName),
  tenantCode: z.preprocess(
    (value: unknown) => (typeof value === "string" ? value.trim().toLowerCase() : ""),
    z
      .string()
      .min(1, en.requiredTenantCode)
      .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, "Use lowercase letters, numbers, and dashes only."),
  ),
  displayName: requiredString("Tenant name is required."),
  city: requiredString(en.requiredCity),
  state: requiredString("State is required."),
  country: requiredString(en.requiredCountry),
  postalCode: optionalString(),
  phone: optionalIndianMobileNumber(),
  clinicEmail: optionalEmail(),
  addressLine1: optionalString(),
  addressLine2: optionalString(),
  planId: requiredString("Plan is required."),
  adminEmail: email(),
  adminFirstName: requiredString("Admin first name is required."),
  adminLastName: requiredString("Admin last name is required."),
  tempPassword: optionalString(),
  modules: z.record(z.string(), z.boolean()).default({}),
  publicListingEnabled: z.boolean().optional().default(false),
});

export const updateTenantSchema = createTenantSchema.partial().extend({
  modules: z.record(z.string(), z.boolean()).optional(),
  publicListingEnabled: z.boolean().optional(),
});

export type CreateTenantFormValues = z.infer<typeof createTenantSchema>;
export type UpdateTenantFormValues = z.infer<typeof updateTenantSchema>;
