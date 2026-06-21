export function sanitizePatientPhoneInput(value) {
  const digits = `${value}`.replace(/[^\d]/g, "");
  if (digits.length > 10 && digits.startsWith("91")) {
    return digits.slice(2, 12).slice(0, 10);
  }
  return digits.slice(0, 10);
}

export function sanitizePatientOtpInput(value) {
  return `${value}`.replace(/[^\d]/g, "").slice(0, 6);
}

export function isValidPatientPhoneInput(value) {
  return /^[6-9]\d{9}$/.test(`${value}`);
}

export function isValidPatientOtpInput(value) {
  return /^\d{6}$/.test(`${value}`);
}
