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
  | "SUSPENDED"
  | "ARCHIVED";
export type ProviderServiceType =
  | "CONSULTATIONS"
  | "VACCINATION"
  | "LAB"
  | "RADIOLOGY"
  | "TELECONSULTATION"
  | "PHARMACY"
  | "HEALTH_CHECKUPS"
  | "PROCEDURES";
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
  readOnly: boolean;
  nextRecommendedAction: string;
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

export function resubmitProviderApplication(id: string, token: string, providerResponseNote?: string) {
  return request<ProviderApplication>(`/api/provider-registration/providers/${id}/resubmit`, {
    method: "POST",
    body: JSON.stringify({ providerResponseNote }),
  }, token);
}

export function loadProviderPreview(id: string, token: string) {
  return request<ProviderPreview>(`/api/provider-registration/providers/${id}/preview`, {}, token);
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
