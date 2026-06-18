import { z } from "zod";

import { optionalString, requiredString, positiveNumber } from "../validators/common.js";

const paymentMethodSchema = z.enum(["CASH", "CARD", "UPI", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"]);

export const consultationFeeSchema = z.object({
  amount: positiveNumber("Amount must be greater than 0."),
  paymentMethod: paymentMethodSchema,
  invoiceNumber: optionalString(),
  notes: optionalString(),
});

export const paymentSchema = z.object({
  amount: positiveNumber("Amount must be greater than 0."),
  paymentMethod: paymentMethodSchema,
  invoiceNumber: optionalString(),
  notes: optionalString(),
});

export const invoiceSchema = z.object({
  amount: positiveNumber("Amount must be greater than 0."),
  paymentMethod: paymentMethodSchema,
  invoiceNumber: optionalString(),
  notes: optionalString(),
});

export const refundSchema = z.object({
  amount: positiveNumber("Amount must be greater than 0."),
  paymentMethod: paymentMethodSchema,
  reason: requiredString("Refund reason is required."),
  notes: optionalString(),
});

export type ConsultationFeeValues = z.infer<typeof consultationFeeSchema>;
export type PaymentValues = z.infer<typeof paymentSchema>;
export type InvoiceValues = z.infer<typeof invoiceSchema>;
export type RefundValues = z.infer<typeof refundSchema>;
