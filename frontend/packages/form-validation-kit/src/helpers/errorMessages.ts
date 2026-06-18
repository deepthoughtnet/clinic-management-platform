import type { ZodError } from "zod";

import { en } from "../messages/en.js";

export const validationMessages = {
  ...en,
} as const;

export function firstZodError(error: ZodError) {
  return error.issues[0]?.message || validationMessages.required;
}

export function mapZodErrors(error: ZodError) {
  const mapped: Record<string, string> = {};
  for (const issue of error.issues) {
    const key = issue.path.length ? issue.path.join(".") : "_form";
    if (!mapped[key]) {
      mapped[key] = issue.message;
    }
  }
  return mapped;
}

export function getFieldError(
  error: ZodError | Record<string, string> | null | undefined,
  fieldPath: string,
) {
  if (!error) return undefined;
  if (typeof error === "object" && "issues" in error) {
    return mapZodErrors(error as ZodError)[fieldPath];
  }
  return error[fieldPath];
}
