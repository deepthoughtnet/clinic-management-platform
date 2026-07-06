import * as React from "react";
import type { Patient, PatientGender, PatientInput } from "../../api/clinicApi";
import { normalizeIndianMobileInput } from "@deepthoughtnet/form-validation-kit";
import { searchPatients } from "../../api/clinicApi";

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
  return calculateAgeFromDob(dateOfBirth);
}

export function calculateAgeFromDob(dateOfBirth: string, now = new Date()) {
  if (!dateOfBirth) return "";
  const dob = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(dob.getTime())) return "";
  let age = now.getFullYear() - dob.getFullYear();
  const monthDiff = now.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < dob.getDate())) {
    age -= 1;
  }
  return age >= 0 && age <= 130 ? String(age) : "";
}

export function approximateDobFromAge(ageYears: string | number | null | undefined, now = new Date()) {
  const parsed = Number(ageYears);
  if (ageYears === null || ageYears === undefined || ageYears === "" || Number.isNaN(parsed) || parsed < 0 || parsed > 130) return "";
  return `${now.getFullYear() - parsed}-01-01`;
}

export function toPatientInput(form: QuickRegisterForm): PatientInput {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    gender: form.gender,
    dateOfBirth: form.dateOfBirth || null,
    ageYears: form.ageYears === "" ? null : Number(form.ageYears),
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

export function patientDisplayName(patient: Patient | null) {
  if (!patient) return "";
  return `${patient.firstName} ${patient.lastName || ""}`.trim();
}

export function patientMobileLine(patient: Patient | null) {
  return patient?.mobile ? `Mobile: ${patient.mobile}` : "Mobile: Not set";
}

export function patientNumberLine(patient: Patient | null) {
  return patient?.patientNumber ? `Patient No: ${patient.patientNumber}` : "Patient No: Not assigned";
}

export function patientIdentitySummary(patient: Patient | null) {
  if (!patient) return "";
  return [patientDisplayName(patient), patientMobileLine(patient), patientNumberLine(patient)].filter(Boolean).join(" • ");
}

export function patientLabel(patient: Patient | null) {
  if (!patient) return "";
  const age = patient.ageYears !== null ? `${patient.ageYears}y` : null;
  const label = patientDisplayName(patient);
  return [label, age, patient.gender].filter(Boolean).join(" • ");
}

export function useDuplicatePatientLookup({
  accessToken,
  tenantId,
  mobile,
  enabled = true,
  debounceMs = 500,
}: {
  accessToken: string | null;
  tenantId: string | null;
  mobile: string;
  enabled?: boolean;
  debounceMs?: number;
}) {
  const [duplicates, setDuplicates] = React.useState<Patient[]>([]);
  const [checking, setChecking] = React.useState(false);
  const [continueNew, setContinueNew] = React.useState(false);

  React.useEffect(() => {
    let cancelled = false;
    const normalizedMobile = String(normalizeIndianMobileInput(mobile) || "").trim();
    const shouldLookup = enabled && !continueNew && Boolean(accessToken) && Boolean(tenantId) && normalizedMobile.length >= 6;
    if (!shouldLookup) {
      setDuplicates([]);
      setChecking(false);
      if (!mobile.trim()) {
        setContinueNew(false);
      }
      return () => {
        cancelled = true;
      };
    }

    setChecking(true);
    const handle = window.setTimeout(async () => {
      try {
        const rows = await searchPatients(accessToken as string, tenantId as string, { mobile: normalizedMobile, active: true });
        if (!cancelled) {
          setDuplicates(rows.slice(0, 5));
        }
      } catch {
        if (!cancelled) {
          setDuplicates([]);
        }
      } finally {
        if (!cancelled) {
          setChecking(false);
        }
      }
    }, debounceMs);

    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [accessToken, continueNew, debounceMs, enabled, mobile, tenantId]);

  React.useEffect(() => {
    if (mobile.trim()) {
      setContinueNew(false);
    }
  }, [mobile]);

  return {
    duplicates,
    checking,
    continueNew,
    setContinueNew,
  };
}
