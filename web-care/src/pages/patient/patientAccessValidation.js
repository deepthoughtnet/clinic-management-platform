import { sanitizePatientPhoneInput } from "./patientLoginInput.js";

const PATIENT_ACCESS_REQUEST_FULL_NAME_MIN_LENGTH = 2;
const PATIENT_ACCESS_REQUEST_FULL_NAME_MAX_LENGTH = 120;
const PATIENT_ACCESS_REQUEST_EMAIL_MAX_LENGTH = 254;
const PATIENT_ACCESS_REQUEST_NOTE_MAX_LENGTH = 500;
const PATIENT_ACCESS_REQUEST_CLINIC_SLUG_MAX_LENGTH = 60;
const PATIENT_ACCESS_CODE_LENGTH = 8;

const PATIENT_ACCESS_REQUEST_CLINIC_SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{0,59}$/i;
const PATIENT_ACCESS_REQUEST_EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function redactIdentifiers(value) {
  return `${value}`.replace(
    /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi,
    "[redacted]",
  );
}

export function sanitizePatientAccessCodeInput(value) {
  return `${value}`.replace(/[^\d]/g, "").slice(0, PATIENT_ACCESS_CODE_LENGTH);
}

export function isValidPatientAccessCodeInput(value) {
  return /^\d{8}$/.test(sanitizePatientAccessCodeInput(value));
}

export function normalizePatientAccessRequestFullName(value) {
  return `${value}`.replace(/\s+/g, " ").trim();
}

export function isValidPatientAccessRequestFullName(value) {
  const normalized = normalizePatientAccessRequestFullName(value);
  return normalized.length >= PATIENT_ACCESS_REQUEST_FULL_NAME_MIN_LENGTH
    && normalized.length <= PATIENT_ACCESS_REQUEST_FULL_NAME_MAX_LENGTH;
}

export function normalizePatientAccessRequestMobile(value) {
  return sanitizePatientPhoneInput(value);
}

export function isValidPatientAccessRequestMobile(value) {
  return sanitizePatientPhoneInput(value).length === 10 && /^[6-9]\d{9}$/.test(sanitizePatientPhoneInput(value));
}

export function normalizePatientAccessRequestEmail(value) {
  return `${value}`.trim();
}

export function isValidPatientAccessRequestEmail(value) {
  const normalized = normalizePatientAccessRequestEmail(value);
  if (!normalized) {
    return true;
  }
  return normalized.length <= PATIENT_ACCESS_REQUEST_EMAIL_MAX_LENGTH && PATIENT_ACCESS_REQUEST_EMAIL_PATTERN.test(normalized);
}

export function normalizePatientAccessRequestClinicSlug(value) {
  return `${value}`.trim();
}

export function isValidPatientAccessRequestClinicSlug(value) {
  const normalized = normalizePatientAccessRequestClinicSlug(value);
  return Boolean(normalized)
    && normalized.length <= PATIENT_ACCESS_REQUEST_CLINIC_SLUG_MAX_LENGTH
    && PATIENT_ACCESS_REQUEST_CLINIC_SLUG_PATTERN.test(normalized);
}

export function normalizePatientAccessRequestNote(value) {
  return `${value}`.trim();
}

export function isValidPatientAccessRequestNote(value) {
  return normalizePatientAccessRequestNote(value).length <= PATIENT_ACCESS_REQUEST_NOTE_MAX_LENGTH;
}

export function sanitizePatientAccessErrorMessage(value) {
  const normalized = redactIdentifiers(value);
  const lower = normalized.toLowerCase();

  if (
    lower.includes("already pending")
    || lower.includes("pending review")
    || lower.includes("already been approved")
    || lower.includes("already has active access")
    || lower.includes("active access")
    || lower.includes("not currently active")
    || lower.includes("revoked")
  ) {
    return normalized;
  }

  if (lower.includes("clinic context")) {
    return "Select the correct clinic or hospital before signing in.";
  }

  if (
    lower.includes("phone is required")
    || lower.includes("invalid value for mobile")
    || lower.includes("mobile: ")
    || lower.includes("mobile number")
    || lower.includes("must be a valid indian mobile")
    || lower.includes("10-digit indian mobile number")
  ) {
    return "Enter a valid 10-digit Indian mobile number.";
  }

  if (
    (lower.includes("access code") || lower.includes("accesscode"))
    && (
      lower.includes("invalid")
      || lower.includes("required")
      || lower.includes("8-digit")
      || lower.includes("must be")
      || lower.includes("must match")
    )
  ) {
    return "Enter the valid temporary access code.";
  }

  if (
    (lower.includes("full name") || lower.includes("fullname"))
    && (
      lower.includes("required")
      || lower.includes("blank")
      || lower.includes("size")
      || lower.includes("length")
    )
  ) {
    return "Enter a full name between 2 and 120 characters.";
  }

  if (
    lower.includes("clinic")
    && lower.includes("slug")
    && (
      lower.includes("invalid")
      || lower.includes("required")
      || lower.includes("pattern")
      || lower.includes("allowed")
      || lower.includes("must match")
    )
  ) {
    return "Please select or enter a valid clinic or hospital slug.";
  }

  if (
    lower.includes("email")
    && (
      lower.includes("invalid")
      || lower.includes("format")
      || lower.includes("email address")
    )
  ) {
    return "Enter a valid email address.";
  }

  if (lower.includes("note") && (lower.includes("length") || lower.includes("size"))) {
    return "Note must be 500 characters or fewer.";
  }

  return normalized;
}
