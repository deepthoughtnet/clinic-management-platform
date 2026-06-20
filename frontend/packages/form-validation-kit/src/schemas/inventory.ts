import { z } from "zod";

import { dateString, enumValue, uuid } from "../validators/common.js";

const medicineTypeValues = ["TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "DROPS", "OINTMENT", "SACHET", "OTHER"] as const;
const inventoryBatchStatusValues = ["ACTIVE", "INACTIVE"] as const;
const inventoryConditionValues = ["REUSABLE", "DAMAGED", "EXPIRED", "NOT_SELLABLE"] as const;
const refundModeValues = ["CASH", "UPI", "CARD", "NO_REFUND", "ORIGINAL_PAYMENT_MODE"] as const;
const inventoryTransactionTypeValues = [
  "OPENING",
  "PURCHASE",
  "SALE",
  "ADJUSTMENT",
  "RETURN",
  "CUSTOMER_RETURN_IN",
  "CUSTOMER_RETURN_NON_SELLABLE",
  "VENDOR_RETURN_OUT",
  "WRITE_OFF",
  "EXPIRED",
  "CANCELLED_DISPENSE",
  "STOCK_IN",
  "ADJUSTMENT_IN",
  "ADJUSTMENT_OUT",
] as const;
const inventoryMovementTypeValues = [
  "STOCK_IN",
  "STOCK_ADJUSTMENT",
  "SALE",
  "DISPENSE",
  "CUSTOMER_RETURN_IN",
  "VENDOR_RETURN_OUT",
  "WRITE_OFF",
  "TRANSFER_IN",
  "TRANSFER_OUT",
  "EXPIRED_MARKED",
  "CANCELLED_REVERSAL",
] as const;

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

const toOptionalUuid = (value: unknown) => {
  if (value == null || value === "") return null;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
};

const hasLetterOrNumber = (value: string) => /[A-Za-z0-9]/.test(value);
const hasAllowedBatchCharacters = (value: string) => /^[A-Za-z0-9/_-]+$/.test(value);
const hasAllowedExternalCodeCharacters = (value: string) => /^[A-Za-z0-9/_-]+$/.test(value);
const hasAllowedPurchaseReferenceCharacters = (value: string) => /^[A-Za-z0-9/_\-\s]+$/.test(value);
const hasDigitsOnly = (value: string) => /^[0-9]+$/.test(value);
const hasAtMostTwoDecimals = (value: number) => Math.round(value * 100) === value * 100;

const optionalTrimmedString = (maxLength: number, message: string) =>
  z.preprocess(
    toOptionalTrimmedString,
    z.string().max(maxLength, message).nullable(),
  );

const optionalPatternString = (maxLength: number, pattern: (value: string) => boolean, message: string) =>
  z.preprocess(
    toOptionalTrimmedString,
    z.string().max(maxLength, message).refine((value) => pattern(value), message).nullable(),
  );

const requiredPatternString = (minLength: number, maxLength: number, pattern: (value: string) => boolean, message: string) =>
  z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    z.string().min(minLength, message).max(maxLength, message).refine((value) => pattern(value), message),
  );

const optionalInteger = (min: number, max: number, message: string) =>
  z.preprocess(
    toOptionalNumber,
    z.number().int(message).min(min, message).max(max, message).nullable(),
  );

const optionalMoney = (min: number, max: number, message: string) =>
  z.preprocess(
    toOptionalNumber,
    z
      .number()
      .min(min, message)
      .max(max, message)
      .refine(hasAtMostTwoDecimals, message)
      .nullable(),
  );

const requiredInteger = (min: number, max: number, message: string) =>
  z.preprocess(
    toOptionalNumber,
    z.number().int(message).min(min, message).max(max, message),
  );

export const inventoryMedicineTypeSchema = enumValue(medicineTypeValues);
export const inventoryBatchStatusSchema = enumValue(inventoryBatchStatusValues);
export const inventoryConditionSchema = enumValue(inventoryConditionValues);
export const inventoryRefundModeSchema = enumValue(refundModeValues);
export const inventoryMovementTypeSchema = enumValue(inventoryMovementTypeValues);
export const inventoryTransactionTypeSchema = enumValue(inventoryTransactionTypeValues);

const inventoryBatchSharedSchema = {
  medicineId: uuid("Medicine is required."),
  locationId: uuid("Location is required."),
  batchNumber: requiredPatternString(3, 30, hasAllowedBatchCharacters, "Batch number must be 3 to 30 characters using letters, numbers, dashes, underscores, or slashes."),
  expiryDate: dateString("Expiry date is required."),
  lowStockThreshold: optionalInteger(0, 999999, "Reorder level must be between 0 and 999999."),
  unitCost: optionalMoney(0, 999999, "Purchase rate must be between 0 and 999999 with up to 2 decimal places."),
  purchasePrice: optionalMoney(0, 999999, "Purchase rate must be between 0 and 999999 with up to 2 decimal places."),
  sellingPrice: optionalMoney(0, 999999, "MRP must be between 0 and 999999 with up to 2 decimal places."),
  barcode: z.preprocess(
    toOptionalTrimmedString,
    z.string().regex(/^[0-9]{8,20}$/, "Barcode must be 8 to 20 digits.").nullable(),
  ),
  qrCode: optionalTrimmedString(100, "QR code must be 100 characters or fewer."),
  externalCode: optionalPatternString(50, hasAllowedExternalCodeCharacters, "External code must be 50 characters or fewer and use letters, numbers, dashes, underscores, or slashes."),
  purchaseReferenceNumber: optionalPatternString(60, hasAllowedPurchaseReferenceCharacters, "Purchase reference must be 60 characters or fewer and can include spaces, letters, numbers, dashes, underscores, and slashes."),
  active: z.boolean(),
};

function validateBatchBase<T extends { quantityOnHand: number; sellingPrice: number | null; unitCost: number | null; purchasePrice: number | null; expiryDate: string | null; active: boolean; barcode: string | null }>(value: T, ctx: z.RefinementCtx, allowZeroQuantity: boolean) {
  if (!allowZeroQuantity && value.quantityOnHand <= 0) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["quantityOnHand"], message: "Quantity on hand must be greater than zero." });
  }
  if (allowZeroQuantity && value.quantityOnHand < 0) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["quantityOnHand"], message: "Quantity on hand cannot be negative." });
  }
  const purchaseRate = value.unitCost ?? value.purchasePrice;
  if (value.sellingPrice != null && purchaseRate != null && value.sellingPrice < purchaseRate) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["sellingPrice"], message: "MRP cannot be less than purchase rate." });
  }
  if (value.active && value.expiryDate) {
    const expiry = new Date(`${value.expiryDate}T00:00:00.000Z`);
    const today = new Date();
    const todayUtc = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
    if (Number.isNaN(expiry.getTime())) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["expiryDate"], message: "Expiry date is invalid." });
    } else if (expiry.getTime() < todayUtc) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["expiryDate"], message: "Expiry date cannot be in the past for active stock." });
    }
  }
  if (value.barcode && !hasDigitsOnly(value.barcode)) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["barcode"], message: "Barcode must contain digits only." });
  }
}

export const inventoryBatchCreateSchema = z.object({
  ...inventoryBatchSharedSchema,
  quantityOnHand: requiredInteger(1, 999999, "Quantity on hand must be a whole number between 1 and 999999."),
}).superRefine((value, ctx) => validateBatchBase(value, ctx, false));

export const inventoryBatchEditSchema = z.object({
  ...inventoryBatchSharedSchema,
  quantityOnHand: requiredInteger(0, 999999, "Quantity on hand must be a whole number between 0 and 999999."),
}).superRefine((value, ctx) => validateBatchBase(value, ctx, true));

export const inventoryBatchBaseSchema = inventoryBatchCreateSchema;

export const inventoryPhysicalCountSchema = z.object({
  stockBatchId: uuid("Batch selection is required."),
  medicineId: uuid("Medicine is required."),
  locationId: uuid("Location is required."),
  physicalQuantity: requiredInteger(0, 999999, "Physical count must be a whole number between 0 and 999999."),
  reason: optionalTrimmedString(250, "Reason must be 250 characters or fewer."),
  remarks: optionalTrimmedString(250, "Remarks must be 250 characters or fewer."),
}).superRefine((value, ctx) => {
  if (value.physicalQuantity == null || Number.isNaN(value.physicalQuantity)) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["physicalQuantity"], message: "Physical count is required." });
  }
});

export const inventoryCustomerReturnSchema = z.object({
  saleId: uuid("Completed sale is required."),
  saleLineId: uuid("Medicine line is required."),
  returnQuantity: requiredInteger(1, 999999, "Return quantity must be a whole number greater than zero."),
  condition: inventoryConditionSchema,
  refundMode: inventoryRefundModeSchema,
  reason: requiredPatternString(1, 60, hasLetterOrNumber, "Reason is required and must be 60 characters or fewer."),
  referenceNumber: optionalTrimmedString(60, "Reference number must be 60 characters or fewer."),
  notes: optionalTrimmedString(250, "Notes must be 250 characters or fewer."),
});

export const inventoryVendorReturnSchema = z.object({
  medicineId: uuid("Medicine is required."),
  stockBatchId: uuid("Batch is required."),
  returnQuantity: requiredInteger(1, 999999, "Return quantity must be a whole number greater than zero."),
  supplierReference: requiredPatternString(1, 60, hasLetterOrNumber, "Supplier / invoice reference is required and must be 60 characters or fewer."),
  reason: requiredPatternString(1, 60, hasLetterOrNumber, "Reason is required and must be 60 characters or fewer."),
  notes: optionalTrimmedString(250, "Notes must be 250 characters or fewer."),
});

export const inventoryWriteOffSchema = z.object({
  medicineId: uuid("Medicine is required."),
  stockBatchId: uuid("Batch is required."),
  writeOffQuantity: requiredInteger(1, 999999, "Write-off quantity must be a whole number greater than zero."),
  reason: requiredPatternString(1, 60, hasLetterOrNumber, "Reason is required and must be 60 characters or fewer."),
  notes: optionalTrimmedString(250, "Notes must be 250 characters or fewer."),
});

export const inventoryMovementSchema = z.object({
  medicineId: uuid("Medicine is required."),
  stockBatchId: uuid("Batch is required."),
  locationId: uuid("Location is required."),
  movementType: inventoryMovementTypeSchema,
  beforeQuantity: optionalInteger(0, 999999, "Before quantity must be between 0 and 999999."),
  afterQuantity: optionalInteger(0, 999999, "After quantity must be between 0 and 999999."),
  quantityDelta: requiredInteger(1, 999999, "Quantity delta must be greater than zero."),
  referenceType: optionalTrimmedString(64, "Reference type must be 64 characters or fewer."),
  referenceId: z.preprocess(toOptionalUuid, z.string().uuid().nullable()),
  reason: optionalTrimmedString(60, "Reason must be 60 characters or fewer."),
  notes: optionalTrimmedString(250, "Notes must be 250 characters or fewer."),
});

export const inventoryTransactionFormSchema = z.object({
  medicineId: uuid("Medicine is required."),
  stockBatchId: z.preprocess(toOptionalUuid, z.string().uuid().nullable()),
  transactionType: inventoryTransactionTypeSchema,
  quantity: requiredInteger(1, 999999, "Quantity must be a whole number greater than zero."),
  referenceType: optionalTrimmedString(64, "Reference type must be 64 characters or fewer."),
  referenceId: z.preprocess(toOptionalUuid, z.string().uuid().nullable()),
  notes: optionalTrimmedString(250, "Notes must be 250 characters or fewer."),
}).superRefine((value, ctx) => {
  if (["ADJUSTMENT", "ADJUSTMENT_IN", "ADJUSTMENT_OUT", "CUSTOMER_RETURN_IN", "CUSTOMER_RETURN_NON_SELLABLE", "VENDOR_RETURN_OUT", "WRITE_OFF"].includes(value.transactionType) && !value.notes) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["notes"], message: "Reason is required for this stock movement." });
  }
});

export type InventoryBatchCreateValues = z.infer<typeof inventoryBatchCreateSchema>;
export type InventoryBatchEditValues = z.infer<typeof inventoryBatchEditSchema>;
export type InventoryPhysicalCountValues = z.infer<typeof inventoryPhysicalCountSchema>;
export type InventoryCustomerReturnValues = z.infer<typeof inventoryCustomerReturnSchema>;
export type InventoryVendorReturnValues = z.infer<typeof inventoryVendorReturnSchema>;
export type InventoryWriteOffValues = z.infer<typeof inventoryWriteOffSchema>;
export type InventoryMovementValues = z.infer<typeof inventoryMovementSchema>;
export type InventoryTransactionFormValues = z.infer<typeof inventoryTransactionFormSchema>;

export function inventoryBatchKey(input: { medicineId: string; locationId: string; batchNumber: string }) {
  return [input.medicineId.trim(), input.locationId.trim(), input.batchNumber.trim().toUpperCase()].join("|");
}
