import { z } from "zod";

import { optionalIndianMobileNumber } from "../validators/india.js";

const paymentModeValues = ["CASH", "CARD", "UPI", "INSURANCE", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"] as const;

const toOptionalTrimmedString = (value: unknown) => {
  if (value == null) return null;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
};

const toRequiredTrimmedString = (value: unknown) => {
  if (typeof value !== "string") return "";
  return value.trim();
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
const hasAtMostTwoDecimals = (value: number) => Math.round(value * 100) === value * 100;

const moneyAmountSchema = z.preprocess(
  toOptionalNumber,
  z.number().min(0, "Amount must be zero or greater.").max(999999, "Amount must be 999999 or less.").refine(hasAtMostTwoDecimals, "Amount can have up to 2 decimal places."),
);

const optionalMoneyAmountSchema = z.preprocess(
  toOptionalNumber,
  z.number().min(0, "Amount must be zero or greater.").max(999999, "Amount must be 999999 or less.").refine(hasAtMostTwoDecimals, "Amount can have up to 2 decimal places.").nullable(),
);

const searchTextSchema = z.preprocess(
  toOptionalTrimmedString,
  z.string().max(60, "Search text must be 60 characters or fewer.").nullable(),
);

const walkInNameSchema = z.preprocess(
  toOptionalTrimmedString,
  z.string().max(60, "Walk-in name must be 60 characters or fewer.").refine(hasLetterOrNumber, "Walk-in name must contain at least one letter or number.").nullable(),
);

const paymentReferenceSchema = z.preprocess(
  toOptionalTrimmedString,
  z.string().max(60, "Reference must be 60 characters or fewer.").nullable(),
);

const cartLineSchema = z.object({
  medicineId: z.string().uuid("Medicine is required."),
  quantity: z.preprocess(
    toOptionalNumber,
    z.number().int("Quantity must be a whole number.").min(1, "Quantity must be greater than 0.").max(999999, "Quantity must be 999999 or less."),
  ),
  availableQuantity: z.preprocess(
    toOptionalNumber,
    z.number().int("Available quantity must be a whole number.").min(0, "Available quantity must be zero or greater.").max(999999, "Available quantity must be 999999 or less."),
  ),
  unitPrice: optionalMoneyAmountSchema,
  discount: optionalMoneyAmountSchema,
  taxRate: z.preprocess(
    toOptionalNumber,
    z.number().min(0, "Tax must be between 0 and 100.").max(100, "Tax must be between 0 and 100.").refine(hasAtMostTwoDecimals, "Tax can have up to 2 decimal places.").nullable(),
  ),
}).superRefine((line, ctx) => {
  if (line.quantity > line.availableQuantity) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["quantity"],
      message: "Requested quantity exceeds available stock.",
    });
  }
  const unitPrice = line.unitPrice ?? 0;
  const discount = line.discount ?? 0;
  const gross = unitPrice * line.quantity;
  if (discount > gross) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["discount"],
      message: "Discount cannot exceed line amount.",
    });
  }
  const lineTotal = gross - discount + ((gross - discount) * (line.taxRate ?? 0)) / 100;
  if (lineTotal < 0) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["discount"],
      message: "Line total cannot be negative.",
    });
  }
});

export const pharmacyPosPaymentModes = paymentModeValues;

export const pharmacyPosSearchSchema = searchTextSchema;

export const pharmacyPosCustomerSchema = z.object({
  patientId: z.string().uuid().nullable().optional(),
  customerName: walkInNameSchema,
  customerMobile: z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      return value;
    },
    optionalIndianMobileNumber(),
  ),
});

export const pharmacyPosPrescriptionAttachmentSchema = z.object({
  prescriptionDocumentId: z.string().uuid().nullable().optional(),
});

export const pharmacyPosCartLineSchema = cartLineSchema;

export const pharmacyPosCheckoutSchema = z.object({
  items: z.array(cartLineSchema).min(1, "Add at least one medicine to the cart."),
  patientId: z.string().uuid().nullable().optional(),
  customerName: walkInNameSchema,
  customerMobile: z.preprocess(
    (value) => {
      if (value == null || value === "") return undefined;
      return value;
    },
    optionalIndianMobileNumber(),
  ),
  grandTotal: moneyAmountSchema,
  paidAmount: optionalMoneyAmountSchema,
  paymentMode: z.enum(paymentModeValues).nullable().optional(),
  paymentReference: paymentReferenceSchema,
  prescriptionDocumentId: z.string().uuid().nullable().optional(),
  notes: z.preprocess(toOptionalTrimmedString, z.string().max(250, "Notes must be 250 characters or fewer.").nullable()),
}).superRefine((sale, ctx) => {
  if (sale.grandTotal > 0) {
    const paidAmount = sale.paidAmount ?? 0;
    if (sale.grandTotal > 0 && paidAmount <= 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["paidAmount"],
        message: "Paid amount is required when the grand total is greater than zero.",
      });
    }
    if (sale.grandTotal > 0 && paidAmount < sale.grandTotal) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["paidAmount"],
        message: "Paid amount must be equal to or greater than grand total.",
      });
    }
  }
  if ((sale.paidAmount ?? 0) > 0 && !sale.paymentMode) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["paymentMode"],
      message: "Payment mode is required when payment is entered.",
    });
  }
  if ((sale.paidAmount ?? 0) > 0 && sale.paymentMode && sale.paymentMode !== "CASH" && !sale.paymentReference) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["paymentReference"],
      message: "Reference is required for non-cash payments.",
    });
  }
});

export type PharmacyPosCartLineValues = z.infer<typeof pharmacyPosCartLineSchema>;
export type PharmacyPosCheckoutValues = z.infer<typeof pharmacyPosCheckoutSchema>;
