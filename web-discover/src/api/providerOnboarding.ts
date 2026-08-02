import { discoverConfig } from "../config";

export type ProviderType = "INDIVIDUAL_DOCTOR" | "CLINIC" | "HOSPITAL";
export type ProviderStatus =
  | "DRAFT"
  | "CONTACT_VERIFIED"
  | "PROFILE_INCOMPLETE"
  | "READY_FOR_REVIEW"
  | "SUBMITTED"
  | "UNDER_REVIEW"
  | "CHANGES_REQUESTED"
  | "APPROVED"
  | "PUBLISHED"
  | "DISCARDED"
  | "SUSPENDED"
  | "ARCHIVED";
export type ProviderServiceType =
  | "CONSULTATION"
  | "TELECONSULTATION"
  | "HEALTH_CHECKUPS"
  | "VACCINATION"
  | "MINOR_PROCEDURES"
  | "HOME_VISIT"
  | "LAB_COLLECTION"
  | "CHRONIC_DISEASE_MANAGEMENT"
  | "PREVENTIVE_CARE";
export type ProviderDocumentType =
  | "LOGO"
  | "COVER_IMAGE"
  | "DOCTOR_PHOTO"
  | "GALLERY_IMAGE"
  | "REGISTRATION_CERTIFICATE"
  | "ACCREDITATION"
  | "IDENTITY_PROOF"
  | "OTHER";

export type ProviderLocationPayload = {
  id?: string;
  label?: string;
  address: string;
  city: string;
  state: string;
  country: string;
  pinCode: string;
  workingHours?: string;
  parkingAvailable?: boolean;
  accessibilityAvailable?: boolean;
  latitude?: number | null;
  longitude?: number | null;
};

export type ProviderServicePayload = {
  id?: string;
  serviceType: ProviderServiceType;
  label: string;
  description?: string;
  enabled?: boolean;
};

export type ProviderApplicationPayload = {
  version?: number;
  email?: string;
  phone?: string;
  contactVerified?: boolean;
  termsAccepted?: boolean;
  privacyAccepted?: boolean;
  displayName?: string;
  legalName?: string;
  organisationType?: string;
  registrationNumber?: string;
  gstNumber?: string;
  website?: string;
  gender?: string;
  dateOfBirth?: string;
  languages?: string[];
  biography?: string;
  medicalCouncil?: string;
  qualification?: string;
  yearsOfExperience?: number;
  specialities?: string[];
  subSpecialities?: string[];
  consultationFee?: number;
  onlineConsultation?: boolean;
  appointmentDurationMinutes?: number;
  ownership?: string;
  hospitalType?: string;
  beds?: number;
  emergencyAvailable?: boolean;
  medicalDirector?: string;
  departments?: string[];
  facilities?: string[];
  accreditations?: string[];
  locations?: ProviderLocationPayload[];
  services?: ProviderServicePayload[];
  branding?: {
    logoDocumentId?: string | null;
    coverImageDocumentId?: string | null;
    doctorPhotoDocumentId?: string | null;
    primaryColor?: string;
    tagline?: string;
  };
};

export type ContactVerificationStatus = {
  email: string;
  emailStatus: "NOT_VERIFIED" | "PENDING" | "VERIFIED";
  emailVerifiedAt: string | null;
  phone: string;
  phoneStatus: "NOT_VERIFIED" | "PENDING" | "VERIFIED";
  phoneVerifiedAt: string | null;
  requirementSatisfied: boolean;
};

export type VerificationChallenge = {
  message: string;
  devCode: string | null;
  expiresInSeconds: number;
  resendAfterSeconds: number;
};

export type ProviderApplication = ProviderApplicationPayload & {
  id: string;
  referenceNumber: string;
  providerType: ProviderType;
  status: ProviderStatus;
  version: number;
  completionPercent: number;
  currentStep: string;
  email: string;
  phone: string;
  contactVerified: boolean;
  termsAccepted: boolean;
  privacyAccepted: boolean;
  locations: ProviderLocationPayload[];
  services: ProviderServicePayload[];
  documents: Array<{
    id: string;
    documentType: ProviderDocumentType;
    originalFilename: string;
    contentType: string;
    sizeBytes: number;
    uploadedAt: string;
    virusScanStatus: string;
  }>;
  statusHistory: Array<{
    id: string;
    fromStatus: ProviderStatus | null;
    toStatus: ProviderStatus;
    reason: string;
    createdAt: string;
  }>;
  missingItems: string[];
  contactVerification: ContactVerificationStatus;
  lastSavedAt: string;
  submittedAt?: string | null;
  onboardingToken?: string;
};

export type ProviderCompletion = {
  completionPercentage: number;
  completedSteps: string[];
  incompleteSteps: string[];
  missingRequiredFields: string[];
  missingRequiredDocuments: string[];
  validationWarnings: string[];
  blockingErrors: string[];
  canSubmit: boolean;
  previewReady: boolean;
  recommendedNextStep: string;
  currentStep: string;
  readOnly: boolean;
};

export type ProviderChangeRequest = {
  id: string;
  submissionVersionNumber: number | null;
  requestedSections: string[];
  reviewerMessage: string | null;
  providerResponseNote: string | null;
  requestedAt: string;
  resolvedAt: string | null;
  resolved: boolean;
};

export type ProviderTimelineEvent = {
  label: string;
  description: string | null;
  actorCategory: string;
  timestamp: string;
};

export type ProviderDashboard = {
  application: ProviderApplication;
  completion: ProviderCompletion;
  timeline: ProviderTimelineEvent[];
  changeRequests: ProviderChangeRequest[];
  submittedSnapshot: ProviderSubmittedSnapshot | null;
  publicProfilePath: string | null;
  readOnly: boolean;
  nextRecommendedAction: string;
};

export type ProviderSubmittedSnapshot = {
  versionNumber: number;
  submittedAt: string;
  providerType: ProviderType;
  displayName: string | null;
  legalName: string | null;
  primarySpeciality: string | null;
  specialities: string[];
  subSpecialities: string[];
  languages: string[];
  qualification: string | null;
  medicalCouncil: string | null;
  yearsOfExperience: number | null;
  organisationType: string | null;
  ownership: string | null;
  hospitalType: string | null;
  beds: number | null;
  emergencyAvailable: boolean;
  medicalDirector: string | null;
  consultationFee: number | null;
  departments: string[];
  facilities: string[];
  services: string[];
  locations: string[];
  serviceCount: number;
  locationCount: number;
  documentCount: number;
  galleryCount: number;
  logoDocumentId: string | null;
  coverImageDocumentId: string | null;
  doctorPhotoDocumentId: string | null;
  galleryDocumentIds: string[];
  biography: string | null;
  tagline: string | null;
};

export type ProviderPreview = {
  providerId: string;
  providerType: ProviderType;
  displayName: string;
  subtitle: string;
  locationSummary: string;
  services: string[];
  specialities: string[];
  biography?: string | null;
  branding: {
    logoDocumentId: string | null;
    coverImageDocumentId: string | null;
    doctorPhotoDocumentId: string | null;
    galleryDocumentIds: string[];
    primaryColor: string | null;
    tagline: string | null;
  };
  completionPercent: number;
  readyForSubmission: boolean;
  missingItems: string[];
};

function buildUrl(path: string) {
  return new URL(`${discoverConfig.apiBaseUrl}${path}`, window.location.origin).toString();
}

async function parseError(response: Response) {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? `Request failed with status ${response.status}`;
  } catch {
    return `Request failed with status ${response.status}`;
  }
}

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(buildUrl(path), {
    ...options,
    headers: {
      Accept: "application/json",
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(token ? { "X-Provider-Onboarding-Token": token } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

async function requestBlob(path: string, options: RequestInit = {}, token?: string): Promise<Blob> {
  const response = await fetch(buildUrl(path), {
    ...options,
    headers: {
      Accept: "application/octet-stream,image/*,*/*",
      ...(token ? { "X-Provider-Onboarding-Token": token } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.blob();
}

export function createProviderApplication(input: { providerType: ProviderType; email: string; phone: string; password: string; termsAccepted: boolean; privacyAccepted: boolean }) {
  return request<ProviderApplication>("/api/provider-registration/providers", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function loadProviderApplication(token: string) {
  return request<ProviderApplication>("/api/provider-registration/providers/me", {}, token);
}

export function loadProviderDashboard(token: string) {
  return request<ProviderDashboard>("/api/provider-registration/providers/me/dashboard", {}, token);
}

export function loadProviderCompletion(id: string, token: string) {
  return request<ProviderCompletion>(`/api/provider-registration/providers/${id}/completion`, {}, token);
}

export function loadProviderStatusHistory(id: string, token: string) {
  return request<Array<{ id: string; fromStatus: ProviderStatus | null; toStatus: ProviderStatus; reason: string | null; createdAt: string }>>(
    `/api/provider-registration/providers/${id}/status-history`,
    {},
    token,
  );
}

export function loadProviderChangeRequests(id: string, token: string) {
  return request<ProviderChangeRequest[]>(`/api/provider-registration/providers/${id}/change-requests`, {}, token);
}

export function updateProviderApplication(id: string, token: string, payload: ProviderApplicationPayload) {
  return request<ProviderApplication>(`/api/provider-registration/providers/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  }, token);
}

export function submitProviderApplication(id: string, token: string) {
  return request<ProviderApplication>(`/api/provider-registration/providers/${id}/submit`, { method: "POST" }, token);
}

export function discardProviderApplication(id: string, token: string, reason?: string) {
  return request<ProviderApplication>(`/api/provider-registration/providers/${id}/discard`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  }, token);
}

export function resubmitProviderApplication(id: string, token: string, providerResponseNote?: string) {
  return request<ProviderApplication>(`/api/provider-registration/providers/${id}/resubmit`, {
    method: "POST",
    body: JSON.stringify({ providerResponseNote }),
  }, token);
}

export function loadProviderPreview(id: string, token: string) {
  return request<ProviderPreview>(`/api/provider-registration/providers/${id}/preview`, {}, token);
}

export function loadProviderContactVerification(id: string, token: string) {
  return request<ContactVerificationStatus>(`/api/provider-registration/providers/${id}/contact-verification`, {}, token);
}

export function requestProviderEmailVerification(id: string, token: string) {
  return request<VerificationChallenge>(`/api/provider-registration/providers/${id}/contact-verification/email/request`, { method: "POST" }, token);
}

export function verifyProviderEmail(id: string, token: string, code: string) {
  return request<ContactVerificationStatus>(`/api/provider-registration/providers/${id}/contact-verification/email/verify`, {
    method: "POST",
    body: JSON.stringify({ code }),
  }, token);
}

export function requestProviderPhoneVerification(id: string, token: string) {
  return request<VerificationChallenge>(`/api/provider-registration/providers/${id}/contact-verification/phone/request`, { method: "POST" }, token);
}

export function verifyProviderPhone(id: string, token: string, code: string) {
  return request<ContactVerificationStatus>(`/api/provider-registration/providers/${id}/contact-verification/phone/verify`, {
    method: "POST",
    body: JSON.stringify({ code }),
  }, token);
}

export function providerDocumentContentPath(providerId: string, documentId: string) {
  return `/api/provider-registration/providers/${providerId}/documents/${documentId}/content`;
}

export function fetchProviderDocumentBlob(path: string, token: string, signal?: AbortSignal) {
  return requestBlob(path, { method: "GET", signal }, token);
}

export function uploadProviderDocument(id: string, token: string, documentType: ProviderDocumentType, file: File) {
  const body = new FormData();
  body.set("documentType", documentType);
  body.set("file", file);
  return request<ProviderApplication["documents"][number]>(`/api/provider-registration/providers/${id}/documents`, {
    method: "POST",
    body,
  }, token);
}
