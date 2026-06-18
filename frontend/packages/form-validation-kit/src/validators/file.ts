import { validationMessages } from "../helpers/errorMessages.js";

export type FileLike = {
  name: string;
  size: number;
  type?: string;
};

function toExtensionValue(value: string) {
  return value.trim().replace(/^\./, "").toLowerCase();
}

export function normalizeFileName(fileName: string) {
  return fileName
    .trim()
    .replace(/[\\/]+/g, "-")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .replace(/[<>:"|?*]+/g, "")
    .replace(/\s+/g, " ")
    .replace(/^\.+/, "")
    .slice(0, 255);
}

export function getFileExtension(fileName: string) {
  const normalized = normalizeFileName(fileName);
  const index = normalized.lastIndexOf(".");
  if (index <= 0 || index === normalized.length - 1) return "";
  return normalized.slice(index + 1).toLowerCase();
}

export function hasAllowedExtension(fileName: string, allowedExtensions: readonly string[]) {
  if (!allowedExtensions.length) return true;
  const extension = getFileExtension(fileName);
  if (!extension) return false;
  return allowedExtensions.some((allowed) => toExtensionValue(allowed) === extension);
}

export function hasAllowedMimeType(file: FileLike, allowedTypes: readonly string[]) {
  if (!allowedTypes.length) return true;
  const fileType = (file.type || "").toLowerCase();
  return allowedTypes.some((allowed) => {
    const normalized = allowed.toLowerCase().trim();
    if (normalized.endsWith("/*")) {
      return fileType.startsWith(normalized.slice(0, -1));
    }
    return fileType === normalized;
  });
}

export function isWithinMaxFileSize(file: FileLike, maxBytes: number) {
  return Number.isFinite(maxBytes) && maxBytes > 0 ? file.size <= maxBytes : true;
}

export const fileValidatorMessages = validationMessages;
