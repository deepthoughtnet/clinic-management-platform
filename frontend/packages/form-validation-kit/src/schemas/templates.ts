import { z } from "zod";

import { optionalString } from "../validators/common.js";

export const adminTemplateTypes = ["CAMPAIGN", "REMINDER", "WEBINAR", "BILLING", "LEAD", "NOTIFICATION", "AI_PROMPT", "GENERAL"] as const;
export const adminTemplateChannels = ["EMAIL", "SMS", "WHATSAPP", "INTERNAL", "VOICE"] as const;
export const adminTemplateCategories = ["APPOINTMENT_REMINDER", "REFILL_REMINDER", "BILLING", "WEBINAR", "FOLLOW_UP", "LEAD", "VACCINATION", "WELLNESS", "GENERAL"] as const;

const CONTROL_CHAR_RE = /[\u0000-\u001F\u007F]/;
const MAX_NAME_LENGTH = 140;
const MAX_DESCRIPTION_LENGTH = 512;
const MAX_SUBJECT_LENGTH = 300;
const MAX_BODY_LENGTH = 10000;
const MAX_VARIABLES_JSON_LENGTH = 10000;

function toTrimmedRequired(value: unknown) {
  if (typeof value !== "string") return "";
  return value.trim();
}

function toTrimmedOptional(value: unknown) {
  if (value == null) return undefined;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
}

function requiredText(label: string, maxLength: number) {
  return z.preprocess(
    toTrimmedRequired,
    z
      .string()
      .min(1, `${label} is required.`)
      .max(maxLength, `${label} must be ${maxLength} characters or fewer.`)
      .refine((value) => !CONTROL_CHAR_RE.test(value), `${label} must not contain control characters.`),
  );
}

function requiredMultilineText(label: string, maxLength: number) {
  return z.preprocess(
    toTrimmedRequired,
    z
      .string()
      .min(1, `${label} is required.`)
      .max(maxLength, `${label} must be ${maxLength} characters or fewer.`),
  );
}

function optionalText(label: string, maxLength: number) {
  return optionalString()
    .refine((value) => value == null || value.length <= maxLength, `${label} must be ${maxLength} characters or fewer.`)
    .refine((value) => value == null || !CONTROL_CHAR_RE.test(value), `${label} must not contain control characters.`);
}

function optionalJsonObject(label: string, maxLength: number) {
  return z.preprocess(
    toTrimmedOptional,
    z.string()
      .max(maxLength, `${label} must be ${maxLength} characters or fewer.`)
      .optional(),
  ).superRefine((value, ctx) => {
    if (value == null) {
      return;
    }
    try {
      const parsed = JSON.parse(value);
      if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object") {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["variablesJson"],
          message: `${label} must be a JSON object.`,
        });
      }
    } catch {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["variablesJson"],
        message: "Enter valid JSON.",
      });
    }
  });
}

function validatePlaceholders(value: string | undefined, label: string, ctx: z.RefinementCtx) {
  if (value == null) {
    return;
  }
  let index = 0;
  while (index < value.length) {
    const open = value.indexOf("{{", index);
    const close = value.indexOf("}}", index);
    if (close >= 0 && (open < 0 || close < open)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: [label.toLowerCase()],
        message: `${label} contains an invalid placeholder.`,
      });
      return;
    }
    if (open < 0) {
      return;
    }
    const end = value.indexOf("}}", open + 2);
    if (end < 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: [label.toLowerCase()],
        message: `${label} contains an invalid placeholder.`,
      });
      return;
    }
    const token = value.substring(open + 2, end).trim();
    if (!token || !/^[A-Za-z0-9_]+$/.test(token)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: [label.toLowerCase()],
        message: `${label} contains an invalid placeholder.`,
      });
      return;
    }
    index = end + 2;
  }
}

export const adminTemplateSchema = z.object({
  name: requiredText("Template name", MAX_NAME_LENGTH),
  description: optionalText("Description", MAX_DESCRIPTION_LENGTH),
  templateType: z.enum(adminTemplateTypes),
  channel: z.enum(adminTemplateChannels),
  category: z.enum(adminTemplateCategories),
  subject: optionalText("Subject", MAX_SUBJECT_LENGTH),
  body: requiredMultilineText("Template body", MAX_BODY_LENGTH),
  variablesJson: optionalJsonObject("Variables JSON", MAX_VARIABLES_JSON_LENGTH),
  active: z.boolean().optional().default(true),
}).superRefine((value, ctx) => {
  if (value.channel === "EMAIL" && !value.subject) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["subject"],
      message: "Subject is required for email templates.",
    });
  }

  validatePlaceholders(value.subject, "Subject", ctx);
  validatePlaceholders(value.body, "Body", ctx);
});

export type AdminTemplateValues = z.infer<typeof adminTemplateSchema>;
