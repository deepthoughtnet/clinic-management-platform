import { z } from "zod";

import { nonNegativeNumber, optionalString, positiveNumber, requiredString } from "../validators/common.js";

export const pharmacyPosLineSchema = z.object({
  medicine: requiredString("Medicine is required."),
  quantity: positiveNumber("Quantity must be greater than 0."),
  unitPrice: nonNegativeNumber("Unit price must be zero or greater."),
  discount: nonNegativeNumber("Discount must be zero or greater."),
});

export const pharmacyPosSaleSchema = z.object({
  items: z.array(pharmacyPosLineSchema).min(1, "Add at least one medicine."),
  notes: optionalString(),
});

export type PharmacyPosLineValues = z.infer<typeof pharmacyPosLineSchema>;
export type PharmacyPosSaleValues = z.infer<typeof pharmacyPosSaleSchema>;
