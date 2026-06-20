import { z } from "zod";

import { validationMessages } from "../helpers/errorMessages.js";
import { fileUploadSchema } from "./fileUpload.js";
import { dateString, uuid } from "../validators/common.js";
import { optionalGstin } from "../validators/india.js";
import { optionalIndianMobileNumber } from "../validators/india.js";

const SELLABLE_MEDICINE_TYPES = ["TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROPS", "DROP", "OINTMENT", "SACHET"] as const;

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

const hasLetterOrNumber = (value: string) => /[A-Za-z0-9]/.test(value);
const hasAllowedCodeCharacters = (value: string) => /^[A-Za-z0-9/_-]+$/.test(value);
const hasAllowedReferenceCharacters = (value: string) => /^[A-Za-z0-9/_\-\s]+$/.test(value);
const hasAllowedBatchCharacters = (value: string) => /^[A-Za-z0-9/_-]+$/.test(value);
const hasAtMostTwoDecimals = (value: number) => Math.round(value * 100) === value * 100;

const requiredPatternString = (minLength: number, maxLength: number, pattern: (value: string) => boolean, message: string) =>
  z.preprocess(
    toRequiredTrimmedString,
    z.string().min(minLength, message).max(maxLength, message).refine(pattern, message),
  );

const optionalPatternString = (maxLength: number, pattern: (value: string) => boolean, message: string) =>
  z.preprocess(
    toOptionalTrimmedString,
    z.string().max(maxLength, message).refine(pattern, message).nullable(),
  );

const optionalText = (maxLength: number) =>
  z.preprocess(
    toOptionalTrimmedString,
    z.string().max(maxLength, validationMessages.required).nullable(),
  );

const optionalInteger = (min: number, max: number, message: string) =>
  z.preprocess(
    toOptionalNumber,
    z.number().int(message).min(min, message).max(max, message).nullable(),
  );

const requiredInteger = (min: number, max: number, message: string) =>
  z.preprocess(
    toOptionalNumber,
    z.number().int(message).min(min, message).max(max, message),
  );

const optionalMoney = (min: number, max: number, message: string) =>
  z.preprocess(
    toOptionalNumber,
    z.number().min(min, message).max(max, message).refine(hasAtMostTwoDecimals, message).nullable(),
  );

const optionalUuid = () =>
  z.preprocess(
    toOptionalTrimmedString,
    z.string().uuid(validationMessages.invalidUuid).nullable(),
  );

const isSellableMedicineType = (medicineType: string | null | undefined) =>
  !!medicineType && SELLABLE_MEDICINE_TYPES.includes(medicineType.toUpperCase() as (typeof SELLABLE_MEDICINE_TYPES)[number]);

const todayUtc = () => {
  const now = new Date();
  return Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
};

const notFutureDateMessage = "Date cannot be in the future.";
const notPastDateMessage = "Date cannot be in the past.";

function dateNotBeforeToday(value: string | null | undefined, path: string[], ctx: z.RefinementCtx) {
  if (!value) return;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime())) return;
  if (parsed.getTime() > todayUtc()) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path, message: notFutureDateMessage });
  }
}

function dateNotAfterToday(value: string | null | undefined, path: string[], ctx: z.RefinementCtx) {
  if (!value) return;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime())) return;
  if (parsed.getTime() < todayUtc()) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path, message: notPastDateMessage });
  }
}

export const supplierSchema = z.object({
  supplierName: requiredPatternString(2, 100, hasLetterOrNumber, "Supplier name must be 2 to 100 characters and include a letter or number."),
  gstNumber: optionalGstin(),
  contactPerson: optionalPatternString(60, hasLetterOrNumber, "Contact person must include a letter or number."),
  phone: optionalIndianMobileNumber(),
  email: z.preprocess(
    toOptionalTrimmedString,
    z.string().max(120, "Email must be 120 characters or fewer.").email("Enter a valid email address.").nullable(),
  ),
  address: optionalText(250),
  active: z.boolean(),
});

export const vendorSheetUploadSchema = fileUploadSchema({
  required: false,
  allowedMimeTypes: [
    "text/csv",
    "application/csv",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/pdf",
  ],
  allowedExtensions: ["csv", "xls", "xlsx", "pdf"],
  maxBytes: 10 * 1024 * 1024,
});

export const stockInwardSchema = z.object({
  medicineId: uuid("Medicine is required."),
  supplierId: optionalUuid(),
  locationId: uuid("Location is required."),
  purchaseReferenceNumber: optionalPatternString(60, hasAllowedReferenceCharacters, "Purchase reference must be 60 characters or fewer and can include spaces, letters, numbers, dashes, underscores, and slashes."),
  batchNumber: requiredPatternString(3, 30, hasAllowedBatchCharacters, "GRN number must be 3 to 30 characters and use letters, numbers, dashes, underscores, or slashes."),
  barcode: z.preprocess(
    toOptionalTrimmedString,
    z.string().regex(/^[0-9]{8,20}$/, "Barcode must be 8 to 20 digits.").nullable(),
  ),
  qrCode: optionalText(100),
  externalCode: optionalPatternString(50, hasAllowedCodeCharacters, "External code must be 50 characters or fewer and use letters, numbers, dashes, underscores, or slashes."),
  expiryDate: dateString("Expiry date is required."),
  purchaseDate: dateString("Inward date is required."),
  quantity: requiredInteger(1, 999999, "Qty must be a whole number between 1 and 999999."),
  lowStockThreshold: optionalInteger(0, 999999, "Threshold must be between 0 and 999999."),
  unitCost: optionalMoney(0, 999999, "Unit cost must be between 0 and 999999 with up to 2 decimals."),
  sellingPrice: optionalMoney(0, 999999, "Selling price must be between 0 and 999999 with up to 2 decimals."),
}).superRefine((value, ctx) => {
  dateNotAfterToday(value.purchaseDate, ["purchaseDate"], ctx);
  dateNotBeforeToday(value.expiryDate, ["expiryDate"], ctx);
  if (value.unitCost != null && value.sellingPrice != null && value.sellingPrice < value.unitCost) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["sellingPrice"], message: "Selling price cannot be less than unit cost." });
  }
});

export const vendorReconciliationSchema = z.object({
  locationId: uuid("Location is required."),
  supplierId: optionalUuid(),
  medicineId: uuid("Primary stock medicine is required."),
  stockBatchId: uuid("Stock batch is required."),
  physicalQuantity: requiredInteger(0, 999999, "Physical qty must be a whole number between 0 and 999999."),
  reason: optionalText(250),
  sheetFile: vendorSheetUploadSchema,
}).superRefine((value, ctx) => {
  if (value.physicalQuantity == null || Number.isNaN(value.physicalQuantity)) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["physicalQuantity"], message: "Physical qty is required." });
  }
});

export const procurementLineSchema = z.object({
  medicineId: optionalUuid(),
  medicineName: requiredPatternString(1, 100, hasLetterOrNumber, "Line item name must be 1 to 100 characters and include a letter or number."),
  quantity: requiredInteger(1, 999999, "Qty must be a whole number greater than zero."),
  expectedUnitCost: optionalMoney(0, 999999, "Expected/unit cost must be between 0 and 999999 with up to 2 decimals."),
  unitCost: optionalMoney(0, 999999, "Unit cost must be between 0 and 999999 with up to 2 decimals."),
  taxPercent: optionalMoney(0, 100, "Tax % must be between 0 and 100 with up to 2 decimals."),
  batchNumber: optionalPatternString(30, hasAllowedBatchCharacters, "Batch must be 30 characters or fewer and use letters, numbers, dashes, underscores, or slashes."),
  expiryDate: z.preprocess(toOptionalTrimmedString, z.string().regex(/^\d{4}-\d{2}-\d{2}$/, validationMessages.invalidDate).nullable()),
  sellingPrice: optionalMoney(0, 999999, "Selling price must be between 0 and 999999 with up to 2 decimals."),
}).superRefine((value, ctx) => {
  if (value.expiryDate) {
    const parsed = new Date(`${value.expiryDate}T00:00:00.000Z`);
    if (!Number.isNaN(parsed.getTime()) && parsed.getTime() < todayUtc()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["expiryDate"], message: "Expiry date must be in the future." });
    }
  }
  if (value.expectedUnitCost != null && value.sellingPrice != null && value.sellingPrice < value.expectedUnitCost) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["sellingPrice"], message: "Selling price cannot be less than expected/unit cost." });
  }
});

export const purchaseOrderSchema = z.object({
  supplierId: uuid("Supplier is required."),
  poNumber: requiredPatternString(1, 60, hasAllowedReferenceCharacters, "PO number must be 60 characters or fewer and can include letters, numbers, dashes, underscores, slashes, and spaces."),
  orderDate: dateString("Order date is required."),
  expectedDeliveryDate: z.preprocess(toOptionalTrimmedString, z.string().regex(/^\d{4}-\d{2}-\d{2}$/, validationMessages.invalidDate).nullable()),
  items: z.array(procurementLineSchema).min(1, "Add at least one procurement line."),
  approvalNote: optionalText(250),
}).superRefine((value, ctx) => {
  dateNotAfterToday(value.orderDate, ["orderDate"], ctx);
  if (value.expectedDeliveryDate) {
    const order = new Date(`${value.orderDate}T00:00:00.000Z`);
    const delivery = new Date(`${value.expectedDeliveryDate}T00:00:00.000Z`);
    if (!Number.isNaN(order.getTime()) && !Number.isNaN(delivery.getTime()) && delivery.getTime() < order.getTime()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["expectedDeliveryDate"], message: "Expected delivery date must be on or after order date." });
    }
  }
});

export const supplierInvoiceSchema = z.object({
  supplierId: uuid("Supplier is required."),
  purchaseOrderId: optionalUuid(),
  invoiceNumber: requiredPatternString(1, 60, hasAllowedReferenceCharacters, "Invoice number must be 60 characters or fewer and can include letters, numbers, dashes, underscores, slashes, and spaces."),
  invoiceDate: dateString("Invoice date is required."),
  taxAmount: optionalMoney(0, 999999, "Tax amount must be between 0 and 999999 with up to 2 decimals."),
  totalAmount: optionalMoney(0, 999999, "Total amount must be between 0 and 999999 with up to 2 decimals."),
  items: z.array(procurementLineSchema).min(1, "Add at least one procurement line."),
  approvalNote: optionalText(250),
}).superRefine((value, ctx) => {
  dateNotAfterToday(value.invoiceDate, ["invoiceDate"], ctx);
});

export const goodsReceiptSchema = z.object({
  supplierId: uuid("Supplier is required."),
  purchaseOrderId: optionalUuid(),
  supplierInvoiceId: optionalUuid(),
  receiptNumber: requiredPatternString(1, 60, hasAllowedReferenceCharacters, "Receipt number must be 60 characters or fewer and can include letters, numbers, dashes, underscores, slashes, and spaces."),
  receivedAt: z.preprocess(
    toRequiredTrimmedString,
    z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/, "Received at is required."),
  ),
  locationId: uuid("Location is required."),
  items: z.array(procurementLineSchema).min(1, "Add at least one procurement line."),
  approvalNote: optionalText(250),
}).superRefine((value, ctx) => {
  const receivedAt = new Date(`${value.receivedAt}:00.000Z`);
  if (!Number.isNaN(receivedAt.getTime()) && receivedAt.getTime() > Date.now()) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["receivedAt"], message: "Received at cannot be in the future." });
  }
});

export type SupplierValues = z.infer<typeof supplierSchema>;
export type StockInwardValues = z.infer<typeof stockInwardSchema>;
export type VendorReconciliationValues = z.infer<typeof vendorReconciliationSchema>;
export type ProcurementLineValues = z.infer<typeof procurementLineSchema>;
export type PurchaseOrderValues = z.infer<typeof purchaseOrderSchema>;
export type SupplierInvoiceValues = z.infer<typeof supplierInvoiceSchema>;
export type GoodsReceiptValues = z.infer<typeof goodsReceiptSchema>;

export type SupplierDuplicateSource = { id?: string | null; supplierName: string | null };
export type PurchaseOrderDuplicateSource = { id?: string | null; poNumber: string | null; supplierId?: string | null };

function normalizeKey(value: string | null | undefined) {
  return (value || "").trim().toLowerCase();
}

export function hasDuplicateSupplierName(
  candidate: string,
  existing: readonly SupplierDuplicateSource[],
  excludeId?: string | null,
) {
  const candidateKey = normalizeKey(candidate);
  if (!candidateKey) return false;
  return existing.some((item) => {
    if (item.id === excludeId || !item.supplierName) return false;
    return normalizeKey(item.supplierName) === candidateKey;
  });
}

export function hasDuplicatePurchaseOrderNumber(
  candidate: string,
  existing: readonly PurchaseOrderDuplicateSource[],
  excludeId?: string | null,
) {
  const candidateKey = normalizeKey(candidate);
  if (!candidateKey) return false;
  return existing.some((item) => {
    if (item.id === excludeId || !item.poNumber) return false;
    return normalizeKey(item.poNumber) === candidateKey;
  });
}
