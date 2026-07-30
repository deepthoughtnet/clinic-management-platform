import { type ChangeEvent, type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { PaletteOutlined } from "@mui/icons-material";
import {
  createProviderApplication,
  loadProviderChangeRequests,
  loadProviderApplication,
  loadProviderPreview,
  requestProviderEmailVerification,
  requestProviderPhoneVerification,
  resubmitProviderApplication,
  submitProviderApplication,
  verifyProviderEmail,
  verifyProviderPhone,
  updateProviderApplication,
  uploadProviderDocument,
  type ProviderApplication,
  type ProviderApplicationPayload,
  type ProviderDocumentType,
  type ContactVerificationStatus,
  type ProviderPreview,
  type ProviderServicePayload,
  type ProviderServiceType,
  type ProviderStatus,
  type ProviderType,
} from "../../api/providerOnboarding";
import { loadDiscoverReferenceCatalog, type DiscoverReferenceCatalog, type DiscoverReferenceOption } from "../../api/referenceData";
import { DISCOVER_ROUTES, REGISTRATION_PROVIDER_TYPE_BY_ROUTE } from "../../routes";
import { careBookingUrl } from "../../components/DiscoveryComponents";
import { LocationPicker } from "../../components/location";
import { ProviderDropdownField, ProviderMultiSelectField } from "../../components/provider-onboarding/ProviderOnboardingFields";
import { ProviderOnboardingStepper, ProviderSaveStatus, type ProviderOnboardingStepState } from "../../components/provider-onboarding/ProviderOnboardingStepper";
import { providerDocumentContentPath } from "../../api/providerOnboarding";
import { PublicProviderProfile } from "../../components/discovery/PublicProviderProfile";

const TOKEN_KEY = "jeevanam.discover.providerOnboardingToken";
const TOKEN_KEYS = [
  TOKEN_KEY,
  `${TOKEN_KEY}.INDIVIDUAL_DOCTOR`,
  `${TOKEN_KEY}.CLINIC`,
  `${TOKEN_KEY}.HOSPITAL`,
];

type SaveReason = "autosave" | "manual" | "navigation" | "preview" | "route-transition";

type AccountStepValues = {
  email: string;
  phone: string;
  password: string;
  termsAccepted: boolean;
  privacyAccepted: boolean;
};

type VerificationCodeState = {
  code: string;
  requestMessage: string;
  resendAfterSeconds: number | null;
  devCode: string | null;
};

type ProviderOnboardingConfig = {
  title: string;
  eyebrow: string;
  nameLabel: string;
  registrationLabel: string;
  specialityLabel: string;
  specialityRequired: boolean;
  showSpecialities: boolean;
  showDepartments: boolean;
  showMedicalCouncil: boolean;
  showQualification: boolean;
  showExperience: boolean;
  showMedicalDirector: boolean;
  showBeds: boolean;
  showEmergencyAvailable: boolean;
  showOwnership: boolean;
  showOrganisationType: boolean;
  showHospitalType: boolean;
  brandingAssetLabel: string;
};

const ownershipOptions = [
  "Private",
  "Government",
  "Trust",
  "NGO",
  "Corporate",
  "Teaching Hospital",
  "Community Health Centre",
  "Clinic Chain",
];

const providerTypeConfig: Record<ProviderType, ProviderOnboardingConfig> = {
  INDIVIDUAL_DOCTOR: {
    title: "Create your public doctor profile",
    eyebrow: "Doctor onboarding",
    nameLabel: "Doctor name",
    registrationLabel: "Medical registration number",
    specialityLabel: "Primary speciality",
    specialityRequired: true,
    showSpecialities: true,
    showDepartments: false,
    showMedicalCouncil: true,
    showQualification: true,
    showExperience: true,
    showMedicalDirector: false,
    showBeds: false,
    showEmergencyAvailable: false,
    showOwnership: false,
    showOrganisationType: false,
    showHospitalType: false,
    brandingAssetLabel: "Doctor photo",
  },
  CLINIC: {
    title: "Create your clinic profile",
    eyebrow: "Clinic onboarding",
    nameLabel: "Clinic name",
    registrationLabel: "Clinic registration number",
    specialityLabel: "Primary speciality",
    specialityRequired: false,
    showSpecialities: true,
    showDepartments: false,
    showMedicalCouncil: false,
    showQualification: false,
    showExperience: false,
    showMedicalDirector: false,
    showBeds: false,
    showEmergencyAvailable: false,
    showOwnership: true,
    showOrganisationType: true,
    showHospitalType: false,
    brandingAssetLabel: "Logo",
  },
  HOSPITAL: {
    title: "Create your hospital profile",
    eyebrow: "Hospital onboarding",
    nameLabel: "Hospital name",
    registrationLabel: "Hospital registration number",
    specialityLabel: "Primary speciality",
    specialityRequired: false,
    showSpecialities: false,
    showDepartments: true,
    showMedicalCouncil: false,
    showQualification: false,
    showExperience: false,
    showMedicalDirector: true,
    showBeds: true,
    showEmergencyAvailable: true,
    showOwnership: true,
    showOrganisationType: false,
    showHospitalType: true,
    brandingAssetLabel: "Logo",
  },
};

function optionMatchesProviderType(option: DiscoverReferenceOption, providerType: ProviderType) {
  return option.active && (option.providerTypes.length === 0 || option.providerTypes.includes(providerType));
}

function toOptionItems(options: DiscoverReferenceOption[], providerType: ProviderType) {
  return options
    .filter((option) => optionMatchesProviderType(option, providerType))
    .sort((left, right) => left.displayOrder - right.displayOrder || left.displayName.localeCompare(right.displayName))
    .map((option) => ({ value: option.displayName, label: option.displayName }));
}

function toServiceItems(options: DiscoverReferenceOption[], providerType: ProviderType) {
  return options
    .filter((option) => optionMatchesProviderType(option, providerType))
    .sort((left, right) => left.displayOrder - right.displayOrder || left.displayName.localeCompare(right.displayName))
    .map((option) => ({ type: option.code as ProviderServiceType, label: option.displayName }));
}

function optionNames(options: DiscoverReferenceOption[], providerType: ProviderType) {
  return toOptionItems(options, providerType).map((item) => item.value.toLowerCase());
}

function hasReferenceData(options: DiscoverReferenceOption[], providerType: ProviderType) {
  return toOptionItems(options, providerType).length > 0;
}

function emptyReferenceCatalog(): DiscoverReferenceCatalog {
  return {
    specialities: [],
    services: [],
    facilities: [],
    languages: [],
    countries: [],
    states: [],
    medicalCouncils: [],
  };
}

const steps = [
  ["account", "Account"],
  ["organisation", "Organisation"],
  ["professional", "Professional Details"],
  ["services", "Services"],
  ["locations", "Locations"],
  ["branding", "Branding"],
  ["preview", "Preview"],
  ["submit", "Submit"],
] as const;

type ProviderOnboardingStepId = (typeof steps)[number][0];

const READ_ONLY_PROVIDER_STATUSES: ProviderStatus[] = ["SUBMITTED", "UNDER_REVIEW", "APPROVED", "PUBLISHED", "SUSPENDED", "ARCHIVED"];

function isApplicationEditable(status: ProviderStatus | undefined) {
  return Boolean(status && !READ_ONLY_PROVIDER_STATUSES.includes(status));
}

function blankDraft(providerType: ProviderType): ProviderApplicationPayload {
  return {
    displayName: "",
    legalName: "",
    registrationNumber: "",
    languages: [],
    specialities: [],
    subSpecialities: [],
    departments: [],
    facilities: [],
    accreditations: [],
    onlineConsultation: false,
    emergencyAvailable: false,
    appointmentDurationMinutes: 15,
    services: [],
    locations: [],
    branding: { primaryColor: "#0F8B8D", tagline: "" },
    ...(providerType === "INDIVIDUAL_DOCTOR" ? { yearsOfExperience: 0 } : {}),
    ...(providerType === "HOSPITAL" ? { beds: 0 } : {}),
  };
}

function providerTypeFromRoute(type: "doctor" | "clinic" | "hospital"): ProviderType {
  return REGISTRATION_PROVIDER_TYPE_BY_ROUTE[type];
}

function readStoredToken(keys: string[]) {
  for (const key of keys) {
    const token = localStorage.getItem(key);
    if (token) return token;
  }
  return "";
}

function toDraftPayload(source: ProviderApplication | ProviderApplicationPayload): ProviderApplicationPayload {
  return {
    version: source.version,
    email: source.email,
    phone: source.phone,
    contactVerified: source.contactVerified,
    termsAccepted: source.termsAccepted,
    privacyAccepted: source.privacyAccepted,
    displayName: source.displayName,
    legalName: source.legalName,
    organisationType: source.organisationType,
    registrationNumber: source.registrationNumber,
    gstNumber: source.gstNumber,
    website: source.website,
    gender: source.gender,
    dateOfBirth: source.dateOfBirth,
    languages: source.languages,
    biography: source.biography,
    medicalCouncil: source.medicalCouncil,
    qualification: source.qualification,
    yearsOfExperience: source.yearsOfExperience,
    specialities: source.specialities,
    subSpecialities: source.subSpecialities,
    consultationFee: source.consultationFee,
    onlineConsultation: source.onlineConsultation,
    appointmentDurationMinutes: source.appointmentDurationMinutes,
    ownership: source.ownership,
    hospitalType: source.hospitalType,
    beds: source.beds,
    emergencyAvailable: source.emergencyAvailable,
    medicalDirector: source.medicalDirector,
    departments: source.departments,
    facilities: source.facilities,
    accreditations: source.accreditations,
    locations: source.locations,
    services: source.services,
    branding: source.branding,
  };
}

function blankAccountStepValues(): AccountStepValues {
  return {
    email: "",
    phone: "",
    password: "",
    termsAccepted: false,
    privacyAccepted: false,
  };
}

function mapAccountStepValues(source: Pick<ProviderApplicationPayload, "email" | "phone" | "termsAccepted" | "privacyAccepted"> | null | undefined): AccountStepValues {
  return {
    email: source?.email ?? "",
    phone: source?.phone ?? "",
    password: "",
    termsAccepted: Boolean(source?.termsAccepted),
    privacyAccepted: Boolean(source?.privacyAccepted),
  };
}

function draftSnapshot(source: ProviderApplication | ProviderApplicationPayload) {
  const { version: _version, ...rest } = toDraftPayload(source);
  void _version;
  return JSON.stringify(rest);
}

type ProviderFieldErrors = Partial<Record<
  | "displayName"
  | "registrationNumber"
  | "website"
  | "gstNumber"
  | "ownership"
  | "languages"
  | "specialities"
  | "facilities"
  | "biography"
  | "organisationType"
  | "hospitalType"
  | "qualification"
  | "medicalCouncil"
  | "yearsOfExperience"
  | "beds"
  | "medicalDirector"
  | "emergencyAvailable"
  | "departments"
  | "locations"
  | "services"
  | "documents"
  | "account"
  | "terms"
  | "privacy",
  string
>>;

type ProviderAccountCompletionInput = Pick<AccountStepValues, "email" | "phone" | "termsAccepted" | "privacyAccepted">;
type PreviewChecklistItem = {
  label: string;
  step: ProviderOnboardingStepId;
  detail?: string;
};
type PreviewGalleryItem = {
  url: string;
  alt: string;
  caption: string;
};

type ProfessionalInformationItem = {
  label: string;
  value: string;
  wide?: boolean;
};

function providerTypeLabel(providerType: ProviderType) {
  return providerType === "INDIVIDUAL_DOCTOR" ? "Doctor" : providerType === "CLINIC" ? "Clinic" : "Hospital";
}

function providerReferencePrefix(providerType: ProviderType) {
  return providerType === "INDIVIDUAL_DOCTOR" ? "JDR" : providerType === "CLINIC" ? "JCL" : "JHS";
}

function stepLabel(stepId: ProviderOnboardingStepId) {
  return steps.find(([id]) => id === stepId)?.[1] ?? "Preview";
}

function normalizeSearchList(items: string[]) {
  return items.map((item) => item.trim()).filter(Boolean).filter((item, index, list) => list.findIndex((candidate) => candidate.toLowerCase() === item.toLowerCase()) === index);
}

function hasEnabledServices(services: ProviderApplicationPayload["services"] | undefined) {
  return Boolean(services?.some((item) => item.enabled !== false));
}

function primaryLocation(draft: ProviderApplicationPayload) {
  return draft.locations?.[0] ?? null;
}

function formatCurrency(amount: number | null | undefined) {
  if (amount == null || Number.isNaN(amount)) return null;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(amount);
}

function providerCallLabel(providerType: ProviderType) {
  return providerType === "INDIVIDUAL_DOCTOR" ? "Call Doctor" : providerType === "CLINIC" ? "Call Clinic" : "Call Hospital";
}

function validateDraft(
  draft: ProviderApplicationPayload,
  account: ProviderAccountCompletionInput,
  providerType: ProviderType,
  catalog: DiscoverReferenceCatalog,
) {
  const errors: ProviderFieldErrors = {};
  const displayName = draft.displayName?.trim() || draft.legalName?.trim();
  const website = draft.website?.trim();
  const gst = draft.gstNumber?.trim();
  const biography = draft.biography?.trim() ?? "";
  const specialityOptions = optionNames(catalog.specialities, providerType);
  const serviceOptions = optionNames(catalog.services, providerType);
  const facilityOptions = optionNames(catalog.facilities, providerType);
  const languageOptions = optionNames(catalog.languages, providerType);
  const countryOptions = optionNames(catalog.countries, providerType);
  const stateOptions = optionNames(catalog.states, providerType);
  const medicalCouncilOptions = optionNames(catalog.medicalCouncils, providerType);
  const specialitySet = new Set(specialityOptions);
  const serviceSet = new Set(serviceOptions);
  const facilitySet = new Set(facilityOptions);
  const languageSet = new Set(languageOptions);
  const countrySet = new Set(countryOptions);
  const stateSet = new Set(stateOptions);
  const medicalCouncilSet = new Set(medicalCouncilOptions);
  const specialities = normalizeSearchList(draft.specialities ?? []);
  const departments = normalizeSearchList(draft.departments ?? []);
  const languages = normalizeSearchList(draft.languages ?? []);
  const facilities = normalizeSearchList(draft.facilities ?? []);
  const services = normalizeSearchList((draft.services ?? []).filter((item) => item.enabled !== false).map((item) => item.label));

  if (!account.email.trim()) errors.account = "Email is required.";
  if (!account.phone.trim()) errors.account = errors.account ?? "Phone is required.";
  if (!account.termsAccepted) errors.terms = "Terms acceptance is required.";
  if (!account.privacyAccepted) errors.privacy = "Privacy acceptance is required.";
  if (!displayName) errors.displayName = `${providerTypeLabel(providerType)} name is required.`;
  if (!draft.registrationNumber?.trim()) errors.registrationNumber = "Official registration number is required.";
  if (website && !/^https?:\/\/[^\s/$.?#].[^\s]*$/i.test(website)) errors.website = "Enter a valid public website URL.";
  if (gst && !/^[0-9A-Z]{15}$/i.test(gst)) errors.gstNumber = "GST number must be 15 alphanumeric characters when provided.";
  if (biography.length > 1500) errors.biography = "Biography must be 1500 characters or fewer.";
  if (providerType !== "INDIVIDUAL_DOCTOR" && !draft.ownership?.trim()) errors.ownership = "Ownership type is required.";
  if (providerType === "CLINIC" && !draft.organisationType?.trim()) errors.organisationType = "Clinic organisation type is required.";
  if (providerType === "HOSPITAL" && !draft.hospitalType?.trim()) errors.hospitalType = "Hospital type is required.";

  if (providerType === "INDIVIDUAL_DOCTOR") {
    if (!draft.qualification?.trim()) errors.qualification = "Qualification is required.";
    if (!draft.medicalCouncil?.trim()) errors.medicalCouncil = "Medical council registration is required.";
    if (draft.yearsOfExperience == null || Number.isNaN(draft.yearsOfExperience)) errors.yearsOfExperience = "Experience is required.";
    if (!specialities.length) errors.specialities = "Select a primary speciality.";
  }

  if (providerType === "CLINIC" && specialities.length && !specialities.every((item) => specialitySet.has(item.toLowerCase()))) {
    errors.specialities = "Choose from the speciality master.";
  }

  if (providerType === "HOSPITAL") {
    if (!departments.length) errors.departments = "Add at least one department.";
    if (!draft.medicalDirector?.trim()) errors.medicalDirector = "Medical director is required.";
    if (!draft.beds || draft.beds <= 0) errors.beds = "Beds must be greater than zero.";
    if (!draft.emergencyAvailable) errors.emergencyAvailable = "Emergency availability must be confirmed.";
  }

  if (!languages.length) errors.languages = "Select at least one language.";
  if (!facilities.length) errors.facilities = "Select at least one facility.";
  if (!services.length) errors.services = "Select at least one service.";

  if (specialities.length && !specialities.every((item) => specialitySet.has(item.toLowerCase()))) errors.specialities = "Choose from the speciality master.";
  if (languages.length && !languages.every((item) => languageSet.has(item.toLowerCase()))) errors.languages = "Choose from the language master.";
  if (facilities.length && !facilities.every((item) => facilitySet.has(item.toLowerCase()))) errors.facilities = "Choose from the facility master.";
  if (draft.services?.length && !draft.services.filter((item) => item.enabled !== false).every((item) => serviceSet.has(item.label.toLowerCase()))) errors.services = "Choose from the service master.";
  if (draft.locations?.length) {
    const location = draft.locations[0];
    if (!location.address?.trim()) errors.locations = "Add a street address.";
    if (!location.city?.trim()) errors.locations = errors.locations ?? "Add a city.";
    if (!location.state?.trim()) errors.locations = errors.locations ?? "Add a state.";
    if (!location.country?.trim()) errors.locations = errors.locations ?? "Add a country.";
    if (location.country && !countrySet.has(location.country.toLowerCase())) errors.locations = errors.locations ?? "Choose from the country master.";
    if (location.state && !stateSet.has(location.state.toLowerCase())) errors.locations = errors.locations ?? "Choose from the state master.";
    if (!location.pinCode?.trim()) errors.locations = errors.locations ?? "Add a postal code.";
  } else {
    errors.locations = "Add at least one location.";
  }
  if (providerType === "INDIVIDUAL_DOCTOR" && draft.medicalCouncil && !medicalCouncilSet.has(draft.medicalCouncil.toLowerCase())) {
    errors.medicalCouncil = "Choose from the medical council master.";
  }

  return {
    errors,
    specialities,
    languages,
    facilities,
    requiredRemaining: Object.keys(errors).length,
  };
}

function contactVerificationSatisfied(application: ProviderApplication | null, contactVerification: ContactVerificationStatus | null) {
  return Boolean(contactVerification?.requirementSatisfied ?? application?.contactVerification?.requirementSatisfied ?? application?.contactVerified);
}

function stepCompletion(
  stepId: ProviderOnboardingStepId,
  validation: ReturnType<typeof validateDraft>,
  application: ProviderApplication | null,
  draft: ProviderApplicationPayload,
  contactSatisfied: boolean,
  providerType: ProviderType,
) {
  const currentLocation = draft.locations?.[0] ?? null;
  const hasBranding = providerType === "INDIVIDUAL_DOCTOR"
    ? Boolean(application?.branding?.doctorPhotoDocumentId)
    : Boolean(application?.branding?.logoDocumentId);

  switch (stepId) {
    case "account":
      return !validation.errors.account && !validation.errors.terms && !validation.errors.privacy && contactSatisfied;
    case "organisation":
      return !validation.errors.displayName && !validation.errors.registrationNumber && !validation.errors.website && !validation.errors.gstNumber && !validation.errors.ownership && !validation.errors.organisationType && !validation.errors.hospitalType;
    case "professional":
      return !validation.errors.qualification && !validation.errors.medicalCouncil && !validation.errors.yearsOfExperience && !validation.errors.specialities && !validation.errors.departments && !validation.errors.languages && !validation.errors.facilities && !validation.errors.beds && !validation.errors.medicalDirector && !validation.errors.emergencyAvailable;
    case "services":
      return !validation.errors.services && Boolean(draft.services?.some((item) => item.enabled !== false));
    case "locations":
      return !validation.errors.locations && Boolean(currentLocation?.address && currentLocation.city && currentLocation.state && currentLocation.country && currentLocation.pinCode);
    case "branding":
      return hasBranding;
    case "preview":
      return contactSatisfied && !validation.errors.account && !validation.errors.registrationNumber && !validation.errors.locations;
    case "submit":
      return Boolean(application && application.status !== "DRAFT" && contactSatisfied);
    default:
      return false;
  }
}

function missingItemLabel(code: string) {
  switch (code) {
    case "CONTACT_VERIFICATION_REQUIRED":
      return "Verify your contact details before submitting your profile.";
    case "EMAIL_REQUIRED":
      return "Email is required.";
    case "PHONE_REQUIRED":
      return "Phone is required.";
    case "TERMS_ACCEPTANCE_REQUIRED":
      return "Accept the terms to continue.";
    case "PRIVACY_ACCEPTANCE_REQUIRED":
      return "Accept the privacy policy to continue.";
    case "DOCTOR_NAME_REQUIRED":
      return "Add the doctor’s full name.";
    case "CLINIC_NAME_REQUIRED":
      return "Add the clinic name.";
    case "HOSPITAL_NAME_REQUIRED":
      return "Add the hospital name.";
    case "DOCTOR_REGISTRATION_NUMBER_REQUIRED":
      return "Add the medical registration number.";
    case "CLINIC_REGISTRATION_NUMBER_REQUIRED":
      return "Add the clinic registration number.";
    case "HOSPITAL_REGISTRATION_NUMBER_REQUIRED":
      return "Add the hospital registration number.";
    case "PRIMARY_SPECIALITY_REQUIRED":
      return "Select a primary speciality.";
    case "DOCTOR_QUALIFICATION_REQUIRED":
      return "Add professional qualifications.";
    case "DOCTOR_REGISTRATION_COUNCIL_REQUIRED":
      return "Select the registration council.";
    case "PRACTISING_SINCE_REQUIRED":
      return "Add the year practice began.";
    case "CLINIC_ORGANISATION_TYPE_REQUIRED":
      return "Add the clinic organisation type.";
    case "CLINIC_FACILITIES_REQUIRED":
      return "Select the clinic facilities.";
    case "HOSPITAL_DEPARTMENTS_REQUIRED":
      return "Add at least one department.";
    case "HOSPITAL_OWNERSHIP_REQUIRED":
      return "Select the hospital ownership type.";
    case "HOSPITAL_TYPE_REQUIRED":
      return "Add the hospital type.";
    case "HOSPITAL_BEDS_REQUIRED":
      return "Add the number of licensed beds.";
    case "HOSPITAL_MEDICAL_DIRECTOR_REQUIRED":
      return "Add the medical director.";
    case "HOSPITAL_EMERGENCY_STATUS_REQUIRED":
      return "Confirm emergency availability.";
    case "PRIMARY_LOCATION_REQUIRED":
      return "Add a primary practice location.";
    case "SERVICES_REQUIRED":
      return "Select at least one service.";
    case "DOCTOR_PHOTO_REQUIRED":
      return "Upload the doctor photo.";
    case "DOCTOR_REGISTRATION_CERTIFICATE_REQUIRED":
      return "Upload the registration certificate.";
    case "CLINIC_LOGO_REQUIRED":
      return "Upload the clinic logo.";
    case "CLINIC_REGISTRATION_DOCUMENT_REQUIRED":
      return "Upload the clinic registration document.";
    case "HOSPITAL_LOGO_REQUIRED":
      return "Upload the hospital logo.";
    case "HOSPITAL_REGISTRATION_DOCUMENT_REQUIRED":
      return "Upload the hospital registration document.";
    case "REFERENCE_DATA_UNAVAILABLE":
      return "Required reference data is unavailable right now.";
    default:
      return code.replaceAll("_", " ").toLowerCase();
  }
}

function missingItemGroup(code: string) {
  if (code.includes("VERIFICATION") || code === "EMAIL_REQUIRED" || code === "PHONE_REQUIRED" || code === "TERMS_ACCEPTANCE_REQUIRED" || code === "PRIVACY_ACCEPTANCE_REQUIRED") {
    return "Profile";
  }
  if (code.includes("DOCTOR_") || code.includes("HOSPITAL_") || code.includes("CLINIC_")) {
    return code.includes("DOCUMENT") ? "Documents" : "Professional";
  }
  if (code === "SERVICES_REQUIRED") {
    return "Services";
  }
  if (code === "PRIMARY_LOCATION_REQUIRED") {
    return "Profile";
  }
  if (code.includes("DOCUMENT")) {
    return "Documents";
  }
  if (code.includes("BRANDING") || code.includes("PHOTO") || code.includes("LOGO")) {
    return "Branding";
  }
  return "Profile";
}

function contactVerificationSummary(contactVerification: ContactVerificationStatus | null, email: string, phone: string) {
  const emailStatus = contactVerification?.emailStatus ?? (email ? "NOT_VERIFIED" : "NOT_VERIFIED");
  const phoneStatus = contactVerification?.phoneStatus ?? (phone ? "NOT_VERIFIED" : "NOT_VERIFIED");
  const emailLabel = emailStatus === "VERIFIED" ? "Email verified" : emailStatus === "PENDING" ? "Email verification sent" : "Email not verified";
  const phoneLabel = phoneStatus === "VERIFIED" ? "Phone verified" : phoneStatus === "PENDING" ? "Phone OTP sent" : "Phone not verified";
  return `${emailLabel} · ${phoneLabel}`;
}

function stepHasRequiredReferenceData(stepId: ProviderOnboardingStepId, providerType: ProviderType, catalog: DiscoverReferenceCatalog) {
  switch (stepId) {
    case "professional":
      return providerType === "INDIVIDUAL_DOCTOR"
        ? hasReferenceData(catalog.specialities, providerType) && hasReferenceData(catalog.medicalCouncils, providerType) && hasReferenceData(catalog.languages, providerType) && hasReferenceData(catalog.facilities, providerType)
        : hasReferenceData(catalog.languages, providerType) && hasReferenceData(catalog.facilities, providerType);
    case "services":
      return hasReferenceData(catalog.services, providerType);
    case "locations":
      return hasReferenceData(catalog.countries, providerType) && hasReferenceData(catalog.states, providerType);
    case "preview":
    case "submit":
      return submissionReferenceDataReady(providerType, catalog);
    default:
      return true;
  }
}

function submissionReferenceDataReady(providerType: ProviderType, catalog: DiscoverReferenceCatalog) {
  return providerType === "INDIVIDUAL_DOCTOR"
    ? hasReferenceData(catalog.specialities, providerType)
      && hasReferenceData(catalog.services, providerType)
      && hasReferenceData(catalog.facilities, providerType)
      && hasReferenceData(catalog.languages, providerType)
      && hasReferenceData(catalog.countries, providerType)
      && hasReferenceData(catalog.states, providerType)
      && hasReferenceData(catalog.medicalCouncils, providerType)
    : hasReferenceData(catalog.services, providerType)
      && hasReferenceData(catalog.facilities, providerType)
      && hasReferenceData(catalog.languages, providerType)
      && hasReferenceData(catalog.countries, providerType)
      && hasReferenceData(catalog.states, providerType);
}

export function ProviderOnboardingPage({ type }: { type?: "doctor" | "clinic" | "hospital" }) {
  const navigate = useNavigate();
  const params = useParams<{ applicationId?: string; step?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const routeProviderType = type ? providerTypeFromRoute(type) : undefined;
  const [providerType, setProviderType] = useState<ProviderType>(() => routeProviderType ?? "INDIVIDUAL_DOCTOR");
  const tokenStorageKey = routeProviderType ? `${TOKEN_KEY}.${routeProviderType}` : TOKEN_KEY;
  const copy = providerTypeConfig[providerType];
  const activeStep = params.step ?? searchParams.get("step") ?? "account";
  const [token, setToken] = useState(() => (routeProviderType ? "" : readStoredToken(TOKEN_KEYS)));
  const [application, setApplication] = useState<ProviderApplication | null>(null);
  const [draft, setDraft] = useState<ProviderApplicationPayload>(() => blankDraft(providerType));
  const [account, setAccount] = useState<AccountStepValues>(() => blankAccountStepValues());
  const [accountHydrated, setAccountHydrated] = useState(false);
  const [contactVerification, setContactVerification] = useState<ContactVerificationStatus | null>(null);
  const [emailVerification, setEmailVerification] = useState<VerificationCodeState>(() => ({ code: "", requestMessage: "", resendAfterSeconds: null, devCode: null }));
  const [phoneVerification, setPhoneVerification] = useState<VerificationCodeState>(() => ({ code: "", requestMessage: "", resendAfterSeconds: null, devCode: null }));
  const [verificationBusy, setVerificationBusy] = useState(false);
  const [statusMessage, setStatusMessage] = useState("Start with your account details. Progress saves as you go.");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [preview, setPreview] = useState<ProviderPreview | null>(null);
  const [referenceCatalog, setReferenceCatalog] = useState<DiscoverReferenceCatalog>(() => emptyReferenceCatalog());
  const [referenceCatalogLoaded, setReferenceCatalogLoaded] = useState(false);
  const [referenceCatalogError, setReferenceCatalogError] = useState<string | null>(null);
  const [changeRequests, setChangeRequests] = useState<Array<{ id: string; reviewerMessage: string | null; providerResponseNote: string | null; requestedSections: string[]; resolved: boolean }>>([]);
  const [responseNote, setResponseNote] = useState("");
  const latestApplicationRef = useRef<ProviderApplication | null>(null);
  const latestDraftRef = useRef<ProviderApplicationPayload>(draft);
  const latestFormStateRef = useRef<ProviderApplicationPayload>(draft);
  const latestVersionRef = useRef<number | null>(null);
  const latestTokenRef = useRef(token);
  const savedDraftSnapshotRef = useRef(draftSnapshot(draft));
  const dirtyRevisionRef = useRef(0);
  const lastPersistedRevisionRef = useRef(0);
  const saveInFlightRef = useRef(false);
  const pendingSaveRef = useRef<SaveReason | null>(null);
  const queuedSaveRef = useRef<SaveReason | null>(null);
  const autosaveTimeoutRef = useRef<number | null>(null);
  const mountedRef = useRef(true);
  const savePromiseRef = useRef<Promise<boolean> | null>(null);

  useEffect(() => {
    if (!routeProviderType) return;
    setProviderType(routeProviderType);
    setToken("");
    setApplication(null);
    setDraft(blankDraft(routeProviderType));
    setAccount(blankAccountStepValues());
    setAccountHydrated(false);
    setContactVerification(null);
    setPreview(null);
    setChangeRequests([]);
    setResponseNote("");
    setStatusMessage("Start with your account details. Progress saves as you go.");
    setError(null);
  }, [routeProviderType]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (autosaveTimeoutRef.current != null) {
        window.clearTimeout(autosaveTimeoutRef.current);
        autosaveTimeoutRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    latestDraftRef.current = draft;
    latestFormStateRef.current = draft;
  }, [draft]);

  useEffect(() => {
    latestApplicationRef.current = application;
    latestVersionRef.current = application?.version ?? null;
  }, [application]);

  useEffect(() => {
    setContactVerification(application?.contactVerification ?? null);
  }, [application]);

  useEffect(() => {
    latestTokenRef.current = token;
  }, [token]);

  useEffect(() => {
    let cancelled = false;
    setReferenceCatalogLoaded(false);
    setReferenceCatalogError(null);
    void loadDiscoverReferenceCatalog()
      .then((catalog) => {
        if (cancelled) return;
        setReferenceCatalog(catalog);
        setReferenceCatalogLoaded(true);
      })
      .catch((ex) => {
        if (cancelled) return;
        setReferenceCatalog(emptyReferenceCatalog());
        setReferenceCatalogLoaded(true);
        setReferenceCatalogError(ex instanceof Error ? ex.message : "Reference data could not be loaded.");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (routeProviderType && !params.applicationId) {
      return;
    }
    if (!token) return;
    let cancelled = false;
    setAccountHydrated(false);
    loadProviderApplication(token)
      .then((result) => {
        if (cancelled) return;
        if (params.applicationId && result.id !== params.applicationId) {
          setError("This onboarding link does not match the stored application.");
          return;
        }
        if (routeProviderType && result.providerType !== routeProviderType) {
          setError("This registration link does not match the stored application type.");
          return;
        }
        setProviderType(result.providerType);
        setApplication(result);
        const normalizedDraft = toDraftPayload(result);
        const hydratedAccount = mapAccountStepValues(result);
        setDraft(normalizedDraft);
        setAccount(hydratedAccount);
        setAccountHydrated(true);
        latestDraftRef.current = normalizedDraft;
        latestFormStateRef.current = normalizedDraft;
        latestApplicationRef.current = result;
        latestVersionRef.current = result.version;
        savedDraftSnapshotRef.current = draftSnapshot(result);
        dirtyRevisionRef.current = 0;
        lastPersistedRevisionRef.current = 0;
        setStatusMessage(`Draft resumed. Last saved ${new Date(result.lastSavedAt).toLocaleString()}.`);
        void loadProviderChangeRequests(result.id, token)
          .then((items) => setChangeRequests(items.map((item) => ({
            id: item.id,
            reviewerMessage: item.reviewerMessage,
            providerResponseNote: item.providerResponseNote,
            requestedSections: item.requestedSections,
            resolved: item.resolved,
          }))))
          .catch(() => setChangeRequests([]));
      })
      .catch(() => {
        if (cancelled) return;
        if (routeProviderType) {
          localStorage.removeItem(tokenStorageKey);
        } else {
          localStorage.removeItem(`${TOKEN_KEY}.INDIVIDUAL_DOCTOR`);
          localStorage.removeItem(`${TOKEN_KEY}.CLINIC`);
          localStorage.removeItem(`${TOKEN_KEY}.HOSPITAL`);
          localStorage.removeItem(TOKEN_KEY);
        }
        setToken("");
      });
    return () => {
      cancelled = true;
    };
  }, [token, params.applicationId, tokenStorageKey, routeProviderType]);

  useEffect(() => {
    if (!application || !token || !accountHydrated || !isApplicationEditable(application.status)) return;
    if (draftSnapshot(draft) === savedDraftSnapshotRef.current) return;
    if (dirtyRevisionRef.current <= lastPersistedRevisionRef.current) return;
    if (autosaveTimeoutRef.current != null) {
      window.clearTimeout(autosaveTimeoutRef.current);
    }
    autosaveTimeoutRef.current = window.setTimeout(() => {
      autosaveTimeoutRef.current = null;
      void requestSave("autosave", { showMessage: false });
    }, 900);
    return () => {
      if (autosaveTimeoutRef.current != null) {
        window.clearTimeout(autosaveTimeoutRef.current);
        autosaveTimeoutRef.current = null;
      }
    };
  }, [draft, application, token, accountHydrated]);

  const hydratedAccount = useMemo(
    () => (accountHydrated || !application ? account : mapAccountStepValues(application)),
    [account, accountHydrated, application],
  );
  const completionPercent = application?.completionPercent ?? estimateCompletion(draft, hydratedAccount, providerType, referenceCatalog);
  const missingItems = application?.missingItems ?? clientMissingItems(draft, hydratedAccount, providerType);
  const currentStepIndex = Math.max(0, steps.findIndex(([id]) => id === activeStep));
  const currentStepId = (steps[currentStepIndex]?.[0] ?? "account") as ProviderOnboardingStepId;
  const contactSatisfied = contactVerificationSatisfied(application, contactVerification);
  const validation = useMemo(
    () => validateDraft(draft, hydratedAccount, providerType, referenceCatalog),
    [draft, hydratedAccount, providerType, referenceCatalog],
  );
  const requiredRemainingCount = validation.requiredRemaining + (contactSatisfied ? 0 : 1);
  const unsavedChanges = Boolean(application) && draftSnapshot(draft) !== savedDraftSnapshotRef.current;
  const conflict = Boolean(error && error.toLowerCase().includes("another session"));
  const verificationPending = application ? !contactSatisfied : false;
  const applicationEditable = isApplicationEditable(application?.status);
  const readOnlyApplication = Boolean(application && !applicationEditable);
  const currentStepComplete = stepCompletion(currentStepId, validation, application, draft, contactSatisfied, providerType);
  const currentStepReferenceReady = stepHasRequiredReferenceData(currentStepId, providerType, referenceCatalog);
  const submissionReadyForReferenceData = submissionReferenceDataReady(providerType, referenceCatalog);
  const currentStepCanContinue = Boolean(applicationEditable && !saving && !verificationBusy && !readOnlyApplication && currentStepComplete && currentStepReferenceReady);
  const lockedStepIndex = useMemo(() => {
    const firstIncomplete = steps.findIndex(([stepId]) => !stepCompletion(stepId, validation, application, draft, contactSatisfied, providerType));
    return firstIncomplete === -1 ? steps.length - 1 : Math.max(0, firstIncomplete);
  }, [application, contactSatisfied, draft, validation, providerType]);
  const showStepper = activeStep !== "preview";
  const stepStates: ProviderOnboardingStepState[] = useMemo(() => {
    return steps.map(([id, label], index) => {
      const complete = stepCompletion(id, validation, application, draft, contactSatisfied, providerType);
      const current = id === activeStep;
      const disabled = !readOnlyApplication && index > lockedStepIndex && !current;
      const summary = id === "account"
        ? contactSatisfied
          ? contactVerificationSummary(contactVerification ?? application?.contactVerification ?? null, hydratedAccount.email, hydratedAccount.phone)
          : "Contact verification required"
        : id === "organisation"
          ? `${requiredRemainingCount} required items remaining`
          : id === "professional"
            ? `${providerTypeLabel(providerType)} details`
            : id === "services"
              ? `${draft.services?.length ?? 0} services selected`
              : id === "locations"
                ? `${draft.locations?.length ?? 0} location${draft.locations?.length === 1 ? "" : "s"}`
                : id === "branding"
                  ? `${application?.documents.length ?? 0} documents uploaded`
                  : id === "preview"
                    ? "Preview only"
                    : application?.status === "CHANGES_REQUESTED"
                      ? "Resubmission ready"
                      : "Submission review";
      return {
        id,
        label,
        completed: complete,
        current,
        disabled: disabled && !current,
        errorCount: id === activeStep ? Object.values(validation.errors).filter(Boolean).length : 0,
        summary,
      };
    });
  }, [activeStep, application, contactSatisfied, contactVerification, draft, hydratedAccount.email, hydratedAccount.phone, lockedStepIndex, providerType, requiredRemainingCount, validation]);
  const stepPosition = `${currentStepIndex + 1} of ${steps.length}`;
  const specialitySelectOptions = useMemo(() => toOptionItems(referenceCatalog.specialities, providerType), [referenceCatalog.specialities, providerType]);
  const serviceSelectOptions = useMemo(() => toServiceItems(referenceCatalog.services, providerType), [referenceCatalog.services, providerType]);
  const facilitySelectOptions = useMemo(() => toOptionItems(referenceCatalog.facilities, providerType), [referenceCatalog.facilities, providerType]);
  const languageSelectOptions = useMemo(() => toOptionItems(referenceCatalog.languages, providerType), [referenceCatalog.languages, providerType]);
  const countrySelectOptions = useMemo(() => toOptionItems(referenceCatalog.countries, providerType), [referenceCatalog.countries, providerType]);
  const stateSelectOptions = useMemo(() => toOptionItems(referenceCatalog.states, providerType), [referenceCatalog.states, providerType]);
  const medicalCouncilSelectOptions = useMemo(() => toOptionItems(referenceCatalog.medicalCouncils, providerType), [referenceCatalog.medicalCouncils, providerType]);
  const ownershipSelectOptions = useMemo(() => ownershipOptions.map((item) => ({ value: item, label: item })), []);

  function markDraftChanged(nextDraft: ProviderApplicationPayload) {
    latestDraftRef.current = nextDraft;
    latestFormStateRef.current = nextDraft;
    if (draftSnapshot(nextDraft) !== savedDraftSnapshotRef.current) {
      dirtyRevisionRef.current += 1;
    }
  }

  function clearAutosaveTimer() {
    if (autosaveTimeoutRef.current != null) {
      window.clearTimeout(autosaveTimeoutRef.current);
      autosaveTimeoutRef.current = null;
    }
  }

  function traceSave(reason: SaveReason, phase: "requested" | "queued" | "start" | "skipped" | "success" | "failed", details: Record<string, unknown> = {}) {
    if (import.meta.env.DEV) {
      console.debug("[provider-onboarding-save]", phase, reason, details);
    }
  }

  async function refreshChangeRequests(applicationId: string, onboardingToken: string) {
    try {
      const items = await loadProviderChangeRequests(applicationId, onboardingToken);
      if (!mountedRef.current) return;
      setChangeRequests(items.map((item) => ({
        id: item.id,
        reviewerMessage: item.reviewerMessage,
        providerResponseNote: item.providerResponseNote,
        requestedSections: item.requestedSections,
        resolved: item.resolved,
      })));
    } catch {
      if (mountedRef.current) {
        setChangeRequests([]);
      }
    }
  }

  async function requestSave(reason: SaveReason, options: { showMessage?: boolean } = {}) {
    if (!application || !token) return true;
    if (!applicationEditable) {
      traceSave(reason, "skipped", { snapshot: "read-only" });
      return true;
    }
    const showMessage = options.showMessage ?? reason !== "autosave";
    if (reason !== "autosave") {
      clearAutosaveTimer();
    }
    const payloadAtSchedule = latestFormStateRef.current;
    traceSave(reason, "requested", {
      inFlight: Boolean(savePromiseRef.current),
      revision: dirtyRevisionRef.current,
      version: latestVersionRef.current ?? application.version,
    });
    if (reason === "autosave" && !accountHydrated) {
      traceSave(reason, "skipped", { snapshot: "hydration-pending" });
      return true;
    }
    if (draftSnapshot(payloadAtSchedule) === savedDraftSnapshotRef.current && reason === "autosave") {
      traceSave(reason, "skipped", { snapshot: "clean" });
      return true;
    }
    if (savePromiseRef.current) {
      if (reason !== "autosave") {
        queuedSaveRef.current = reason;
      }
      traceSave(reason, "queued", { queued: queuedSaveRef.current ?? null });
      return savePromiseRef.current;
    }

    const run = async (): Promise<boolean> => {
      let nextReason: SaveReason | null = reason;
      let saved = true;
      while (nextReason) {
        const currentReason = nextReason;
        nextReason = null;
        const currentApplication = latestApplicationRef.current;
        const currentToken = latestTokenRef.current;
        if (!currentApplication || !currentToken) {
          break;
        }

        const payload = latestFormStateRef.current;
        if (draftSnapshot(payload) === savedDraftSnapshotRef.current) {
          traceSave(currentReason, "skipped", {
            snapshot: "clean",
            version: latestVersionRef.current ?? currentApplication.version,
          });
          if (showMessage && mountedRef.current && currentReason !== "autosave") {
            setStatusMessage("Draft saved.");
          }
        } else {
          saveInFlightRef.current = true;
          pendingSaveRef.current = currentReason;
          const revisionAtStart = dirtyRevisionRef.current;
          traceSave(currentReason, "start", {
            revision: revisionAtStart,
            version: latestVersionRef.current ?? currentApplication.version,
          });
          if (mountedRef.current) {
            setSaving(true);
            setError(null);
          }
          try {
            const updated = await updateProviderApplication(currentApplication.id, currentToken, {
              ...payload,
              version: latestVersionRef.current ?? currentApplication.version,
            });
            const normalizedDraft = toDraftPayload(updated);
            const hydratedAccount = mapAccountStepValues(updated);
            latestApplicationRef.current = updated;
            latestVersionRef.current = updated.version;
            savedDraftSnapshotRef.current = draftSnapshot(updated);
            setAccount(hydratedAccount);
            setAccountHydrated(true);

            if (dirtyRevisionRef.current === revisionAtStart) {
              latestDraftRef.current = normalizedDraft;
              latestFormStateRef.current = normalizedDraft;
              lastPersistedRevisionRef.current = dirtyRevisionRef.current;
              if (mountedRef.current) {
                setApplication(updated);
                setDraft(normalizedDraft);
              }
            } else {
              const mergedDraft = { ...latestFormStateRef.current, version: updated.version };
              latestDraftRef.current = mergedDraft;
              latestFormStateRef.current = mergedDraft;
              lastPersistedRevisionRef.current = revisionAtStart;
              if (mountedRef.current) {
                setApplication(updated);
                setDraft(mergedDraft);
              }
            }

            void refreshChangeRequests(updated.id, currentToken);
            if (showMessage && mountedRef.current && currentReason !== "autosave") {
              setStatusMessage("Draft saved.");
            }
            traceSave(currentReason, "success", {
              version: updated.version,
              status: updated.status,
            });
          } catch (ex) {
            saved = false;
            traceSave(currentReason, "failed", {
              error: ex instanceof Error ? ex.message : "Draft could not be saved.",
            });
            if (mountedRef.current) {
              setError(ex instanceof Error ? ex.message : "Draft could not be saved.");
            }
            break;
          } finally {
            saveInFlightRef.current = false;
            pendingSaveRef.current = null;
            if (mountedRef.current) {
              setSaving(false);
            }
          }
        }

        if (queuedSaveRef.current) {
          nextReason = queuedSaveRef.current;
          queuedSaveRef.current = null;
        }
      }
      return saved;
    };

    const promise = run().finally(() => {
      if (savePromiseRef.current === promise) {
        savePromiseRef.current = null;
      }
    });
    savePromiseRef.current = promise;
    return promise;
  }

  async function goToStep(step: string, reason: SaveReason = "navigation") {
    const saved = applicationEditable
      ? await requestSave(reason, { showMessage: false })
      : true;
    if (!saved || !mountedRef.current) return;
    if (params.applicationId) {
      navigate(`/provider/onboarding/${params.applicationId}/${step}`);
      return;
    }
    setSearchParams({ step });
  }

  function patchDraft(patch: ProviderApplicationPayload) {
    if (!applicationEditable) return;
    setDraft((current) => {
      const next = { ...current, ...patch };
      markDraftChanged(next);
      return next;
    });
  }

  async function createDraft(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const created = await createProviderApplication({ providerType: routeProviderType ?? providerType, ...account });
      if (created.onboardingToken) {
        localStorage.setItem(tokenStorageKey, created.onboardingToken);
        localStorage.removeItem(TOKEN_KEY);
        setToken(created.onboardingToken);
      }
      setApplication(created);
      const normalizedDraft = toDraftPayload(created);
      const hydratedAccount = mapAccountStepValues(created);
      setDraft(normalizedDraft);
      setAccount(hydratedAccount);
      setAccountHydrated(true);
      latestDraftRef.current = normalizedDraft;
      latestFormStateRef.current = normalizedDraft;
      latestApplicationRef.current = created;
      latestVersionRef.current = created.version;
      savedDraftSnapshotRef.current = draftSnapshot(created);
      dirtyRevisionRef.current = 0;
      lastPersistedRevisionRef.current = 0;
      setStatusMessage("Draft created. Continue completing your profile.");
      navigate(`/provider/onboarding/${created.id}/organisation`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not create your provider draft.");
    } finally {
      setSaving(false);
    }
  }

  async function uploadDocument(type: ProviderDocumentType, event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file || !application || !token) return;
    if (!applicationEditable) return;
    if (!["image/png", "image/jpeg", "application/pdf"].includes(file.type)) {
      setError("Upload PNG, JPEG, or PDF files only.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await uploadProviderDocument(application.id, token, type, file);
      const refreshed = await loadProviderApplication(token);
      const normalizedDraft = toDraftPayload(refreshed);
      const hydratedAccount = mapAccountStepValues(refreshed);
      setApplication(refreshed);
      setDraft(normalizedDraft);
      setAccount(hydratedAccount);
      setAccountHydrated(true);
      latestDraftRef.current = normalizedDraft;
      latestFormStateRef.current = normalizedDraft;
      latestApplicationRef.current = refreshed;
      latestVersionRef.current = refreshed.version;
      savedDraftSnapshotRef.current = draftSnapshot(refreshed);
      dirtyRevisionRef.current = 0;
      lastPersistedRevisionRef.current = 0;
      setProviderType(refreshed.providerType);
      void refreshChangeRequests(refreshed.id, token);
      setStatusMessage(`${file.name} uploaded.`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Document upload failed.");
    } finally {
      setSaving(false);
    }
  }

  async function loadPreviewPanel() {
    if (!application || !token) return;
    const saved = await requestSave("preview", { showMessage: false });
    if (!saved) return;
    try {
      const result = await loadProviderPreview(application.id, token);
      setPreview(result);
      setStatusMessage("Preview refreshed.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Preview could not be loaded.");
    }
  }

  async function refreshVerificationState(message: string) {
    if (!token) return;
    const refreshed = await loadProviderApplication(token);
    const normalizedDraft = toDraftPayload(refreshed);
    const hydratedAccount = mapAccountStepValues(refreshed);
    setApplication(refreshed);
    setDraft(normalizedDraft);
    setAccount(hydratedAccount);
    setAccountHydrated(true);
    latestDraftRef.current = normalizedDraft;
    latestFormStateRef.current = normalizedDraft;
    latestApplicationRef.current = refreshed;
    latestVersionRef.current = refreshed.version;
    savedDraftSnapshotRef.current = draftSnapshot(refreshed);
    dirtyRevisionRef.current = 0;
    lastPersistedRevisionRef.current = 0;
    setStatusMessage(message);
  }

  async function requestContactVerification(channel: "email" | "phone") {
    if (!application || !token) return;
    if (!applicationEditable) return;
    setVerificationBusy(true);
    setError(null);
    try {
      const result = channel === "email"
        ? await requestProviderEmailVerification(application.id, token)
        : await requestProviderPhoneVerification(application.id, token);
      await refreshVerificationState(result.message);
      if (channel === "email") {
        setEmailVerification((current) => ({ ...current, requestMessage: result.message, devCode: result.devCode, resendAfterSeconds: result.resendAfterSeconds }));
      } else {
        setPhoneVerification((current) => ({ ...current, requestMessage: result.message, devCode: result.devCode, resendAfterSeconds: result.resendAfterSeconds }));
      }
      setStatusMessage(result.message);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Verification code could not be requested.");
    } finally {
      setVerificationBusy(false);
    }
  }

  async function verifyContact(channel: "email" | "phone") {
    if (!application || !token) return;
    if (!applicationEditable) return;
    const code = (channel === "email" ? emailVerification.code : phoneVerification.code).trim();
    if (!code) {
      setError("Enter the verification code.");
      return;
    }
    setVerificationBusy(true);
    setError(null);
    try {
      const result = channel === "email"
        ? await verifyProviderEmail(application.id, token, code)
        : await verifyProviderPhone(application.id, token, code);
      if (channel === "email") {
        setEmailVerification((current) => ({ ...current, code: "", requestMessage: "", devCode: null }));
      } else {
        setPhoneVerification((current) => ({ ...current, code: "", requestMessage: "", devCode: null }));
      }
      await refreshVerificationState(channel === "email" ? "Email verified." : "Phone verified.");
      setContactVerification(result);
      setStatusMessage(channel === "email" ? "Email verified." : "Phone verified.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Verification could not be completed.");
    } finally {
      setVerificationBusy(false);
    }
  }

  function verificationPanel() {
    const contact = contactVerification ?? application?.contactVerification ?? null;
    const emailStatus = contact?.emailStatus ?? (account.email ? "NOT_VERIFIED" : "NOT_VERIFIED");
    const phoneStatus = contact?.phoneStatus ?? (account.phone ? "NOT_VERIFIED" : "NOT_VERIFIED");
    const emailVerified = emailStatus === "VERIFIED";
    const phoneVerified = phoneStatus === "VERIFIED";
    const emailPending = emailStatus === "PENDING";
    const phonePending = phoneStatus === "PENDING";
    return (
      <div className="verification-panel">
        <div className="verification-row">
          <div>
            <strong>Email</strong>
            <p>{account.email || application?.email || "Email not set"}</p>
            <small>{emailVerified ? "Verified" : emailPending ? "Verification sent" : "Not verified"}</small>
          </div>
          <div className="cta-row">
            <button className="secondary-button" type="button" onClick={() => void requestContactVerification("email")} disabled={verificationBusy || !account.email}>
              {emailPending ? "Resend email" : "Send verification email"}
            </button>
          </div>
        </div>
        <div className="verification-row">
          <div>
            <strong>Phone</strong>
            <p>{account.phone || application?.phone || "Phone not set"}</p>
            <small>{phoneVerified ? "Verified" : phonePending ? "OTP sent" : "Not verified"}</small>
          </div>
          <div className="cta-row">
            <button className="secondary-button" type="button" onClick={() => void requestContactVerification("phone")} disabled={verificationBusy || !account.phone}>
              {phonePending ? "Resend OTP" : "Send phone OTP"}
            </button>
          </div>
        </div>
        {!emailVerified ? (
          <label>
            Email code
            <input value={emailVerification.code} onChange={(event) => setEmailVerification((current) => ({ ...current, code: event.target.value }))} placeholder="Enter verification code" />
          </label>
        ) : null}
        {!phoneVerified ? (
          <label>
            OTP
            <input value={phoneVerification.code} onChange={(event) => setPhoneVerification((current) => ({ ...current, code: event.target.value }))} placeholder="Enter 6-digit OTP" />
          </label>
        ) : null}
        <div className="cta-row">
          {!emailVerified ? <button className="primary-button" type="button" onClick={() => void verifyContact("email")} disabled={verificationBusy || !emailVerification.code.trim()}>Verify email</button> : null}
          {!phoneVerified ? <button className="primary-button" type="button" onClick={() => void verifyContact("phone")} disabled={verificationBusy || !phoneVerification.code.trim()}>Verify phone</button> : null}
        </div>
        <small>{contactSatisfied ? "Contact verification complete." : "Verify at least one contact method before submitting."}</small>
        {emailVerification.devCode ? <small>Dev email code: {emailVerification.devCode}</small> : null}
        {phoneVerification.devCode ? <small>Dev OTP: {phoneVerification.devCode}</small> : null}
      </div>
    );
  }

  async function submit() {
    if (!application || !token) return;
    if (!submissionReadyForReferenceData) {
      setError("REFERENCE_DATA_UNAVAILABLE");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const submitted = application.status === "CHANGES_REQUESTED"
        ? await resubmitProviderApplication(application.id, token, responseNote || undefined)
        : await submitProviderApplication(application.id, token);
      const normalizedDraft = toDraftPayload(submitted);
      const hydratedAccount = mapAccountStepValues(submitted);
      setApplication(submitted);
      setDraft(normalizedDraft);
      setAccount(hydratedAccount);
      setAccountHydrated(true);
      latestDraftRef.current = normalizedDraft;
      latestFormStateRef.current = normalizedDraft;
      latestApplicationRef.current = submitted;
      latestVersionRef.current = submitted.version;
      savedDraftSnapshotRef.current = draftSnapshot(submitted);
      dirtyRevisionRef.current = 0;
      lastPersistedRevisionRef.current = 0;
      void refreshChangeRequests(submitted.id, token);
      setStatusMessage("Submitted for verification. You can return here to track review progress.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Submission could not be completed.");
    } finally {
      setSaving(false);
    }
  }

  const providerPublicId = application?.id ?? "";
  const previewBranding = preview?.branding;
  const previewLogoPath = previewBranding?.logoDocumentId ? providerDocumentContentPath(providerPublicId, previewBranding.logoDocumentId) : null;
  const previewCoverPath = previewBranding?.coverImageDocumentId ? providerDocumentContentPath(providerPublicId, previewBranding.coverImageDocumentId) : null;
  const previewDoctorPhotoPath = previewBranding?.doctorPhotoDocumentId ? providerDocumentContentPath(providerPublicId, previewBranding.doctorPhotoDocumentId) : null;
  const previewGalleryPaths = (previewBranding?.galleryDocumentIds ?? []).map((documentId) => providerDocumentContentPath(providerPublicId, documentId));
  const isPreviewStep = activeStep === "preview";
  const previewPrimaryLocation = primaryLocation(draft);
  const primarySpeciality = preview?.specialities[0] ?? draft.specialities?.[0] ?? null;
  const consultationFeeLabel = formatCurrency(draft.consultationFee);
  const enabledServices = draft.services?.filter((item) => item.enabled !== false) ?? [];
  const hasTeleconsultation = Boolean(draft.onlineConsultation || enabledServices.some((item) => item.serviceType === "TELECONSULTATION"));
  const hasInPersonConsultation = enabledServices.some((item) => item.serviceType === "CONSULTATION");
  const publicPhone = contactVerification?.phoneStatus === "VERIFIED" ? (account.phone || application?.phone || draft.phone || "").trim() : "";
  const hasPublicPhone = publicPhone.length >= 10;
  const heroLocation = [previewPrimaryLocation?.city, previewPrimaryLocation?.state].filter(Boolean).join(", ") || preview?.locationSummary || null;
  const heroSummary = [
    draft.qualification?.trim() || null,
    primarySpeciality,
  ].filter(Boolean).join(" • ");
  const tagline = previewBranding?.tagline?.trim() || draft.branding?.tagline?.trim() || null;
  const serviceLabels = preview?.services?.length
    ? preview.services
    : enabledServices.map((item) => item.label);
  const locationFacilityValues = validation.facilities.filter((item) => item.trim().toLowerCase() !== "online appointment");
  const consultationModes = [
    hasInPersonConsultation ? "In-person" : null,
    hasTeleconsultation ? "Teleconsultation" : null,
  ].filter((item): item is string => Boolean(item));
  const previewGalleryItems = useMemo(() => {
    const name = preview?.displayName ?? draft.displayName ?? providerTypeLabel(providerType);
    const items: PreviewGalleryItem[] = [];
    const seen = new Set<string>();
    const push = (url: string | null, caption: string, alt: string) => {
      if (!url || seen.has(url)) return;
      seen.add(url);
      items.push({ url, caption, alt });
    };

    previewGalleryPaths.forEach((url, index) => {
      push(url, `Clinic image ${index + 1}`, `${name} clinic image ${index + 1}`);
    });

    return items;
  }, [draft.displayName, preview?.displayName, previewGalleryPaths]);
  const previewChecklistItems = useMemo(() => {
    const items: PreviewChecklistItem[] = [];
    const add = (label: string, step: ProviderOnboardingStepId, detail?: string) => {
      items.push({ label, step, detail });
    };

    if (!contactSatisfied) add("Contact information", "account", "Verify an email address or mobile number.");
    if (!draft.registrationNumber?.trim()) add(copy.registrationLabel, "organisation");
    if (providerType === "INDIVIDUAL_DOCTOR" && !draft.medicalCouncil?.trim()) add("Medical council registration", "professional");
    if (!draft.biography?.trim()) add("Biography", "professional");
    if (!validation.languages.length) add("Languages", "professional");
    if (!hasEnabledServices(draft.services)) add("Services", "services");
    if (!validation.facilities.length) add("Facilities", "professional");
    if (draft.consultationFee == null || Number.isNaN(draft.consultationFee)) add("Consultation fee", "professional");
    if (!previewPrimaryLocation?.address?.trim() || !previewPrimaryLocation.city?.trim() || !previewPrimaryLocation.state?.trim() || !previewPrimaryLocation.country?.trim() || !previewPrimaryLocation.pinCode?.trim()) {
      add("Location details", "locations");
    }
    if (!previewPrimaryLocation?.workingHours?.trim()) add("Clinic timings", "locations");
    if (!previewDoctorPhotoPath && providerType === "INDIVIDUAL_DOCTOR") add("Profile photo", "branding");
    if (!previewLogoPath && providerType !== "INDIVIDUAL_DOCTOR") add("Logo", "branding");
    if (!previewGalleryItems.length) add("Clinic gallery images", "branding", "Add real clinic or facility images for patients to preview your space.");

    return items;
  }, [
    contactSatisfied,
    copy.registrationLabel,
    draft.biography,
    draft.consultationFee,
    draft.medicalCouncil,
    draft.registrationNumber,
    draft.services,
    previewDoctorPhotoPath,
    previewGalleryItems.length,
    previewLogoPath,
    previewPrimaryLocation,
    providerType,
    validation.facilities.length,
    validation.languages.length,
  ]);
  const returnToEditingStep = previewChecklistItems[0]?.step ?? (providerType === "INDIVIDUAL_DOCTOR" ? "professional" : "organisation");
  const showFacilitiesSection = locationFacilityValues.length > 0 && (providerType !== "INDIVIDUAL_DOCTOR" || Boolean(previewPrimaryLocation));
  const facilitySectionTitle = providerType === "INDIVIDUAL_DOCTOR" ? "Clinic facilities at this location" : "Facilities";
  const professionalInformation = [
    draft.registrationNumber?.trim() ? { label: "Registration Number", value: draft.registrationNumber.trim() } : null,
    draft.medicalCouncil?.trim() ? { label: "Medical Council", value: draft.medicalCouncil.trim() } : null,
    draft.qualification?.trim() ? { label: "Qualifications", value: draft.qualification.trim(), wide: true } : null,
    draft.yearsOfExperience != null && !Number.isNaN(draft.yearsOfExperience) ? { label: "Experience", value: `${draft.yearsOfExperience} years` } : null,
    consultationFeeLabel ? { label: "Consultation Fee", value: consultationFeeLabel } : null,
    primarySpeciality ? { label: "Specialty", value: primarySpeciality } : null,
    validation.languages.length ? { label: "Languages", value: validation.languages.join(" • "), wide: true } : null,
    previewPrimaryLocation?.workingHours?.trim() ? { label: "Working Hours", value: previewPrimaryLocation.workingHours.trim(), wide: true } : null,
  ].filter((item): item is ProfessionalInformationItem => Boolean(item));
  const trustIndicators = [
    application?.status === "PUBLISHED" ? "Published on Jeevanam Discover" : null,
    contactVerification?.emailStatus === "VERIFIED" ? "Email verified" : null,
    contactVerification?.phoneStatus === "VERIFIED" ? "Phone verified" : null,
  ].filter((item): item is string => Boolean(item));
  const readinessCompletedAreas = [
    professionalInformation.length ? "Professional information" : null,
    hasEnabledServices(draft.services) ? "Services" : null,
    previewPrimaryLocation?.address?.trim() ? "Location" : null,
    previewDoctorPhotoPath || previewLogoPath ? "Images" : null,
    contactSatisfied ? "Contact details" : null,
  ].filter((item): item is string => Boolean(item));
  const locationAddress = [
    previewPrimaryLocation?.address,
    previewPrimaryLocation?.city,
    previewPrimaryLocation?.state,
    previewPrimaryLocation?.country,
    previewPrimaryLocation?.pinCode,
  ].filter(Boolean).join(", ");
  const trustSectionTitle = trustIndicators.some((item) => item === "Published on Jeevanam Discover")
    ? "Trust and Verification"
    : "Verified contact details";

  return (
    <section className={`page-section provider-portal-page${isPreviewStep ? " provider-portal-page--preview" : ""}`}>
      {!isPreviewStep ? (
        <div className="provider-portal-hero">
          <div>
            <span className="eyebrow">{copy.eyebrow}</span>
            <h1>{copy.title}</h1>
            <p>Complete your profile step by step, upload required documents, preview your public presence, and submit for Jeevanam verification.</p>
          </div>
          <aside className="resume-card">
            <strong>{application ? application.referenceNumber : "New application"}</strong>
            <span>{application ? `${providerReferencePrefix(application.providerType)} reference` : `${providerReferencePrefix(providerType)} reference prefix`}</span>
            <span>{application?.status.replaceAll("_", " ") ?? "Draft not created"}</span>
            <div className="progress-track" aria-label={`${completionPercent}% complete`}>
              <span style={{ width: `${completionPercent}%` }} />
            </div>
            <small>{completionPercent}% Complete</small>
          </aside>
        </div>
      ) : null}

      <div className={`provider-portal-layout${activeStep === "preview" ? " provider-portal-layout--preview" : ""}`}>
        {showStepper ? (
          <ProviderOnboardingStepper
            steps={stepStates}
            onSelect={(stepId) => {
              void goToStep(stepId, "route-transition");
            }}
          />
        ) : null}

        <div className={`provider-workspace${activeStep === "preview" ? " provider-workspace--preview" : ""}`}>
          {referenceCatalogError ? (
            <div className="portal-warning" role="status" aria-live="polite">
              <strong>Reference data is partially unavailable.</strong>
              <span>{referenceCatalogError}</span>
            </div>
          ) : null}
          {!isPreviewStep ? (
            <ProviderSaveStatus
              saving={saving}
              statusMessage={statusMessage}
              unsavedChanges={unsavedChanges}
              conflict={conflict}
              autosaveEnabled={application ? applicationEditable : false}
            />
          ) : null}
          {readOnlyApplication && !isPreviewStep ? (
            <div className="provider-readonly-banner" role="status" aria-live="polite">
              <strong>Application submitted</strong>
              <span>Your profile is read-only while Jeevanam reviews it.</span>
              <span>You can edit it again if changes are requested.</span>
            </div>
          ) : null}
          {!isPreviewStep ? (
            <div className="provider-progress-summary" aria-label="Onboarding progress summary">
              <strong>Step {stepPosition}</strong>
              <span>{completionPercent}% complete</span>
              <span>{validation.requiredRemaining} required items remaining</span>
              <span>{application ? `${application.missingItems.length} missing items` : `${validation.requiredRemaining} missing items`}</span>
              <span>{verificationPending ? "Verification pending" : application?.status === "PUBLISHED" ? "Published" : application?.status ?? "Draft"}</span>
            </div>
          ) : null}
          {!isPreviewStep && application && applicationEditable ? <div className="provider-save-actions"><button className="secondary-button" type="button" onClick={() => void requestSave("manual", { showMessage: true })}>Save draft</button></div> : null}
          {error ? <div className="portal-error" role="alert">{error}</div> : null}
          {activeStep === "account" ? (
            !application ? (
              <form className="onboarding-panel" onSubmit={createDraft}>
                <h2>Account and consent</h2>
                <div className="form-grid">
                  <label>Email<input type="email" value={account.email} onChange={(event) => setAccount({ ...account, email: event.target.value })} required /></label>
                  <label>Phone<input value={account.phone} onChange={(event) => setAccount({ ...account, phone: event.target.value })} required /></label>
                  <label>Password<input type="password" value={account.password} onChange={(event) => setAccount({ ...account, password: event.target.value })} required minLength={8} /></label>
                  <div className="verification-note" role="note">
                    Contact verification is recorded before review. Delivery integration will use backend notification events.
                  </div>
                </div>
                <label className="checkbox-row"><input type="checkbox" checked={account.termsAccepted} onChange={(event) => setAccount({ ...account, termsAccepted: event.target.checked })} /> I accept the Terms.</label>
                <label className="checkbox-row"><input type="checkbox" checked={account.privacyAccepted} onChange={(event) => setAccount({ ...account, privacyAccepted: event.target.checked })} /> I accept the Privacy policy.</label>
                <div className="cta-row">
                  <button className="primary-button" type="submit" disabled={saving}>Create draft</button>
                  <Link className="text-button" to={DISCOVER_ROUTES.login.path}>Already have account?</Link>
                </div>
              </form>
            ) : (
              <Panel title="Account and consent">
                <div className="status-messages">
                  <strong>{application.referenceNumber}</strong>
                  <span>{application.email}</span>
                  <span>{application.phone}</span>
                  <span>{contactVerification?.emailStatus === "VERIFIED" ? "Email verified" : contactVerification?.emailStatus === "PENDING" ? "Email verification sent" : "Email not verified"}</span>
                  <span>{contactVerification?.phoneStatus === "VERIFIED" ? "Phone verified" : contactVerification?.phoneStatus === "PENDING" ? "Phone OTP sent" : "Phone not verified"}</span>
                </div>
                <p className="panel-help">Change email or phone here if needed. Saving the draft resets verification for the updated contact details.</p>
                <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
                <div className="form-grid">
                  <FieldShell label="Email" helperText="Used for verification and account recovery." error={validation.errors.account}>
                    <input
                      type="email"
                      aria-invalid={Boolean(validation.errors.account)}
                      value={account.email}
                      onChange={(event) => {
                        const nextEmail = event.target.value;
                        setAccount((current) => ({ ...current, email: nextEmail }));
                        patchDraft({ email: nextEmail });
                      }}
                    />
                  </FieldShell>
                  <FieldShell label="Phone" helperText="Used for OTP delivery and verification." error={validation.errors.account}>
                    <input
                      value={account.phone}
                      aria-invalid={Boolean(validation.errors.account)}
                      onChange={(event) => {
                        const nextPhone = event.target.value;
                        setAccount((current) => ({ ...current, phone: nextPhone }));
                        patchDraft({ phone: nextPhone });
                      }}
                    />
                  </FieldShell>
                </div>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={account.termsAccepted}
                    onChange={(event) => {
                      const nextChecked = event.target.checked;
                      setAccount((current) => ({ ...current, termsAccepted: nextChecked }));
                      patchDraft({ termsAccepted: nextChecked });
                    }}
                  />
                  I accept the Terms.
                </label>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={account.privacyAccepted}
                    onChange={(event) => {
                      const nextChecked = event.target.checked;
                      setAccount((current) => ({ ...current, privacyAccepted: nextChecked }));
                      patchDraft({ privacyAccepted: nextChecked });
                    }}
                  />
                  I accept the Privacy policy.
                </label>
                {verificationPanel()}
                </fieldset>
                <div className="cta-row">
                  <button className="secondary-button" type="button" onClick={() => void goToStep("organisation")} disabled={!currentStepCanContinue}>
                    Continue
                  </button>
                </div>
              </Panel>
            )
          ) : null}

          {activeStep === "organisation" ? (
            <Panel title="Organisation">
              <p className="panel-help">Use the official provider identity that patients will see on the public profile.</p>
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
              <div className="form-grid">
                <FieldShell label={copy.nameLabel} helperText="Displayed publicly on the provider profile." error={validation.errors.displayName}>
                  <input
                    aria-invalid={Boolean(validation.errors.displayName)}
                    value={draft.displayName ?? ""}
                    onChange={(event) => patchDraft({ displayName: event.target.value, legalName: event.target.value })}
                    placeholder={copy.nameLabel}
                  />
                </FieldShell>
                <FieldShell label={copy.registrationLabel} helperText="Official registration document number." error={validation.errors.registrationNumber}>
                  <input
                    aria-invalid={Boolean(validation.errors.registrationNumber)}
                    value={draft.registrationNumber ?? ""}
                    onChange={(event) => patchDraft({ registrationNumber: event.target.value })}
                    placeholder={copy.registrationLabel}
                  />
                </FieldShell>
                {copy.showOrganisationType ? (
                  <FieldShell label="Organisation type" helperText="Describe the practice structure." error={validation.errors.organisationType}>
                    <input
                      aria-invalid={Boolean(validation.errors.organisationType)}
                      value={draft.organisationType ?? ""}
                      onChange={(event) => patchDraft({ organisationType: event.target.value })}
                    placeholder="Group practice, standalone, chain, etc."
                  />
                </FieldShell>
                ) : null}
                <FieldShell label="Website" helperText="Public website. Must include https://." error={validation.errors.website}>
                  <input
                    type="url"
                    aria-invalid={Boolean(validation.errors.website)}
                    value={draft.website ?? ""}
                    onChange={(event) => patchDraft({ website: event.target.value })}
                    placeholder="https://example.com"
                  />
                </FieldShell>
                <FieldShell label="GST" helperText="Optional. Only used for verification." error={validation.errors.gstNumber}>
                  <input
                    aria-invalid={Boolean(validation.errors.gstNumber)}
                    value={draft.gstNumber ?? ""}
                    onChange={(event) => patchDraft({ gstNumber: event.target.value.toUpperCase() })}
                    placeholder="15-character GST number"
                  />
                </FieldShell>
                {copy.showOwnership ? (
                  <ProviderDropdownField
                    label="Ownership"
                    helperText="Select the ownership category."
                    error={validation.errors.ownership}
                    value={draft.ownership ?? ""}
                    options={ownershipSelectOptions}
                    onChange={(value) => patchDraft({ ownership: value })}
                    placeholder="Select ownership"
                    disabled={!applicationEditable}
                  />
                ) : null}
                {copy.showHospitalType ? (
                  <FieldShell label="Hospital type" helperText="Describe the hospital profile type." error={null}>
                    <input
                      value={draft.hospitalType ?? ""}
                      onChange={(event) => patchDraft({ hospitalType: event.target.value })}
                      placeholder="Multi-speciality, super speciality, etc."
                    />
                  </FieldShell>
                ) : null}
              </div>
              </fieldset>
            </Panel>
          ) : null}

          {activeStep === "professional" ? (
            <Panel title="Professional details">
              <p className="panel-help">Add the details that support the public profile and review process.</p>
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
              <div className="form-grid">
                {copy.showQualification ? <>
                  <FieldShell label="Gender" helperText="Optional." error={null}><input value={draft.gender ?? ""} onChange={(event) => patchDraft({ gender: event.target.value })} /></FieldShell>
                  <FieldShell label="Date of birth" helperText="Publicly hidden unless required for verification." error={null}><input type="date" value={draft.dateOfBirth ?? ""} onChange={(event) => patchDraft({ dateOfBirth: event.target.value })} /></FieldShell>
                  <FieldShell label="Qualification" helperText="Displayed publicly on your profile." error={validation.errors.qualification}><input aria-invalid={Boolean(validation.errors.qualification)} value={draft.qualification ?? ""} onChange={(event) => patchDraft({ qualification: event.target.value })} /></FieldShell>
                  {copy.showMedicalCouncil ? (
                    <ProviderDropdownField
                      label="Medical council"
                      helperText="Registration details used for review."
                      error={validation.errors.medicalCouncil}
                      value={draft.medicalCouncil ?? ""}
                      options={medicalCouncilSelectOptions}
                      onChange={(value) => patchDraft({ medicalCouncil: value })}
                      placeholder="Select medical council"
                      disabled={!applicationEditable}
                    />
                  ) : null}
                  <FieldShell label="Experience" helperText="Years of practice." error={validation.errors.yearsOfExperience}><input type="number" min={0} value={draft.yearsOfExperience ?? 0} onChange={(event) => patchDraft({ yearsOfExperience: Number(event.target.value) })} /></FieldShell>
                  <FieldShell label="Consultation fee" helperText="Optional. Shown publicly when enabled." error={null}><input type="number" min={0} value={draft.consultationFee ?? ""} onChange={(event) => patchDraft({ consultationFee: Number(event.target.value) })} /></FieldShell>
                </> : <>
                  {copy.showBeds ? (
                    <FieldShell label="Beds" helperText="Number of licensed beds." error={validation.errors.beds}>
                      <input type="number" min={1} value={draft.beds ?? 0} onChange={(event) => patchDraft({ beds: Number(event.target.value) })} />
                    </FieldShell>
                  ) : null}
                  {copy.showMedicalDirector ? (
                    <FieldShell label="Medical director" helperText="Lead physician responsible for the hospital." error={validation.errors.medicalDirector}>
                      <input aria-invalid={Boolean(validation.errors.medicalDirector)} value={draft.medicalDirector ?? ""} onChange={(event) => patchDraft({ medicalDirector: event.target.value })} />
                    </FieldShell>
                  ) : null}
                  {copy.showEmergencyAvailable ? (
                    <label className="checkbox-row">
                      <input type="checkbox" checked={draft.emergencyAvailable ?? false} onChange={(event) => patchDraft({ emergencyAvailable: event.target.checked })} />
                      Emergency services available
                    </label>
                  ) : null}
                </>}
                <ProviderMultiSelectField
                  label="Languages"
                  helperText="Patients will see these."
                  error={validation.errors.languages}
                  value={validation.languages}
                  options={languageSelectOptions}
                  onChange={(value) => patchDraft({ languages: normalizeSearchList(value) })}
                  placeholder="Search languages"
                  disabled={!applicationEditable}
                />
                {copy.showSpecialities ? (
                  <ProviderDropdownField
                    label={copy.specialityLabel}
                    helperText={copy.specialityRequired ? "Choose one primary speciality." : "Optional. Choose the closest fit if relevant."}
                    error={validation.errors.specialities}
                    value={draft.specialities?.[0] ?? ""}
                    options={specialitySelectOptions}
                    onChange={(value) => patchDraft({ specialities: value ? [value] : [] })}
                    placeholder={copy.specialityLabel}
                    disabled={!applicationEditable}
                  />
                ) : null}
                {copy.showDepartments ? (
                  <ProviderMultiSelectField
                    label="Departments"
                    helperText="Add the hospital departments you operate."
                    error={validation.errors.departments}
                    value={draft.departments ?? []}
                    options={(draft.departments ?? []).map((item) => ({ value: item, label: item }))}
                    onChange={(value) => patchDraft({ departments: normalizeSearchList(value) })}
                    placeholder="Type a department and press Enter"
                    allowCustomValues
                    disabled={!applicationEditable}
                  />
                ) : null}
                <ProviderMultiSelectField
                  label="Facilities"
                  helperText="Select the facilities available at this location."
                  error={validation.errors.facilities}
                  value={validation.facilities}
                  options={facilitySelectOptions}
                  onChange={(value) => patchDraft({ facilities: normalizeSearchList(value) })}
                  placeholder="Search facilities"
                  noOptionsText="No facility matches the catalog"
                  disabled={!applicationEditable}
                />
                {copy.showQualification ? null : (
                  <FieldShell label="Consultation fee" helperText="Optional. Shown publicly when enabled." error={null}><input type="number" min={0} value={draft.consultationFee ?? ""} onChange={(event) => patchDraft({ consultationFee: Number(event.target.value) })} /></FieldShell>
                )}
              </div>
              <FieldShell label="Biography" helperText="Displayed publicly on your profile. Maximum 1500 characters." error={validation.errors.biography}>
                <textarea
                  aria-invalid={Boolean(validation.errors.biography)}
                  value={draft.biography ?? ""}
                  onChange={(event) => patchDraft({ biography: event.target.value.slice(0, 1500) })}
                  maxLength={1500}
                  placeholder="Summarise experience, focus areas, and care approach."
                />
              </FieldShell>
              </fieldset>
            </Panel>
          ) : null}

          {activeStep === "services" ? (
            <Panel title="Services">
              <p className="panel-help">Select the services your practice offers.</p>
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
                <div className="service-grid">
                  {serviceSelectOptions.length ? serviceSelectOptions.map((service) => (
                    <ServiceToggle
                      key={service.type}
                      service={{ type: service.type, label: service.label }}
                      draft={draft}
                      patchDraft={patchDraft}
                      disabled={!applicationEditable}
                    />
                  )) : <p className="panel-help">No active service catalog entries are available right now.</p>}
                </div>
              </fieldset>
            </Panel>
          ) : null}

          {activeStep === "locations" ? (
            <Panel title="Locations">
              <p className="panel-help">Enter the primary location patients will search and visit.</p>
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
              <div className="form-grid">
                <FieldShell label="Address" helperText="Street address or landmark." error={null}>
                  <input value={draft.locations?.[0]?.address ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { address: event.target.value })} />
                </FieldShell>
                <FieldShell label="City" helperText="Primary city for the location." error={null}>
                  <input value={draft.locations?.[0]?.city ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { city: event.target.value })} />
                </FieldShell>
                <ProviderDropdownField
                  label="State"
                  helperText="Select the state or province."
                  error={validation.errors.locations}
                  value={draft.locations?.[0]?.state ?? ""}
                  options={stateSelectOptions}
                  onChange={(value) => patchLocation(draft, patchDraft, { state: value })}
                  placeholder="Select state"
                  disabled={!applicationEditable}
                />
                <ProviderDropdownField
                  label="Country"
                  helperText="Select the country."
                  error={validation.errors.locations}
                  value={draft.locations?.[0]?.country ?? "India"}
                  options={countrySelectOptions}
                  onChange={(value) => patchLocation(draft, patchDraft, { country: value })}
                  placeholder="Select country"
                  disabled={!applicationEditable}
                />
                <FieldShell label="PIN" helperText="Postal or ZIP code." error={null}>
                  <input value={draft.locations?.[0]?.pinCode ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { pinCode: event.target.value })} />
                </FieldShell>
                <FieldShell label="Working hours" helperText="For example, Mon-Sat 9am-6pm." error={null}>
                  <input value={draft.locations?.[0]?.workingHours ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { workingHours: event.target.value })} />
                </FieldShell>
              </div>
              <div className="icon-pill-row">
                <label className="checkbox-row"><input type="checkbox" checked={draft.locations?.[0]?.parkingAvailable ?? false} onChange={(event) => patchLocation(draft, patchDraft, { parkingAvailable: event.target.checked })} /> Parking available</label>
                <label className="checkbox-row"><input type="checkbox" checked={draft.locations?.[0]?.accessibilityAvailable ?? false} onChange={(event) => patchLocation(draft, patchDraft, { accessibilityAvailable: event.target.checked })} /> Accessibility support available</label>
              </div>
              <LocationPicker
                providerName={draft.displayName || copy.nameLabel}
                location={draft.locations?.[0] ?? null}
                editable={applicationEditable}
                onCoordinatesChange={(coordinates) => patchLocation(draft, patchDraft, {
                  latitude: coordinates?.latitude ?? null,
                  longitude: coordinates?.longitude ?? null,
                })}
              />
              </fieldset>
            </Panel>
          ) : null}

          {activeStep === "branding" ? (
            <Panel title="Branding and documents">
              <p className="panel-help">Upload the assets patients will recognize on the public page.</p>
              <div className="provider-branding-banner">
                <strong>Branding</strong>
                <span>Logo, cover image, and gallery assets should match the final published profile.</span>
              </div>
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
              <div className="upload-grid">
                <UploadBox label={copy.brandingAssetLabel} type={providerType === "INDIVIDUAL_DOCTOR" ? "DOCTOR_PHOTO" : "LOGO"} uploadDocument={uploadDocument} disabled={!applicationEditable} />
                <UploadBox label="Cover image" type="COVER_IMAGE" uploadDocument={uploadDocument} disabled={!applicationEditable} />
                <UploadBox label="Gallery image" type="GALLERY_IMAGE" uploadDocument={uploadDocument} disabled={!applicationEditable} />
                <UploadBox label="Registration document" type="REGISTRATION_CERTIFICATE" uploadDocument={uploadDocument} disabled={!applicationEditable} />
              </div>
              <FieldShell label="Tagline" helperText="Short public brand line. Optional." error={null}>
                <input
                  value={draft.branding?.tagline ?? ""}
                  onChange={(event) => patchDraft({ branding: { ...draft.branding, tagline: event.target.value } })}
                  placeholder="One line summary"
                />
              </FieldShell>
              </fieldset>
              <div className="document-list">{application?.documents.map((document) => <span key={document.id}>{document.documentType.replaceAll("_", " ")} · {document.originalFilename}</span>)}</div>
            </Panel>
          ) : null}

          {activeStep === "preview" ? (
            <section className="provider-preview-page">
              <header className="provider-preview-banner" role="status" aria-live="polite">
                <div className="provider-preview-banner-copy">
                  <span className="eyebrow">Preview</span>
                  <h1>Public profile preview</h1>
                  <p>This is how patients will see your profile after publication. Only you can see this preview before submission.</p>
                  {readOnlyApplication ? <small>Your application is currently read-only while Jeevanam reviews it.</small> : null}
                </div>
                <div className="provider-preview-banner-actions">
                  <button className="primary-button" type="button" onClick={() => void loadPreviewPanel()}>
                    Refresh preview
                  </button>
                </div>
              </header>
              <div className="provider-preview-page-body">
                <PublicProviderProfile
                  providerType={providerType}
                  displayName={preview?.displayName ?? draft.displayName ?? "Profile name pending"}
                  profileEyebrow={`${providerTypeLabel(providerType)} profile`}
                  heroSummary={heroSummary}
                  tagline={tagline}
                  coverImageUrl={previewCoverPath}
                  avatarImageUrl={previewDoctorPhotoPath ?? previewLogoPath}
                  imageToken={token}
                  primarySpeciality={primarySpeciality}
                  locationSummary={heroLocation}
                  yearsOfExperience={draft.yearsOfExperience}
                  consultationFeeLabel={consultationFeeLabel}
                  languages={validation.languages}
                  teleconsultationAvailable={hasTeleconsultation}
                  bookingUrl={careBookingUrl({ provider: preview?.displayName ?? draft.displayName })}
                  callHref={hasPublicPhone ? `tel:${publicPhone}` : null}
                  callLabel={hasPublicPhone ? providerCallLabel(providerType) : null}
                  biographyTitle={providerType === "INDIVIDUAL_DOCTOR" ? `About ${preview?.displayName ?? draft.displayName ?? "the doctor"}` : `About ${preview?.displayName ?? draft.displayName ?? "the provider"}`}
                  biography={preview?.biography ?? draft.biography}
                  biographyEmptyDescription="Add a patient-facing introduction so people understand your background and care approach."
                  professionalInformation={professionalInformation}
                  services={serviceLabels}
                  facilitiesTitle={showFacilitiesSection ? facilitySectionTitle : null}
                  facilities={showFacilitiesSection ? locationFacilityValues : []}
                  galleryItems={previewGalleryItems}
                  galleryEmptyActionLabel="Add images"
                  onGalleryEmptyAction={() => {
                    void goToStep("branding", "route-transition");
                  }}
                  locationName={previewPrimaryLocation?.label || preview?.displayName || draft.displayName || copy.nameLabel}
                  locationAddress={locationAddress}
                  locationWorkingHours={previewPrimaryLocation?.workingHours ?? null}
                  locationFacilities={showFacilitiesSection ? locationFacilityValues : []}
                  locations={draft.locations ?? []}
                  locationEmptyDescription="Add an address and map pin so patients can find you easily."
                  trustTitle={trustSectionTitle}
                  trustIndicators={trustIndicators}
                  trustSupportingCopy={trustSectionTitle === "Verified contact details" ? "Currently showing verified contact details. Clinical verification badges are not available in this preview yet." : null}
                  consultationModes={consultationModes}
                  className="provider-preview-profile profile-preview-card provider-public-preview"
                  dataTestId="provider-preview-canvas"
                  preview
                />
                <section className="provider-preview-workflow" data-testid="provider-preview-readiness-panel">
                  <div className="provider-preview-section-heading">
                    <span className="eyebrow">Provider workflow</span>
                    <h2>Ready for submission</h2>
                  </div>
                  {previewChecklistItems.length ? (
                    <div className="provider-preview-checklist">
                      {previewChecklistItems.map((item) => (
                        <article className="provider-preview-checklist-item" key={`${item.step}-${item.label}`}>
                          <div>
                            <h3>{item.label}</h3>
                            <p>{item.detail ?? `Update this in ${stepLabel(item.step)}.`}</p>
                            <small>{stepLabel(item.step)}</small>
                          </div>
                          <button className="secondary-button" type="button" onClick={() => void goToStep(item.step, "route-transition")}>
                            Complete now
                          </button>
                        </article>
                      ))}
                    </div>
                  ) : (
                    <div className="provider-preview-checklist-empty" role="status" aria-live="polite">
                      <strong>Your public profile contains the required information.</strong>
                      {readinessCompletedAreas.length ? (
                        <div className="provider-preview-pill-grid">
                          {readinessCompletedAreas.map((item) => <span className="provider-preview-ready-pill" key={item}>{item}</span>)}
                        </div>
                      ) : null}
                    </div>
                  )}
                  <div className="provider-preview-workflow-actions">
                    <button className="secondary-button" type="button" onClick={() => void goToStep(returnToEditingStep, "route-transition")}>
                      Back to editing
                    </button>
                    <button className="primary-button" type="button" onClick={() => void goToStep("submit", "route-transition")}>
                      Continue to submission
                    </button>
                  </div>
                </section>
              </div>
            </section>
          ) : null}

          {activeStep === "submit" ? (
            <Panel title="Submit for verification">
              <p className="panel-help">Review the remaining items before submitting for verification.</p>
              {referenceCatalogLoaded && !submissionReadyForReferenceData ? (
                <div className="verification-blocking" role="status" aria-live="polite">
                  <strong>Reference data unavailable</strong>
                  <p>At least one required reference dataset is empty for this provider type.</p>
                </div>
              ) : null}
              <div className="submission-summary-grid">
                <section className="submission-summary-card">
                  <strong>Profile completion</strong>
                  <div className="progress-track" aria-label={`${completionPercent}% complete`}>
                    <span style={{ width: `${completionPercent}%` }} />
                  </div>
                  <p>{completionPercent}% complete</p>
                  <small>{validation.requiredRemaining} required items remaining</small>
                </section>
                <section className="submission-summary-card">
                  <strong>Submission summary</strong>
                  <ul>
                    <li>{providerTypeLabel(providerType)}</li>
                    <li>{providerType === "INDIVIDUAL_DOCTOR" ? `Speciality: ${draft.specialities?.[0] ?? "Pending"}` : providerType === "HOSPITAL" ? `Departments: ${draft.departments?.join(", ") ?? "Pending"}` : `Speciality: ${draft.specialities?.[0] ?? "Optional"}`}</li>
                    <li>Location: {draft.locations?.[0]?.city ? `${draft.locations[0].city}, ${draft.locations[0].state}` : "Pending"}</li>
                    <li>Documents uploaded: {application?.documents.length ?? 0}</li>
                  </ul>
                </section>
              </div>
              <section className="submission-timeline-card">
                <strong>Submission timeline</strong>
                <div className="submission-timeline">
                  {["Draft", "Submitted", "Under Review", "Changes Requested", "Approved", "Published"].map((label, index) => (
                    <span key={label} className={index <= (application?.status === "PUBLISHED" ? 5 : application?.status === "APPROVED" ? 4 : application?.status === "CHANGES_REQUESTED" ? 3 : application?.status === "UNDER_REVIEW" ? 2 : application?.status === "SUBMITTED" ? 1 : 0) ? "is-active" : ""}>
                      {label}
                    </span>
                  ))}
                </div>
              </section>
              {!contactSatisfied ? (
                <div className="verification-blocking">
                  <strong>Contact verification required</strong>
                  <p>Verify your contact details before submitting your profile.</p>
                  {verificationPanel()}
                </div>
              ) : null}
              {changeRequests.length ? (
                <div className="change-request-panel">
                  <strong>Requested changes</strong>
                  {changeRequests.map((request) => (
                    <div key={request.id} className="change-request-item">
                      <span>{request.reviewerMessage ?? "Review team feedback"}</span>
                      {request.requestedSections.length ? <small>{request.requestedSections.join(", ")}</small> : null}
                      {request.providerResponseNote ? <small>Response: {request.providerResponseNote}</small> : null}
                    </div>
                  ))}
                  {application?.status === "CHANGES_REQUESTED" ? (
                    <label>
                      Response note
                      <textarea value={responseNote} onChange={(event) => setResponseNote(event.target.value)} placeholder="Describe the updates you made" />
                    </label>
                  ) : null}
                </div>
              ) : null}
              {application?.statusHistory.length ? (
                <div className="status-messages">
                  <strong>Status messages</strong>
                  {application.statusHistory.slice(-3).map((item) => (
                    <span key={item.id}>{item.reason}</span>
                  ))}
                </div>
              ) : null}
              <div className="submission-blockers">
                <strong>Blocking items</strong>
                {missingItems.length ? (
                  <div className="submission-blocker-groups">
                    {Object.entries(missingItems.reduce<Record<string, string[]>>((groups, code) => {
                      const group = missingItemGroup(code);
                      (groups[group] ??= []).push(code);
                      return groups;
                    }, {})).map(([group, items]) => (
                      <section key={group}>
                        <strong>{group}</strong>
                        {items.map((item) => (
                          <span key={item}>{missingItemLabel(item)}</span>
                        ))}
                      </section>
                    ))}
                  </div>
                ) : (
                  <p>Your application is ready for review.</p>
                )}
              </div>
              <button className="primary-button" type="button" onClick={() => void submit()} disabled={!applicationEditable || saving || missingItems.length > 0 || !contactSatisfied || !submissionReadyForReferenceData}>
                {application?.status === "CHANGES_REQUESTED" ? "Resubmit for verification" : "Submit for verification"}
              </button>
            </Panel>
          ) : null}

          <div className="wizard-footer">
            <button className="secondary-button" type="button" onClick={() => void goToStep(steps[Math.max(0, currentStepIndex - 1)][0])} disabled={currentStepIndex === 0}>Back</button>
            {activeStep !== "submit" ? (
              <button
                className="primary-button"
                type="button"
                onClick={() => void goToStep(steps[Math.min(steps.length - 1, currentStepIndex + 1)][0])}
                disabled={currentStepIndex >= steps.length - 1 || !currentStepCanContinue}
              >
                Continue
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return <div className="onboarding-panel"><h2>{title}</h2>{children}</div>;
}

function ServiceToggle({ service, draft, patchDraft, disabled = false }: { service: { type: ProviderServiceType; label: string }; draft: ProviderApplicationPayload; patchDraft: (patch: ProviderApplicationPayload) => void; disabled?: boolean }) {
  const enabled = draft.services?.some((item) => item.serviceType === service.type && item.enabled !== false) ?? false;
  return (
    <label className="service-toggle">
      <input
        type="checkbox"
        checked={enabled}
        disabled={disabled}
        onChange={(event) => {
          const others = draft.services?.filter((item) => item.serviceType !== service.type) ?? [];
          const next: ProviderServicePayload[] = event.target.checked ? [...others, { serviceType: service.type, label: service.label, enabled: true }] : others;
          patchDraft({ services: next });
        }}
      />
      <span>{service.label}</span>
    </label>
  );
}

function patchLocation(draft: ProviderApplicationPayload, patchDraft: (patch: ProviderApplicationPayload) => void, patch: Partial<NonNullable<ProviderApplicationPayload["locations"]>[number]>) {
  const current = draft.locations?.[0] ?? { label: "Primary", address: "", city: "", state: "", country: "India", pinCode: "" };
  patchDraft({ locations: [{ ...current, ...patch }] });
}

function UploadBox({ label, type, uploadDocument, disabled = false }: { label: string; type: ProviderDocumentType; uploadDocument: (type: ProviderDocumentType, event: ChangeEvent<HTMLInputElement>) => void; disabled?: boolean }) {
  return <label className="upload-box"><span>{label}</span><input type="file" disabled={disabled} accept=".png,.jpg,.jpeg,.pdf,image/png,image/jpeg,application/pdf" onChange={(event) => uploadDocument(type, event)} /></label>;
}

function StatusTimeline({ status }: { status: string }) {
  const labels = ["Draft", "Profile Complete", "Submitted", "Under Review", "Approved", "Published"];
  return <div className="status-timeline">{labels.map((item) => <span className={status.replaceAll("_", " ").toLowerCase().includes(item.toLowerCase().split(" ")[0]) ? "is-active" : ""} key={item}>{item}</span>)}</div>;
}

function FieldShell({ label, helperText, error, children }: { label: string; helperText?: string; error?: string | null; children: ReactNode }) {
  return (
    <label className="provider-field">
      <span>
        {label}
        {helperText ? <small>{helperText}</small> : null}
      </span>
      {children}
      {error ? <small className="field-error">{error}</small> : null}
    </label>
  );
}

function clientMissingItems(draft: ProviderApplicationPayload, account: { email: string; phone: string; termsAccepted: boolean; privacyAccepted: boolean }, providerType: ProviderType) {
  const missing = [];
  if (!account.email && !draft.email) missing.push("EMAIL_REQUIRED");
  if (!account.phone && !draft.phone) missing.push("PHONE_REQUIRED");
  if (!account.termsAccepted && !draft.termsAccepted) missing.push("TERMS_ACCEPTANCE_REQUIRED");
  if (!account.privacyAccepted && !draft.privacyAccepted) missing.push("PRIVACY_ACCEPTANCE_REQUIRED");
  if (!draft.displayName && !draft.legalName) missing.push(providerType === "INDIVIDUAL_DOCTOR" ? "DOCTOR_NAME_REQUIRED" : providerType === "CLINIC" ? "CLINIC_NAME_REQUIRED" : "HOSPITAL_NAME_REQUIRED");
  if (!draft.registrationNumber) missing.push(providerType === "INDIVIDUAL_DOCTOR" ? "DOCTOR_REGISTRATION_NUMBER_REQUIRED" : providerType === "CLINIC" ? "CLINIC_REGISTRATION_NUMBER_REQUIRED" : "HOSPITAL_REGISTRATION_NUMBER_REQUIRED");
  if (providerType === "INDIVIDUAL_DOCTOR" && !draft.specialities?.length) missing.push("PRIMARY_SPECIALITY_REQUIRED");
  if (providerType === "CLINIC" && draft.specialities && draft.specialities.length > 1) missing.push("PRIMARY_SPECIALITY_REQUIRED");
  if (providerType === "HOSPITAL" && !draft.departments?.length) missing.push("HOSPITAL_DEPARTMENTS_REQUIRED");
  if (!draft.services?.some((item) => item.enabled !== false)) missing.push("SERVICES_REQUIRED");
  if (!draft.locations?.length) missing.push("PRIMARY_LOCATION_REQUIRED");
  if (providerType === "INDIVIDUAL_DOCTOR" && !draft.qualification) missing.push("DOCTOR_QUALIFICATION_REQUIRED");
  if (providerType === "HOSPITAL" && !draft.beds) missing.push("HOSPITAL_BEDS_REQUIRED");
  if (providerType === "HOSPITAL" && !draft.emergencyAvailable) missing.push("HOSPITAL_EMERGENCY_STATUS_REQUIRED");
  return missing;
}

function estimateCompletion(draft: ProviderApplicationPayload, account: ProviderAccountCompletionInput, providerType: ProviderType, catalog: DiscoverReferenceCatalog = emptyReferenceCatalog()) {
  const validation = validateDraft(draft, account, providerType, catalog);
  const accountComplete = !validation.errors.account && !validation.errors.terms && !validation.errors.privacy;
  const organisationComplete = !validation.errors.displayName && !validation.errors.registrationNumber && !validation.errors.website && !validation.errors.gstNumber && !validation.errors.ownership && !validation.errors.organisationType && !validation.errors.hospitalType;
  const professionalComplete = !validation.errors.qualification && !validation.errors.medicalCouncil && !validation.errors.yearsOfExperience && !validation.errors.specialities && !validation.errors.departments && !validation.errors.languages && !validation.errors.facilities && !validation.errors.beds && !validation.errors.medicalDirector && !validation.errors.emergencyAvailable;
  const servicesComplete = !validation.errors.services;
  const locationsComplete = !validation.errors.locations;
  const brandingComplete = providerType === "INDIVIDUAL_DOCTOR" ? Boolean(draft.branding?.doctorPhotoDocumentId) : Boolean(draft.branding?.logoDocumentId);
  const previewReady = accountComplete && organisationComplete && professionalComplete && servicesComplete && locationsComplete && brandingComplete;
  const steps = [
    [accountComplete, 10],
    [organisationComplete, 15],
    [professionalComplete, 20],
    [servicesComplete, 15],
    [locationsComplete, 15],
    [brandingComplete, 10],
    [previewReady, 5],
    [previewReady && !validation.errors.documents, 10],
  ] as const;
  const total = steps.reduce((sum, [, weight]) => sum + weight, 0);
  const completed = steps.reduce((sum, [done, weight]) => sum + (done ? weight : 0), 0);
  return Math.max(0, Math.min(100, (completed * 100) / total));
}
