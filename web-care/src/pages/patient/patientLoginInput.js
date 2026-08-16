export function sanitizePatientPhoneInput(value) {
  const digits = `${value}`.replace(/[^\d]/g, "");
  if (digits.length === 12 && digits.startsWith("91")) {
    return digits.slice(2, 12);
  }
  return digits.slice(0, 10);
}

export function sanitizePatientOtpInput(value) {
  return `${value}`.replace(/[^\d]/g, "").slice(0, 6);
}

export function sanitizePatientAccessCodeInput(value) {
  return `${value}`.replace(/[^\d]/g, "").slice(0, 8);
}

export function isValidPatientPhoneInput(value) {
  return /^[6-9]\d{9}$/.test(sanitizePatientPhoneInput(value));
}

export function isValidPatientOtpInput(value) {
  return /^\d{6}$/.test(`${value}`);
}

export function isValidPatientAccessCodeInput(value) {
  return /^\d{8}$/.test(sanitizePatientAccessCodeInput(value));
}
