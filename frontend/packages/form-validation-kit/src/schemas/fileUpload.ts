import { z } from "zod";

import { validationMessages } from "../helpers/errorMessages.js";
import { hasAllowedExtension, hasAllowedMimeType, isWithinMaxFileSize, type FileLike } from "../validators/file.js";

export type FileUploadOptions = {
  allowedMimeTypes?: readonly string[];
  allowedExtensions?: readonly string[];
  maxBytes?: number;
  required?: boolean;
  requiredMessage?: string;
  fileTypeMessage?: string;
  fileSizeMessage?: string;
};

function isFileLike(value: unknown): value is FileLike {
  return typeof value === "object" && value !== null && "name" in value && "size" in value;
}

function createFileSchema(options: FileUploadOptions = {}) {
  return z.any().superRefine((value, context) => {
    if (value == null || value === "") {
      if (options.required) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          message: options.requiredMessage || validationMessages.required,
        });
      }
      return;
    }

    if (!isFileLike(value)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: options.requiredMessage || validationMessages.required,
      });
      return;
    }

    const mimeMatches = !options.allowedMimeTypes?.length || hasAllowedMimeType(value, options.allowedMimeTypes);
    const extensionMatches = !options.allowedExtensions?.length || hasAllowedExtension(value.name, options.allowedExtensions);

    const hasAllowedType = options.allowedMimeTypes?.length && options.allowedExtensions?.length
      ? mimeMatches || extensionMatches
      : mimeMatches && extensionMatches;

    if (!hasAllowedType) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: options.fileTypeMessage || validationMessages.invalidFileType,
      });
    }

    if (typeof options.maxBytes === "number" && !isWithinMaxFileSize(value, options.maxBytes)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: options.fileSizeMessage || validationMessages.invalidFileSize,
      });
    }
  });
}

export function fileUploadSchema(options: FileUploadOptions = {}) {
  return createFileSchema(options);
}

export function imageUploadSchema(options: FileUploadOptions = {}) {
  return createFileSchema({
    allowedMimeTypes: ["image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif"],
    allowedExtensions: ["png", "jpg", "jpeg", "webp", "gif"],
    ...options,
  });
}

export function documentUploadSchema(options: FileUploadOptions = {}) {
  return createFileSchema({
    allowedMimeTypes: ["application/pdf", "image/png", "image/jpeg", "image/jpg", "image/webp"],
    allowedExtensions: ["pdf", "png", "jpg", "jpeg", "webp"],
    ...options,
  });
}
