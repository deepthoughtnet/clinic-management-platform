import { z } from "zod";

import {
  billingDateValue,
  billingDiscountTypeValues,
  billingLineTypeValues,
  billingPaymentModeValues,
  billingSourceValues,
  moneyAmount,
  optionalTrimmedText,
  percentage,
  requiredDateString,
  trimmedText,
  wholeNumber,
} from "../validators/finance.js";

const isNonFutureDate = (value: string) => billingDateValue(value) <= Date.now();

const billLineSchema = z.object({
  item: trimmedText(100, "Item is required and must contain a letter or number."),
  type: z.enum(billingLineTypeValues),
  quantity: wholeNumber(1, 999999, "Quantity must be a whole number between 1 and 999999."),
  unit: moneyAmount(999999, "Unit must be between 0 and 999999 with up to 2 decimals."),
  discount: z.preprocess(
    (value) => (value == null || value === "" ? undefined : value),
    z.preprocess(
      (value) => {
        if (value == null || value === "") return undefined;
        if (typeof value === "string") {
          const trimmed = value.trim();
          if (trimmed === "") return undefined;
          const parsed = Number(trimmed);
          return Number.isNaN(parsed) ? value : parsed;
        }
        return value;
      },
      z.number().min(0, "Discount must be zero or greater.").max(999999, "Discount must be 999999 or less.").refine((value) => Math.round(value * 100) === value * 100, "Discount can have up to 2 decimals.").optional(),
    ),
  ),
  tax: z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      if (typeof value === "string") {
        const trimmed = value.trim();
        if (trimmed === "") return undefined;
        const parsed = Number(trimmed);
        return Number.isNaN(parsed) ? value : parsed;
      }
      return value;
    },
    z.number().min(0, "Tax must be between 0 and 100.").max(100, "Tax must be between 0 and 100.").refine((value) => Math.round(value * 100) === value * 100, "Tax can have up to 2 decimals.").optional(),
  ),
});

export const billingBillLineSchema = billLineSchema.superRefine((line, ctx) => {
  const gross = line.quantity * line.unit;
  const discount = line.discount ?? 0;
  if (discount > gross) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["discount"],
      message: "Discount cannot exceed line subtotal.",
    });
  }
  const tax = line.tax ?? 0;
  const total = gross - discount + tax;
  if (total < 0) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["tax"],
      message: "Line total cannot be negative.",
    });
  }
});

export const billingBillDraftSchema = z.object({
  patientId: z.string().uuid("Patient is required."),
  billDate: requiredDateString("Bill date is required.").refine(isNonFutureDate, "Bill date cannot be in the future."),
  source: z.enum(billingSourceValues),
  discountType: z.enum(billingDiscountTypeValues),
  discountValue: z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      if (typeof value === "string") {
        const trimmed = value.trim();
        if (trimmed === "") return undefined;
        const parsed = Number(trimmed);
        return Number.isNaN(parsed) ? value : parsed;
      }
      return value;
    },
    z.number().min(0, "Discount value must be zero or greater.").max(999999, "Discount value must be 999999 or less.").refine((value) => Math.round(value * 100) === value * 100, "Discount value can have up to 2 decimals.").optional(),
  ),
  discountReason: optionalTrimmedText(60, "Discount reason must be 60 characters or fewer."),
  consultationId: optionalTrimmedText(60, "Consultation ID must be 60 characters or fewer."),
  appointmentId: optionalTrimmedText(60, "Appointment ID must be 60 characters or fewer."),
  notes: optionalTrimmedText(250, "Notes must be 250 characters or fewer."),
  lines: z.array(billingBillLineSchema).min(1, "Add at least one bill line."),
}).superRefine((value, ctx) => {
  const subtotal = value.lines.reduce((sum, line) => sum + (line.quantity * line.unit), 0);
  const discountValue = value.discountValue ?? 0;
  if (value.discountType !== "NONE" && value.discountValue == null) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["discountValue"],
      message: "Discount value is required when a discount type is selected.",
    });
  }
  if (value.discountType === "NONE" && discountValue > 0) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["discountValue"],
      message: "Discount value must be blank or zero when discount type is NONE.",
    });
  }
  if (value.discountType === "FLAT") {
    if (discountValue > subtotal) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["discountValue"],
        message: "Flat discount cannot exceed subtotal.",
      });
    }
  }
  if (value.discountType === "PERCENT" && (discountValue < 0 || discountValue > 100)) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["discountValue"],
      message: "Percentage discount must be between 0 and 100.",
    });
  }
  if (value.discountType !== "NONE" && discountValue > 0 && !value.discountReason) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["discountReason"],
      message: "Discount reason is required when discount is applied.",
    });
  }
});

export const billingCreatePaymentSchema = z.object({
  paymentAmount: moneyAmount(999999, "Payment amount must be greater than 0.", false),
  paymentMode: z.enum(billingPaymentModeValues),
  referenceNumber: optionalTrimmedText(60, "Reference number must be 60 characters or fewer."),
  notes: optionalTrimmedText(250, "Notes must be 250 characters or fewer."),
}).superRefine((value, ctx) => {
  if (value.paymentMode !== "CASH" && !value.referenceNumber) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["referenceNumber"],
      message: "Reference number is required for non-cash payments.",
    });
  }
});

export const billingRefundSchema = z.object({
  amount: moneyAmount(999999, "Refund amount must be greater than 0.", false),
  refundMode: z.enum(billingPaymentModeValues),
  reason: trimmedText(100, "Refund reason is required."),
  notes: optionalTrimmedText(250, "Notes must be 250 characters or fewer."),
});

export const billingLedgerFilterSchema = z.object({
  search: optionalTrimmedText(60, "Search text must be 60 characters or fewer."),
  status: optionalTrimmedText(40, "Status must be 40 characters or fewer."),
  fromDate: requiredDateString("From date is required.").optional(),
  toDate: requiredDateString("To date is required.").optional(),
  paymentMode: optionalTrimmedText(20, "Payment mode must be 20 characters or fewer."),
}).superRefine((value, ctx) => {
  if (value.fromDate && value.toDate && billingDateValue(value.fromDate) > billingDateValue(value.toDate)) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["toDate"],
      message: "From date cannot be after To date.",
    });
  }
});

export const billingConsultationFeeSchema = z.object({
  consultantId: z.string().uuid("Consultant is required."),
  fee: moneyAmount(999999, "Fee must be greater than 0.", false),
});

const legacyPaymentMethodSchema = z.enum(billingPaymentModeValues);

export const consultationFeeSchema = z.object({
  amount: moneyAmount(999999, "Amount must be greater than 0.", false),
  paymentMethod: legacyPaymentMethodSchema,
  invoiceNumber: optionalTrimmedText(128, "Reference number must be 128 characters or fewer."),
  notes: optionalTrimmedText(250, "Notes must be 250 characters or fewer."),
});

export const paymentSchema = z.object({
  amount: moneyAmount(999999, "Amount must be greater than 0.", false),
  paymentMethod: legacyPaymentMethodSchema,
  invoiceNumber: optionalTrimmedText(128, "Reference number must be 128 characters or fewer."),
  notes: optionalTrimmedText(250, "Notes must be 250 characters or fewer."),
});

export const invoiceSchema = z.object({
  amount: moneyAmount(999999, "Amount must be greater than 0.", false),
  paymentMethod: legacyPaymentMethodSchema,
  invoiceNumber: optionalTrimmedText(128, "Reference number must be 128 characters or fewer."),
  notes: optionalTrimmedText(250, "Notes must be 250 characters or fewer."),
});

export const refundSchema = z.object({
  amount: moneyAmount(999999, "Amount must be greater than 0.", false),
  paymentMethod: legacyPaymentMethodSchema,
  reason: trimmedText(100, "Refund reason is required."),
  notes: optionalTrimmedText(250, "Notes must be 250 characters or fewer."),
});

export type BillingBillLineValues = z.infer<typeof billingBillLineSchema>;
export type BillingBillDraftValues = z.infer<typeof billingBillDraftSchema>;
export type BillingCreatePaymentValues = z.infer<typeof billingCreatePaymentSchema>;
export type BillingRefundValues = z.infer<typeof billingRefundSchema>;
export type BillingLedgerFilterValues = z.infer<typeof billingLedgerFilterSchema>;
