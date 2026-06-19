import { z } from "zod";

import { requiredString } from "../validators/common.js";

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

const toOptionalTrimmedString = (value: unknown) => {
  if (value == null) return null;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
};

export const dispensingActionSchema = z.enum([
  "FULL_DISPENSE",
  "PARTIAL_DISPENSE",
  "MARK_UNAVAILABLE",
  "BUY_OUTSIDE",
  "PATIENT_DECLINED",
  "UNAVAILABLE_CLOSED",
  "CANCEL_PRESCRIPTION",
  "EXPIRED",
]);

export function createDispenseQuantitySchema(options: { pendingQuantity: number; availableQuantity: number; required?: boolean }) {
  return z.preprocess(
    toOptionalNumber,
    z.number().int("Dispense quantity must be a whole number.")
      .min(1, "Dispense quantity must be at least 1.")
      .max(Math.max(1, options.pendingQuantity), "Dispense quantity cannot exceed pending quantity.")
      .max(Math.max(1, options.availableQuantity), "Dispense quantity cannot exceed available stock.")
      .nullable(),
  ).superRefine((value, ctx) => {
    if (options.required && (value == null || value === null)) {
      ctx.addIssue({ code: "custom", message: "Dispense quantity is required." });
    }
  });
}

export const closureReasonSchema = z.preprocess(
  toOptionalTrimmedString,
  z.string().min(1, "Closure reason is required.").max(60, "Closure reason must be 60 characters or fewer."),
);
export const remarksSchema = z.preprocess(toOptionalTrimmedString, z.string().max(250, "Remarks must be 250 characters or fewer.").nullable());
export const batchOverrideSchema = z.preprocess(toOptionalTrimmedString, z.string().max(60, "Batch override must be 60 characters or fewer.").nullable());

export function createDispenseActionInputSchema(options: {
  pendingQuantity: number;
  availableQuantity: number;
  action: z.infer<typeof dispensingActionSchema>;
}) {
  return z.object({
    action: dispensingActionSchema,
    quantity: z.preprocess(toOptionalNumber, z.number().int("Dispense quantity must be a whole number.").positive("Dispense quantity must be at least 1.").nullable()),
    batchOverride: batchOverrideSchema,
    reason: z.preprocess(toOptionalTrimmedString, z.string().max(60, "Closure reason must be 60 characters or fewer.").nullable()),
    remarks: remarksSchema,
  }).superRefine((value, ctx) => {
    const isClosure = value.action !== "FULL_DISPENSE" && value.action !== "PARTIAL_DISPENSE";
    if (isClosure) {
      if (!value.reason) {
        ctx.addIssue({ code: "custom", path: ["reason"], message: "Closure reason is required." });
      }
      return;
    }

    if (value.action === "PARTIAL_DISPENSE" || value.action === "FULL_DISPENSE") {
      if (value.quantity == null) {
        ctx.addIssue({ code: "custom", path: ["quantity"], message: "Dispense quantity is required." });
      } else if (value.quantity > options.pendingQuantity) {
        ctx.addIssue({ code: "custom", path: ["quantity"], message: "Dispense quantity cannot exceed pending quantity." });
      } else if (value.quantity > options.availableQuantity) {
        ctx.addIssue({ code: "custom", path: ["quantity"], message: "Dispense quantity cannot exceed available stock." });
      }
    }
  });
}

export const dispensingLineIdentitySchema = z.object({
  prescriptionId: requiredString("Prescription ID is required."),
  medicineLineId: requiredString("Medicine line ID is required."),
  action: dispensingActionSchema,
  previousStatus: requiredString("Previous status is required."),
  newStatus: requiredString("New status is required."),
  quantity: z.number().nullable(),
  batch: z.string().nullable(),
  reason: z.string().max(60).nullable(),
  remarks: z.string().max(250).nullable(),
  user: requiredString("User is required."),
  timestamp: requiredString("Timestamp is required."),
});

export type DispensingActionInput = z.infer<ReturnType<typeof createDispenseActionInputSchema>>;
