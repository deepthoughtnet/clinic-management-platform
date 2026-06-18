import { z } from "zod";

import { requiredString, optionalString, nonNegativeNumber } from "../validators/common.js";
import { fileUploadSchema } from "./fileUpload.js";

const toOptionalString = (value: unknown) => {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
};

const toOptionalInt = (value: unknown) => {
  if (value == null || value === "") return undefined;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return undefined;
    const parsed = Number(trimmed);
    return Number.isNaN(parsed) ? value : parsed;
  }
  return value;
};

export const labTestParameterSchema = z.object({
  parameterName: requiredString("Parameter name is required."),
  unit: optionalString(),
  normalRange: optionalString(),
  criticalRange: optionalString(),
  sortOrder: z.preprocess(toOptionalInt, z.number().int().min(1).optional()),
});

export const labTestMasterSchema = z.object({
  testCode: z.preprocess(toOptionalString, z.string().optional()),
  testName: requiredString("Test name is required."),
  category: requiredString("Category is required."),
  department: optionalString(),
  sampleType: optionalString(),
  unit: optionalString(),
  referenceRange: optionalString(),
  turnaroundTime: optionalString(),
  price: nonNegativeNumber(),
  active: z.boolean().optional(),
  parameters: z.array(labTestParameterSchema).optional().default([]),
});

export const labOrderCreateSchema = z.object({
  patientId: z.preprocess(toOptionalString, z.string().optional()),
  doctorId: z.preprocess(toOptionalString, z.string().optional()),
  testIds: z.array(requiredString("Test is required.")).min(1, "Select at least one lab test."),
  notes: optionalString(),
});

export const labResultComponentSchema = z.object({
  parameterName: optionalString(),
  componentName: optionalString(),
  resultValue: optionalString(),
  unit: optionalString(),
  referenceRange: optionalString(),
});

export const labResultItemSchema = z.object({
  labOrderItemId: requiredString("Lab order item is required."),
  resultValue: optionalString(),
  unit: optionalString(),
  referenceRange: optionalString(),
  componentResults: z.array(labResultComponentSchema).optional().default([]),
});

export const labResultEntrySchema = z.object({
  comments: optionalString(),
  items: z.array(labResultItemSchema).min(1, "At least one result item is required."),
});

export const labReportUploadSchema = fileUploadSchema({
  required: false,
  allowedMimeTypes: ["application/pdf", "image/png", "image/jpeg", "image/jpg", "image/webp"],
  allowedExtensions: ["pdf", "png", "jpg", "jpeg", "webp"],
  maxBytes: 15 * 1024 * 1024,
});
