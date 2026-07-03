import type { Patient, PatientGender, PatientInput } from "../../api/clinicApi";
import { normalizeIndianMobileInput } from "@deepthoughtnet/form-validation-kit";

export type QuickRegisterForm = {
  mobile: string;
  firstName: string;
  lastName: string;
  ageYears: string;
  dateOfBirth: string;
  gender: PatientGender;
};

export function emptyQuickRegisterForm(mobile = ""): QuickRegisterForm {
  return {
    mobile,
    firstName: "",
    lastName: "",
    ageYears: "",
    dateOfBirth: "",
    gender: "UNKNOWN",
  };
}

export function calculateAge(dateOfBirth: string) {
  if (!dateOfBirth) return "";
  const dob = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(dob.getTime())) return "";
  const today = new Date();
  let age = today.getFullYear() - dob.getFullYear();
  const monthDiff = today.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) {
    age -= 1;
  }
  return age >= 0 && age <= 130 ? String(age) : "";
}

export function approximateDobFromAge(ageYears: string) {
  const parsed = Number(ageYears);
  if (!ageYears || Number.isNaN(parsed) || parsed < 0 || parsed > 130) return "";
  return `${new Date().getFullYear() - parsed}-01-01`;
}

export function toPatientInput(form: QuickRegisterForm): PatientInput {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    gender: form.gender,
    dateOfBirth: form.dateOfBirth || null,
    ageYears: form.ageYears ? Number(form.ageYears) : null,
    mobile: normalizeIndianMobileInput(form.mobile) as string,
    email: null,
    addressLine1: null,
    addressLine2: null,
    city: null,
    state: null,
    country: null,
    postalCode: null,
    emergencyContactName: null,
    emergencyContactMobile: null,
    bloodGroup: null,
    allergies: null,
    existingConditions: null,
    longTermMedications: null,
    surgicalHistory: null,
    notes: null,
    active: true,
  };
}

export function seedQuickRegisterMobile(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return "";
  try {
    const normalized = normalizeIndianMobileInput(trimmed) as string;
    return /^[6-9]\d{9}$/.test(normalized) ? normalized : "";
  } catch {
    return "";
  }
}

export function patientSummary(patient: Patient) {
  return `${patient.patientNumber} • ${patient.mobile}`;
}

export function patientLabel(patient: Patient | null) {
  if (!patient) return "";
  const age = patient.ageYears !== null ? `${patient.ageYears}y` : null;
  const label = `${patient.firstName} ${patient.lastName || ""}`.trim();
  return [label, age, patient.gender].filter(Boolean).join(" • ");
}
