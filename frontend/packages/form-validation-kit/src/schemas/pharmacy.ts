import { z } from "zod";

import { nonNegativeNumber, optionalString, positiveNumber, requiredString } from "../validators/common.js";

const medicineTypeValues = ["TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "OINTMENT", "OTHER"] as const;
const timingValues = ["BEFORE_FOOD", "AFTER_FOOD", "WITH_FOOD", "ANYTIME"] as const;

const toRequiredTrimmedString = (value: unknown) => {
  if (typeof value !== "string") return "";
  return value.trim();
};

const toOptionalTrimmedString = (value: unknown) => {
  if (value == null) return null;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
};

const toOptionalNumber = (value: unknown) => {
  if (value == null || value === "") return null;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed === "") return null;
    const parsed = Number(trimmed);
    return Number.isNaN(parsed) ? value : parsed;
  }
  return value;
};

const toOptionalTiming = (value: unknown) => {
  if (value == null || value === "") return null;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
};

const hasLetterOrNumber = (value: string) => /[A-Za-z0-9]/.test(value);
const isAllowedBarcode = (value: string) => /^[A-Za-z0-9/_-]+$/.test(value);
const hasAtMostTwoDecimals = (value: number) => Math.round(value * 100) === value * 100;

const optionalText = (maxLength: number) =>
  z.preprocess(toOptionalTrimmedString, z.string().max(maxLength).nullable());

const optionalSymbolAwareText = (maxLength: number, message: string) =>
  z.preprocess(
    toOptionalTrimmedString,
    z.string().max(maxLength, message).refine(hasLetterOrNumber, message).nullable(),
  );

const requiredSymbolAwareText = (minLength: number, maxLength: number, message: string) =>
  z
    .preprocess(toRequiredTrimmedString, z.string().min(minLength, message).max(maxLength, message))
    .refine(hasLetterOrNumber, message);

const optionalBarcode = z.preprocess(
  toOptionalTrimmedString,
  z.string().max(60).refine(isAllowedBarcode, "Barcode can use letters, numbers, dashes, underscores, and slashes only.").nullable(),
);

const optionalNumber = (min: number, max: number, message: string) =>
  z.preprocess(
    toOptionalNumber,
    z
      .number()
      .min(min, message)
      .max(max, message)
      .refine(hasAtMostTwoDecimals, message)
      .nullable(),
  );

export const medicineTypeSchema = z.enum(medicineTypeValues);
export const medicineTimingSchema = z.preprocess(toOptionalTiming, z.enum(timingValues).nullable());

export const medicineMasterSchema = z.object({
  medicineName: requiredSymbolAwareText(2, 60, "Medicine name must be 2 to 60 characters and include a letter or number."),
  medicineType: medicineTypeSchema,
  barcode: optionalBarcode,
  qrCode: optionalText(60),
  externalCode: optionalText(60),
  genericName: optionalSymbolAwareText(60, "Generic name must include a letter or number."),
  brandName: optionalSymbolAwareText(60, "Brand name must include a letter or number."),
  category: optionalText(60),
  dosageForm: optionalText(60),
  strength: requiredSymbolAwareText(1, 60, "Strength is required and must include a letter or number."),
  unit: optionalText(60),
  manufacturer: optionalText(60),
  defaultDosage: optionalText(60),
  defaultFrequency: optionalText(60),
  defaultDurationDays: z.preprocess(
    toOptionalNumber,
    z.number().int("Default duration must be a whole number.").min(1, "Default duration must be between 1 and 365 days.").max(365, "Default duration must be between 1 and 365 days.").nullable(),
  ),
  defaultTiming: medicineTimingSchema,
  defaultInstructions: optionalText(250),
  defaultPrice: optionalNumber(0, 999999, "Default price must be between 0 and 999999 with up to 2 decimal places."),
  taxRate: optionalNumber(0, 100, "Tax % must be between 0 and 100 with up to 2 decimal places."),
  active: z.boolean(),
});

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

export type MedicineMasterValues = z.infer<typeof medicineMasterSchema>;
export type PharmacyPosLineValues = z.infer<typeof pharmacyPosLineSchema>;
export type PharmacyPosSaleValues = z.infer<typeof pharmacyPosSaleSchema>;

export type MedicineMasterIdentity = Pick<
  MedicineMasterValues,
  "medicineName" | "medicineType" | "strength"
>;

export type MedicineMasterDuplicateSource = {
  id?: string | null;
  medicineName: string | null;
  medicineType: MedicineMasterValues["medicineType"];
  strength: string | null;
};

export function normalizeMedicineMasterIdentity(value: MedicineMasterIdentity) {
  return {
    medicineName: value.medicineName.trim().toLowerCase(),
    medicineType: value.medicineType,
    strength: value.strength.trim().toLowerCase(),
  };
}

export function medicineMasterIdentityKey(value: MedicineMasterIdentity) {
  const normalized = normalizeMedicineMasterIdentity(value);
  return `${normalized.medicineType}|${normalized.medicineName}|${normalized.strength}`;
}

export function hasDuplicateMedicineMaster(
  candidate: MedicineMasterIdentity,
  existing: readonly MedicineMasterDuplicateSource[],
  excludeId?: string | null,
) {
  const candidateKey = medicineMasterIdentityKey(candidate);
  return existing.some((item) => {
    if (item.id === excludeId || item.medicineName == null || item.strength == null) return false;
    return medicineMasterIdentityKey({
      medicineName: item.medicineName,
      medicineType: item.medicineType,
      strength: item.strength,
    }) === candidateKey;
  });
}
