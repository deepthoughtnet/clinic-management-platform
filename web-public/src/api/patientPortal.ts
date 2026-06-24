type PatientPortalSessionBase = {
  mode: "otp";
  tenantCode: string;
  tenantId: string;
  phone: string;
  createdAt: string;
  patientSessionToken: string;
};

export type PatientPortalPatientSession = PatientPortalSessionBase & {
  sessionRole: "patient";
  patientLabel: string;
};

export type PatientPortalRegistrationSession = PatientPortalSessionBase & {
  sessionRole: "registration";
  patientLabel: string;
};

export type PatientPortalSession = PatientPortalPatientSession | PatientPortalRegistrationSession;

export function isUuid(value: string | null | undefined): value is string {
  if (!value) {
    return false;
  }
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value.trim());
}

export type PatientPortalOtpRequestResponse = {
  accepted: boolean;
  message: string;
  expiresInSeconds: number;
  resendAvailableInSeconds: number;
  devOtp: string | null;
};

export type PatientPortalOtpVerifyResponse = {
  verified: boolean;
  patientExists: boolean;
  registrationRequired: boolean;
  message: string;
  tenantId: string | null;
  tenantCode: string | null;
  patientDisplayName: string | null;
  patientSessionToken: string | null;
  registrationSessionToken: string | null;
};

export type PatientPortalMeResponse = {
  patientNumber: string;
  fullName: string;
  clinicName: string;
  gender: string | null;
  dateOfBirth: string | null;
  ageYears: number | null;
  mobile: string | null;
  email: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  country: string | null;
  postalCode: string | null;
  emergencyContactName: string | null;
  emergencyContactMobile: string | null;
  bloodGroup: string | null;
  allergies: string | null;
  chronicConditions: string | null;
  longTermMedications: string | null;
};

export type PatientPortalRegistrationRequest = {
  firstName: string;
  lastName: string;
  gender: string;
  dateOfBirth: string | null;
  ageYears: number | null;
  email: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string;
  state: string | null;
  country: string | null;
  postalCode: string | null;
  emergencyContactName: string | null;
  emergencyContactMobile: string | null;
};

export type PatientPortalRegistrationResponse = {
  created: boolean;
  linkedExistingPatient: boolean;
  message: string;
  tenantId: string;
  patientDisplayName: string;
  patientSessionToken: string;
};

export type PatientPortalProfileUpdateRequest = {
  firstName: string;
  lastName: string;
  gender: string;
  dateOfBirth: string | null;
  ageYears: number | null;
  email: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  country: string | null;
  postalCode: string | null;
  emergencyContactName: string | null;
  emergencyContactMobile: string | null;
};

export type PatientPortalAppointmentResponse = {
  appointmentDate: string;
  appointmentTime: string | null;
  doctorName: string | null;
  clinicName: string | null;
  source: string | null;
  reason: string | null;
  status: string | null;
};

export type PatientPortalDoctorResponse = {
  publicDoctorId: string;
  doctorName: string;
  specialization: string | null;
  qualification: string | null;
  consultationRoom: string | null;
  yearsOfExperience: number | null;
};

export type PatientPortalDoctorSlotResponse = {
  appointmentDate: string;
  slotTime: string;
  slotEndTime: string | null;
  status: string | null;
  selectable: boolean;
};

export type PatientPortalAppointmentBookingRequest = {
  publicDoctorId: string;
  clinicSlug?: string | null;
  tenantId?: string | null;
  clinicId?: string | null;
  appointmentDate: string;
  appointmentTime: string;
  reason: string | null;
};

export type PatientPortalDoctorSlotQuery = {
  doctorId: string;
  clinicSlug: string;
  tenantId?: string | null;
  clinicId?: string | null;
  date: string;
};

export type PatientPortalAppointmentConfirmationResponse = {
  appointmentDate: string;
  appointmentTime: string | null;
  doctorName: string | null;
  clinicName: string | null;
  source: string | null;
  status: string | null;
  reason: string | null;
  message: string;
};

export type PatientPortalPrescriptionMedicineResponse = {
  medicineName: string;
  instructions: string;
};

export type PatientPortalPrescriptionTestResponse = {
  testName: string;
  instructions: string | null;
};

export type PatientPortalPrescriptionResponse = {
  prescriptionNumber: string;
  prescriptionDate: string | null;
  doctorName: string | null;
  clinicName: string | null;
  diagnosisSummary: string | null;
  adviceSummary: string | null;
  followUpDate: string | null;
  status: string | null;
  pdfAvailable: boolean;
  medicines: PatientPortalPrescriptionMedicineResponse[];
  recommendedTests: PatientPortalPrescriptionTestResponse[];
};

export type PatientPortalBillReceiptSummaryResponse = {
  receiptNumber: string;
  receiptDate: string | null;
  amount: number | null;
};

export type PatientPortalBillLineResponse = {
  itemName: string;
  quantity: number | null;
  totalPrice: number | null;
  summary: string;
};

export type PatientPortalBillResponse = {
  billNumber: string;
  billType: string | null;
  billDate: string | null;
  status: string | null;
  totalAmount: number | null;
  paidAmount: number | null;
  dueAmount: number | null;
  latestReceipt: PatientPortalBillReceiptSummaryResponse | null;
  lines: PatientPortalBillLineResponse[];
};

export type PatientPortalNotificationResponse = {
  id: string;
  eventType: string;
  subject: string | null;
  message: string;
  status: string;
  readAt: string | null;
  sourceType: string | null;
  sourceId: string | null;
  createdAt: string;
  actionPath: string | null;
};

export type PatientPortalLabResultResponse = {
  testCode: string;
  testName: string;
  parameterName: string | null;
  componentName: string | null;
  resultValue: string | null;
  unit: string | null;
  referenceRange: string | null;
  resultFlag: string | null;
  criticalResult: boolean;
};

export type PatientPortalLabOrderResponse = {
  orderNumber: string;
  doctorName: string | null;
  status: string | null;
  orderedAt: string;
  sampleCollectedAt: string | null;
  resultEnteredAt: string | null;
  reportGeneratedAt: string | null;
  doctorReviewedAt: string | null;
  doctorComments: string | null;
  results: PatientPortalLabResultResponse[];
};

export type PatientPortalLabLatestResultResponse = {
  orderNumber: string;
  testCode: string;
  testName: string;
  componentName: string | null;
  resultValue: string | null;
  unit: string | null;
  referenceRange: string | null;
  resultEnteredAt: string | null;
  doctorReviewedAt: string | null;
  doctorComments: string | null;
};

export type PatientPortalDashboardResponse = {
  patientDisplayName: string;
  patientNumber: string;
  clinicName: string;
  nextAppointment: PatientPortalAppointmentResponse | null;
  recentPrescription: PatientPortalPrescriptionResponse | null;
  unpaidDueAmount: number | null;
  latestBill: PatientPortalBillResponse | null;
};

export type PatientPortalCareAiMessageRequest = {
  message: string;
  language?: string | null;
};

export type PatientPortalCareAiStateResponse = {
  language: string;
  currentIntent: string | null;
  doctorName: string | null;
  speciality: string | null;
  selectedAppointment: string | null;
  preferredDate: string | null;
  preferredTimeWindow: string | null;
  suggestedSlot: string | null;
  confirmationPending: boolean;
  booked: boolean;
  actionCompleted: boolean;
  lastAction: string | null;
  bookedAppointmentDate: string | null;
  bookedAppointmentTime: string | null;
  bookingStatus: string | null;
  handoffRequired: boolean;
  handoffReason: string | null;
  doctorOptions: string[];
  appointmentOptions: string[];
  slotOptions: string[];
};

export type PatientPortalCareAiMessageResponse = {
  assistantMessage: string;
  state: PatientPortalCareAiStateResponse;
};

export type PatientPortalCareAiResetResponse = {
  cleared: boolean;
  message: string;
};

const apiBaseUrl = import.meta.env.VITE_PUBLIC_API_BASE_URL?.trim() ?? "";
const careAiRuntimeUrl = import.meta.env.VITE_CAREAI_RUNTIME_URL?.trim() ?? "";

function buildUrl(path: string) {
  return new URL(`${apiBaseUrl}${path}`, window.location.origin).toString();
}

function buildHeaders(session?: PatientPortalSession | null, extra?: HeadersInit) {
  const headers = new Headers(extra);
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  if (session) {
    headers.set("X-Patient-Session", session.patientSessionToken);
    headers.set("X-Tenant-Id", session.tenantId);
  }
  return headers;
}

export function isPatientPortalPatientSession(session: PatientPortalSession | null | undefined): session is PatientPortalPatientSession {
  return Boolean(session && session.sessionRole === "patient" && session.patientSessionToken);
}

export function isPatientPortalRegistrationSession(
  session: PatientPortalSession | null | undefined,
): session is PatientPortalRegistrationSession {
  return Boolean(session && session.sessionRole === "registration" && session.patientSessionToken);
}

export function patientPortalHomePath(session: PatientPortalSession | null | undefined) {
  if (isPatientPortalPatientSession(session)) {
    return "/patient/dashboard";
  }
  if (isPatientPortalRegistrationSession(session)) {
    return "/patient/register";
  }
  return "/patient/login";
}

export function buildPatientPortalVoiceWebSocketUrl(session: PatientPortalPatientSession) {
  const url = careAiRuntimeUrl
    ? new URL(careAiRuntimeUrl, window.location.origin)
    : new URL(window.location.origin);
  if (url.protocol === "http:") {
    url.protocol = "ws:";
  } else if (url.protocol === "https:") {
    url.protocol = "wss:";
  }
  if (!careAiRuntimeUrl) {
    url.pathname = "/ws/patient-portal/careai";
  }
  url.hash = "";
  url.searchParams.set("sessionToken", session.patientSessionToken);
  return url.toString();
}

async function parseError(response: Response) {
  const fallback = `Request failed with status ${response.status}`;
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? fallback;
  } catch {
    return fallback;
  }
}

export async function postPatientPortalJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(buildUrl(path), {
    method: "POST",
    headers: buildHeaders(undefined, {
      "Content-Type": "application/json",
    }),
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

export async function postPatientPortalSessionJson<T>(
  path: string,
  body: unknown,
  session: PatientPortalSession,
): Promise<T> {
  const response = await fetch(buildUrl(path), {
    method: "POST",
    headers: buildHeaders(session, {
      "Content-Type": "application/json",
    }),
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

export async function putPatientPortalSessionJson<T>(
  path: string,
  body: unknown,
  session: PatientPortalSession,
): Promise<T> {
  const response = await fetch(buildUrl(path), {
    method: "POST",
    headers: buildHeaders(session, {
      "Content-Type": "application/json",
    }),
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

export async function fetchPatientPortalJson<T>(path: string, session: PatientPortalSession, signal?: AbortSignal): Promise<T> {
  const response = await fetch(buildUrl(path), {
    headers: buildHeaders(session),
    signal,
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

export async function loadPatientPortalDoctorSlots(
  query: PatientPortalDoctorSlotQuery,
  session: PatientPortalPatientSession,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams();
  if (isUuid(query.clinicId)) {
    params.set("clinicId", query.clinicId);
  }
  if (isUuid(query.tenantId)) {
    params.set("tenantId", query.tenantId);
  }
  if (query.clinicSlug.trim()) {
    params.set("clinicSlug", query.clinicSlug.trim());
  }
  params.set("date", query.date);
  return fetchPatientPortalJson<PatientPortalDoctorSlotResponse[]>(
    `/api/patient-portal/doctors/${encodeURIComponent(query.doctorId)}/slots?${params.toString()}`,
    session,
    signal,
  );
}

export async function openPatientPortalPdf(path: string, session: PatientPortalSession) {
  const response = await fetch(buildUrl(path), {
    headers: buildHeaders(session, {
      Accept: "application/pdf",
    }),
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  const blob = await response.blob();
  const blobUrl = window.URL.createObjectURL(blob);
  window.open(blobUrl, "_blank", "noopener,noreferrer");
  window.setTimeout(() => window.URL.revokeObjectURL(blobUrl), 60_000);
}

export async function getPatientLabOrders(session: PatientPortalSession) {
  return fetchPatientPortalJson<PatientPortalLabOrderResponse[]>("/api/patient-portal/lab/orders", session);
}

export async function getPatientLabReports(session: PatientPortalSession) {
  return fetchPatientPortalJson<PatientPortalLabOrderResponse[]>("/api/patient-portal/lab/reports", session);
}

export async function getPatientLabLatestResults(session: PatientPortalSession, query?: string | null) {
  const suffix = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : "";
  return fetchPatientPortalJson<PatientPortalLabLatestResultResponse[]>(`/api/patient-portal/lab/reports/latest${suffix}`, session);
}

export async function getPatientNotifications(session: PatientPortalSession) {
  return fetchPatientPortalJson<PatientPortalNotificationResponse[]>("/api/patient-portal/notifications", session);
}

export async function markPatientNotificationRead(session: PatientPortalSession, id: string) {
  const response = await fetch(buildUrl(`/api/patient-portal/notifications/${id}/read`), {
    method: "POST",
    headers: buildHeaders(session),
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<PatientPortalNotificationResponse>;
}

export async function getPatientLabReportPdfPath(orderNumber: string) {
  return `/api/patient-portal/lab/orders/${encodeURIComponent(orderNumber)}/pdf`;
}
