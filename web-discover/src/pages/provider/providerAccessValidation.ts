import type { ProviderType } from "../../api/providerOnboarding";

export const PROVIDER_LOGIN_IDENTIFIER_MAX_LENGTH = 254;
export const PROVIDER_LOGIN_ACCESS_CODE_LENGTH = 8;
export const PROVIDER_REQUEST_FULL_NAME_MIN_LENGTH = 2;
export const PROVIDER_REQUEST_FULL_NAME_MAX_LENGTH = 120;
export const PROVIDER_REQUEST_EMAIL_MAX_LENGTH = 254;
export const PROVIDER_REQUEST_MOBILE_MAX_LENGTH = 20;
export const PROVIDER_REQUEST_PROVIDER_REFERENCE_MAX_LENGTH = 80;
export const PROVIDER_REQUEST_NOTE_MAX_LENGTH = 500;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MOBILE_ALLOWED_PATTERN = /^[0-9+\s()-]+$/;
const PROVIDER_REFERENCE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9 _./-]{0,79}$/;

const SUPPORT_PROVIDER_TYPES: readonly ProviderType[] = ["INDIVIDUAL_DOCTOR", "CLINIC", "HOSPITAL"];

function trim(value: string) {
  return `${value}`.trim();
}

function collapseWhitespace(value: string) {
  return trim(value).replace(/\s+/g, " ");
}

function normalizeMobileDigits(value: string) {
  const compact = trim(value).replace(/[\s()-]/g, "");
  const normalized = compact.startsWith("+") ? compact.slice(1) : compact;
  if (normalized.length === 12 && normalized.startsWith("91")) {
    return normalized.slice(2);
  }
  return normalized;
}

export function normalizeProviderLoginIdentifier(value: string) {
  return trim(value);
}

export function isValidProviderEmailIdentifier(value: string) {
  const normalized = normalizeProviderLoginIdentifier(value);
  return Boolean(normalized)
    && normalized.length <= PROVIDER_LOGIN_IDENTIFIER_MAX_LENGTH
    && EMAIL_PATTERN.test(normalized);
}

export function isValidProviderMobileIdentifier(value: string) {
  const normalized = normalizeProviderLoginIdentifier(value);
  if (!normalized || !MOBILE_ALLOWED_PATTERN.test(normalized)) {
    return false;
  }
  const digits = normalizeMobileDigits(normalized);
  return digits.length === 10 && /^[6-9]\d{9}$/.test(digits);
}

export function isValidProviderLoginIdentifier(value: string) {
  const normalized = normalizeProviderLoginIdentifier(value);
  if (!normalized) {
    return false;
  }
  if (normalized.includes("@")) {
    return isValidProviderEmailIdentifier(normalized);
  }
  return isValidProviderMobileIdentifier(normalized);
}

export function getProviderLoginIdentifierError(value: string) {
  return isValidProviderLoginIdentifier(value)
    ? null
    : "Enter a valid registered email address or mobile number.";
}

export function sanitizeProviderAccessCodeInput(value: string) {
  return `${value}`.replace(/\D/g, "").slice(0, PROVIDER_LOGIN_ACCESS_CODE_LENGTH);
}

export function isValidProviderAccessCodeInput(value: string) {
  return /^\d{8}$/.test(sanitizeProviderAccessCodeInput(value));
}

export function getProviderAccessCodeError(value: string) {
  return isValidProviderAccessCodeInput(value)
    ? null
    : "Enter the 8-digit temporary access code.";
}

export function normalizeProviderAccessRequestFullName(value: string) {
  return collapseWhitespace(value);
}

export function isValidProviderAccessRequestFullName(value: string) {
  const normalized = normalizeProviderAccessRequestFullName(value);
  return normalized.length >= PROVIDER_REQUEST_FULL_NAME_MIN_LENGTH
    && normalized.length <= PROVIDER_REQUEST_FULL_NAME_MAX_LENGTH;
}

export function normalizeProviderAccessRequestEmail(value: string) {
  return trim(value).toLowerCase();
}

export function isValidProviderAccessRequestEmail(value: string) {
  const normalized = normalizeProviderAccessRequestEmail(value);
  return Boolean(normalized)
    && normalized.length <= PROVIDER_REQUEST_EMAIL_MAX_LENGTH
    && EMAIL_PATTERN.test(normalized);
}

export function normalizeProviderAccessRequestMobile(value: string) {
  return trim(value);
}

export function isValidProviderAccessRequestMobile(value: string) {
  const normalized = normalizeProviderAccessRequestMobile(value);
  if (!normalized || !MOBILE_ALLOWED_PATTERN.test(normalized)) {
    return false;
  }
  const digits = normalizeMobileDigits(normalized);
  return digits.length === 10 && /^[6-9]\d{9}$/.test(digits);
}

export function normalizeProviderAccessRequestProviderType(value: ProviderType | ""): ProviderType {
  return value as ProviderType;
}

export function isValidProviderAccessRequestProviderType(value: ProviderType | "") {
  return SUPPORT_PROVIDER_TYPES.includes(value as ProviderType);
}

export function normalizeProviderAccessRequestReference(value: string) {
  return trim(value);
}

export function isValidProviderAccessRequestReference(value: string) {
  const normalized = normalizeProviderAccessRequestReference(value);
  if (!normalized) {
    return true;
  }
  return normalized.length <= PROVIDER_REQUEST_PROVIDER_REFERENCE_MAX_LENGTH
    && PROVIDER_REFERENCE_PATTERN.test(normalized);
}

export function normalizeProviderAccessRequestNote(value: string) {
  return trim(value);
}

export function isValidProviderAccessRequestNote(value: string) {
  return normalizeProviderAccessRequestNote(value).length <= PROVIDER_REQUEST_NOTE_MAX_LENGTH;
}

export function getProviderAccessRequestProviderTypeError(value: ProviderType | "") {
  return isValidProviderAccessRequestProviderType(value)
    ? null
    : "Please select a provider type.";
}

export function getProviderAccessRequestFullNameError(value: string) {
  return isValidProviderAccessRequestFullName(value)
    ? null
    : "Enter a provider name between 2 and 120 characters.";
}

export function getProviderAccessRequestEmailError(value: string) {
  return isValidProviderAccessRequestEmail(value)
    ? null
    : "Enter a valid email address.";
}

export function getProviderAccessRequestMobileError(value: string) {
  return isValidProviderAccessRequestMobile(value)
    ? null
    : "Enter a valid 10-digit Indian mobile number.";
}

export function getProviderAccessRequestReferenceError(value: string) {
  return isValidProviderAccessRequestReference(value)
    ? null
    : "Enter a valid provider application reference.";
}

export function getProviderAccessRequestNoteError(value: string) {
  return isValidProviderAccessRequestNote(value)
    ? null
    : "Note must be 500 characters or fewer.";
}

export function providerLoginIdentifierHelpText(value: string) {
  const normalized = normalizeProviderLoginIdentifier(value);
  if (!normalized) {
    return "Use the registered email address or mobile number linked to your approved provider account.";
  }
  if (normalized.includes("@")) {
    return "Use the registered email address linked to your approved provider account.";
  }
  return "Use the registered mobile number linked to your approved provider account.";
}
