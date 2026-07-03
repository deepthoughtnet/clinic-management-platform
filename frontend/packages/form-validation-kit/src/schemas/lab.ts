import { z } from "zod";

import { moneyAmount } from "../validators/finance.js";
import {
  labCategoryValues,
  labOptionalCodeText,
  labOptionalIntegerText,
  labOptionalNamedText,
  labOptionalPlainText,
  labOrderOriginValues,
  labOrderStatusValues,
  labRequiredNamedText,
  labRequiredPositiveMoney,
  labReviewDecisionValues,
} from "../validators/lab.js";
import { fileUploadSchema } from "./fileUpload.js";

const toOptionalString = (value: unknown) => {
  if (value == null) return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

const toOptionalNumber = (value: unknown) => {
  if (value == null || value === "") return undefined;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed === "") return undefined;
    const parsed = Number(trimmed);
    return Number.isNaN(parsed) ? value : parsed;
  }
  return value;
};

const toOptionalUuid = (value: unknown) => {
  if (value == null || value === "") return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

const optionalUuid = (message: string) => z.preprocess(toOptionalUuid, z.string().uuid(message).optional());
const requiredUuid = (message: string) => z.preprocess(
  (value) => (typeof value === "string" ? value.trim() : ""),
  z.string().uuid(message),
);

export const labTestParameterSchema = z.object({
  parameterName: labRequiredNamedText(60, "Parameter name is required."),
  unit: labOptionalPlainText(30, "Unit must be 30 characters or fewer."),
  normalRange: labOptionalPlainText(120, "Reference range must be 120 characters or fewer."),
  criticalRange: labOptionalPlainText(120, "Critical range must be 120 characters or fewer."),
  sortOrder: z.preprocess(toOptionalNumber, z.number().int().min(1).optional()),
});

export const labTestMasterSchema = z.object({
  testCode: labOptionalCodeText(30, "Test code must be 30 characters or fewer and may contain letters, numbers, dash, slash, or underscore."),
  testName: labRequiredNamedText(100, "Test name is required."),
  category: z.enum(labCategoryValues),
  department: labOptionalPlainText(60, "Department must be 60 characters or fewer."),
  sampleType: labOptionalNamedText(60, "Sample type must be 60 characters or fewer."),
  unit: labOptionalPlainText(30, "Unit must be 30 characters or fewer."),
  referenceRange: labOptionalPlainText(120, "Reference range must be 120 characters or fewer."),
  turnaroundTime: labOptionalIntegerText(999, "Turnaround time must be a whole number between 0 and 999."),
  price: labRequiredPositiveMoney(999999, "Price is required and must be zero or greater."),
  active: z.boolean().optional(),
  parameters: z.array(labTestParameterSchema).optional().default([]),
}).superRefine((value, ctx) => {
  const normalizedNames = new Set<string>();
  value.parameters.forEach((parameter, index) => {
    const key = parameter.parameterName.trim().toLowerCase();
    if (normalizedNames.has(key)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["parameters", index, "parameterName"],
        message: "Parameter names must be unique within a test.",
      });
    }
    normalizedNames.add(key);
  });
});

export const labOrderCreateSchema = z.object({
  patientId: requiredUuid("Patient is required."),
  orderOrigin: z.enum(labOrderOriginValues),
  requestedByInternalDoctorId: optionalUuid("Doctor must be a valid identifier."),
  externalDoctorName: labOptionalNamedText(256, "External doctor name must be 256 characters or fewer."),
  externalDoctorMobile: labOptionalPlainText(32, "External doctor mobile must be 32 characters or fewer."),
  externalClinicName: labOptionalPlainText(256, "External clinic name must be 256 characters or fewer."),
  referralSource: labOptionalNamedText(128, "Referral source must be 128 characters or fewer."),
  testIds: z.array(requiredUuid("Test is required.")).min(1, "Select at least one lab test."),
  notes: labOptionalPlainText(250, "Notes must be 250 characters or fewer."),
}).superRefine((value, ctx) => {
  if (value.orderOrigin === "DOCTOR_REFERRAL" && !value.externalDoctorName) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["externalDoctorName"],
      message: "External doctor name is required for doctor referral orders.",
    });
  }
});

export const labConsultationOrderCreateSchema = z.object({
  patientId: requiredUuid("Patient is required."),
  testIds: z.array(requiredUuid("Test is required.")).min(1, "Select at least one lab test."),
  notes: labOptionalPlainText(250, "Notes must be 250 characters or fewer."),
});

export const labResultComponentSchema = z.object({
  parameterName: labOptionalNamedText(60, "Parameter name must be 60 characters or fewer."),
  componentName: labOptionalNamedText(60, "Component name must be 60 characters or fewer."),
  resultValue: labOptionalPlainText(120, "Result value must be 120 characters or fewer."),
  unit: labOptionalPlainText(30, "Unit must be 30 characters or fewer."),
  referenceRange: labOptionalPlainText(120, "Reference range must be 120 characters or fewer."),
});

export const labResultItemSchema = z.object({
  labOrderItemId: requiredUuid("Lab order item is required."),
  resultValue: labOptionalPlainText(120, "Result value must be 120 characters or fewer."),
  unit: labOptionalPlainText(30, "Unit must be 30 characters or fewer."),
  referenceRange: labOptionalPlainText(120, "Reference range must be 120 characters or fewer."),
  componentResults: z.array(labResultComponentSchema).optional().default([]),
}).superRefine((value, ctx) => {
  const hasComponents = value.componentResults.length > 0;
  const hasResultValue = String(value.resultValue || "").trim().length > 0;
  if (!hasComponents && !hasResultValue) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["resultValue"],
      message: "Result value is required.",
    });
  }
  value.componentResults.forEach((component, index) => {
    const componentHasValue = String(component.resultValue || "").trim().length > 0;
    if (!componentHasValue) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["componentResults", index, "resultValue"],
        message: "Result value is required.",
      });
    }
  });
});

export const labResultEntrySchema = z.object({
  comments: labOptionalPlainText(250, "Remarks must be 250 characters or fewer."),
  items: z.array(labResultItemSchema).min(1, "At least one result item is required."),
});

export const labDoctorReviewSchema = z.object({
  decision: z.enum(labReviewDecisionValues),
  reason: labOptionalNamedText(60, "Reason must be 60 characters or fewer."),
  remarks: labOptionalPlainText(250, "Remarks must be 250 characters or fewer."),
}).superRefine((value, ctx) => {
  if (value.decision === "SEND_BACK") {
    if (!value.reason) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["reason"],
        message: "Reason is required when sending back a result.",
      });
    }
    if (!value.remarks) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["remarks"],
        message: "Remarks are required when sending back a result.",
      });
    }
  }
});

export const labPaymentModeValues = ["CASH", "CARD", "UPI", "INSURANCE", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"] as const;
export const labOrderFilterStatusValues = ["ALL", ...labOrderStatusValues] as const;

export const labOrderFilterSchema = z.object({
  search: labOptionalPlainText(60, "Search text must be 60 characters or fewer."),
  status: z.enum(labOrderFilterStatusValues).optional().default("ALL"),
});

export const labPaymentSchema = z.object({
  amount: labRequiredPositiveMoney(999999, "Payment amount must be greater than 0."),
  paymentMode: z.enum(labPaymentModeValues),
  referenceNumber: labOptionalPlainText(60, "Reference number must be 60 characters or fewer."),
  notes: labOptionalPlainText(250, "Notes must be 250 characters or fewer."),
}).superRefine((value, ctx) => {
  if (value.paymentMode !== "CASH" && !value.referenceNumber) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["referenceNumber"],
      message: "Reference number is required for non-cash payments.",
    });
  }
});

export const labReportUploadSchema = fileUploadSchema({
  required: false,
  allowedMimeTypes: ["application/pdf", "image/png", "image/jpeg", "image/jpg"],
  allowedExtensions: ["pdf", "png", "jpg", "jpeg"],
  maxBytes: 10 * 1024 * 1024,
});

export const labManualReportUploadSchema = labReportUploadSchema;

export const labSampleCollectionSchema = z.object({
  sampleType: labOptionalNamedText(60, "Sample type must be 60 characters or fewer."),
  collectedBy: labOptionalNamedText(60, "Collected by must be 60 characters or fewer."),
  collectedAt: z.preprocess(
    toOptionalString,
    z.string().min(1, "Collection date/time is required."),
  ),
  notes: labOptionalPlainText(250, "Notes must be 250 characters or fewer."),
});

export const labLabTestCatalogSearchSchema = z.object({
  search: labOptionalPlainText(60, "Search text must be 60 characters or fewer."),
});

export const labTestPriceSchema = moneyAmount(999999, "Price must be zero or greater.");

export type LabTestParameterValues = z.infer<typeof labTestParameterSchema>;
export type LabTestMasterValues = z.infer<typeof labTestMasterSchema>;
export type LabOrderCreateValues = z.infer<typeof labOrderCreateSchema>;
export type LabResultEntryValues = z.infer<typeof labResultEntrySchema>;
export type LabDoctorReviewValues = z.infer<typeof labDoctorReviewSchema>;
