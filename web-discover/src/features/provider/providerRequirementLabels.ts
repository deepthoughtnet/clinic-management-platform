const LABELS: Record<string, string> = {
  CLINIC_NAME_REQUIRED: "Clinic name",
  CLINIC_REGISTRATION_NUMBER_REQUIRED: "Clinic registration number",
  CLINIC_ORGANISATION_TYPE_REQUIRED: "Clinic organisation type",
  CLINIC_FACILITIES_REQUIRED: "Clinic facilities",
  CLINIC_LOGO_REQUIRED: "Clinic logo",
  CLINIC_REGISTRATION_DOCUMENT_REQUIRED: "Clinic registration document",
  DOCTOR_NAME_REQUIRED: "Doctor name",
  DOCTOR_REGISTRATION_NUMBER_REQUIRED: "Doctor registration number",
  DOCTOR_QUALIFICATION_REQUIRED: "Doctor qualification",
  DOCTOR_REGISTRATION_COUNCIL_REQUIRED: "Doctor registration council",
  DOCTOR_PHOTO_REQUIRED: "Doctor photo",
  DOCTOR_REGISTRATION_CERTIFICATE_REQUIRED: "Doctor registration certificate",
  HOSPITAL_NAME_REQUIRED: "Hospital name",
  HOSPITAL_REGISTRATION_NUMBER_REQUIRED: "Hospital registration number",
  HOSPITAL_OWNERSHIP_REQUIRED: "Hospital ownership",
  HOSPITAL_TYPE_REQUIRED: "Hospital type",
  HOSPITAL_BEDS_REQUIRED: "Hospital beds",
  HOSPITAL_DEPARTMENTS_REQUIRED: "Hospital departments",
  HOSPITAL_MEDICAL_DIRECTOR_REQUIRED: "Hospital medical director",
  HOSPITAL_EMERGENCY_STATUS_REQUIRED: "Hospital emergency status",
  HOSPITAL_LOGO_REQUIRED: "Hospital logo",
  HOSPITAL_REGISTRATION_DOCUMENT_REQUIRED: "Hospital registration document",
  PRIMARY_LOCATION_REQUIRED: "Primary location",
  SERVICES_REQUIRED: "At least one service",
  PRIMARY_SPECIALITY_REQUIRED: "Primary speciality",
  PRACTISING_SINCE_REQUIRED: "Year practice began",
  CONTACT_VERIFICATION_REQUIRED: "Contact verification",
  EMAIL_REQUIRED: "Email",
  PHONE_REQUIRED: "Phone",
  TERMS_ACCEPTANCE_REQUIRED: "Terms acceptance",
  PRIVACY_ACCEPTANCE_REQUIRED: "Privacy acceptance",
  REFERENCE_DATA_UNAVAILABLE: "Required reference data unavailable",
};

function humanizeValue(value: string) {
  return value
    .replace(/_/g, " ")
    .trim()
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

export function providerRequirementLabel(code: string) {
  return LABELS[code] ?? humanizeValue(code);
}

export function providerRequirementGroup(code: string) {
  if (
    code.includes("EMAIL") ||
    code.includes("PHONE") ||
    code.includes("CONTACT") ||
    code.includes("TERMS") ||
    code.includes("PRIVACY")
  ) {
    return "Account";
  }
  if (
    code.includes("LOGO") ||
    code.includes("PHOTO") ||
    code.includes("DOCUMENT") ||
    code.includes("BRANDING") ||
    code.includes("ACCREDITATION")
  ) {
    return "Branding";
  }
  if (code.includes("SERVICE") || code.includes("SPECIALITY")) {
    return "Services";
  }
  if (code.includes("LOCATION")) {
    return "Locations";
  }
  if (
    code.includes("CLINIC") ||
    code.includes("HOSPITAL") ||
    code.includes("DOCTOR") ||
    code.includes("NAME") ||
    code.includes("REGISTRATION") ||
    code.includes("FACILITIES") ||
    code.includes("OWNERSHIP") ||
    code.includes("TYPE") ||
    code.includes("BED") ||
    code.includes("DIRECTOR") ||
    code.includes("QUALIFICATION") ||
    code.includes("PRACTISING")
  ) {
    return "Organisation";
  }
  return "Other";
}

export function groupProviderRequirements(codes: string[]) {
  return codes.reduce<Record<string, string[]>>((groups, code) => {
    const group = providerRequirementGroup(code);
    (groups[group] ??= []).push(code);
    return groups;
  }, {});
}
