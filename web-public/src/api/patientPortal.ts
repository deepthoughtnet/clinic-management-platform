export type PatientPortalSession = {
  mode: "otp";
  tenantCode: string;
  tenantId: string;
  phone: string;
  patientLabel: string;
  createdAt: string;
  patientSessionToken: string;
};

export type PatientPortalOtpRequestResponse = {
  accepted: boolean;
  message: string;
  expiresInSeconds: number;
  resendAvailableInSeconds: number;
  devOtp: string | null;
};

export type PatientPortalOtpVerifyResponse = {
  verified: boolean;
  message: string;
  tenantId: string | null;
  patientDisplayName: string | null;
  patientSessionToken: string | null;
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
  bloodGroup: string | null;
  allergies: string | null;
  chronicConditions: string | null;
  longTermMedications: string | null;
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
  appointmentDate: string;
  appointmentTime: string;
  reason: string | null;
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
  billDate: string | null;
  status: string | null;
  totalAmount: number | null;
  paidAmount: number | null;
  dueAmount: number | null;
  latestReceipt: PatientPortalBillReceiptSummaryResponse | null;
  lines: PatientPortalBillLineResponse[];
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
  doctorName: string | null;
  speciality: string | null;
  preferredDate: string | null;
  preferredTimeWindow: string | null;
  suggestedSlot: string | null;
  confirmationPending: boolean;
  booked: boolean;
  bookedAppointmentDate: string | null;
  bookedAppointmentTime: string | null;
  bookingStatus: string | null;
  handoffRequired: boolean;
  handoffReason: string | null;
  doctorOptions: string[];
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
    credentials: "include",
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
    credentials: "include",
    signal,
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

export async function openPatientPortalPdf(path: string, session: PatientPortalSession) {
  const response = await fetch(buildUrl(path), {
    headers: buildHeaders(session, {
      Accept: "application/pdf",
    }),
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  const blob = await response.blob();
  const blobUrl = window.URL.createObjectURL(blob);
  window.open(blobUrl, "_blank", "noopener,noreferrer");
  window.setTimeout(() => window.URL.revokeObjectURL(blobUrl), 60_000);
}
