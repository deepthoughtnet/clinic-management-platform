import { httpGet, httpPatch, httpPost, httpPut } from "./restClient";

export type PatientGender = "MALE" | "FEMALE" | "OTHER" | "UNKNOWN";
export type AppointmentStatus = "BOOKED" | "WAITING" | "IN_CONSULTATION" | "COMPLETED" | "CANCELLED" | "NO_SHOW";
export type AppointmentType = "WALK_IN" | "SCHEDULED" | "FOLLOW_UP" | "VACCINATION";
export type ConsultationStatus = "DRAFT" | "COMPLETED" | "CANCELLED";
export type TemperatureUnit = "CELSIUS" | "FAHRENHEIT";
export type PrescriptionStatus = "DRAFT" | "FINALIZED" | "PRINTED" | "SENT" | "CANCELLED";
export type MedicineType = "TABLET" | "SYRUP" | "INJECTION" | "DROP" | "OINTMENT" | "CAPSULE" | "OTHER";
export type Timing = "BEFORE_FOOD" | "AFTER_FOOD" | "WITH_FOOD" | "ANYTIME";
export type NotificationStatus = "PENDING" | "SENT" | "FAILED" | "SKIPPED";
export type NotificationChannel = "EMAIL" | "WHATSAPP" | "SMS" | "PUSH";
export type NotificationEventType =
  | "PRESCRIPTION_READY"
  | "PRESCRIPTION_SENT"
  | "BILL_PAID"
  | "RECEIPT_SENT"
  | "FOLLOW_UP_REMINDER"
  | "VACCINATION_REMINDER"
  | "APPOINTMENT_REMINDER";
export type InventoryTransactionType = "OPENING" | "PURCHASE" | "SALE" | "ADJUSTMENT" | "RETURN";

export type DashboardSummary = {
  todayAppointments: number;
  waitingPatients: number;
  inConsultationCount: number;
  completedConsultations: number;
  todayRevenue: number;
  pendingDues: number;
  followUpsDue: number;
  vaccinationsDue: number;
  lowStockMedicines: number;
};

export type NotificationHistory = {
  id: string;
  tenantId: string;
  patientId: string | null;
  eventType: NotificationEventType;
  channel: NotificationChannel;
  recipient: string;
  subject: string | null;
  message: string;
  status: NotificationStatus;
  failureReason: string | null;
  sourceType: string | null;
  sourceId: string | null;
  deduplicationKey: string | null;
  outboxEventId: string | null;
  attemptCount: number;
  sentAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ReportRow = Record<string, string | number | boolean | null>;

export type ClinicProfile = {
  id: string;
  tenantId: string;
  clinicName: string;
  displayName: string;
  phone: string | null;
  email: string | null;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  state: string;
  country: string;
  postalCode: string;
  registrationNumber: string | null;
  gstNumber: string | null;
  logoDocumentId: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ClinicProfileInput = Omit<ClinicProfile, "id" | "tenantId" | "createdAt" | "updatedAt">;

export type ClinicUser = {
  appUserId: string;
  tenantId: string;
  keycloakSub: string | null;
  email: string | null;
  displayName: string | null;
  userStatus: string;
  membershipRole: string | null;
  membershipStatus: string | null;
  createdAt: string;
  updatedAt: string;
  provisioningStatus: string | null;
};

export type ClinicRole = {
  role: string;
  displayName: string;
  permissions: string[];
};

export type Patient = {
  id: string;
  tenantId: string;
  patientNumber: string;
  firstName: string;
  lastName: string;
  gender: PatientGender;
  dateOfBirth: string | null;
  ageYears: number | null;
  mobile: string;
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
  existingConditions: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PatientDetail = {
  patient: Patient;
  upcomingAppointments: Appointment[];
  recentAppointments: Appointment[];
  previousConsultations: Consultation[];
};

export type PatientInput = Omit<Patient, "id" | "tenantId" | "patientNumber" | "createdAt" | "updatedAt">;

export type Appointment = {
  id: string;
  tenantId: string;
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  doctorUserId: string;
  doctorName: string | null;
  appointmentDate: string;
  appointmentTime: string | null;
  tokenNumber: number | null;
  reason: string | null;
  type: AppointmentType;
  status: AppointmentStatus;
  createdAt: string;
  updatedAt: string;
};

export type AppointmentInput = {
  patientId: string;
  doctorUserId: string;
  appointmentDate: string;
  appointmentTime: string | null;
  reason: string | null;
  type: AppointmentType;
  status: AppointmentStatus | null;
};

export type WalkInAppointmentInput = {
  patientId: string;
  doctorUserId: string;
  appointmentDate: string;
  reason: string | null;
};

export type DoctorAvailability = {
  id: string;
  tenantId: string;
  doctorUserId: string;
  doctorName: string | null;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  consultationDurationMinutes: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type DoctorAvailabilityInput = {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  consultationDurationMinutes: number;
  active: boolean;
};

export type Consultation = {
  id: string;
  tenantId: string;
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  doctorUserId: string;
  doctorName: string | null;
  appointmentId: string | null;
  chiefComplaints: string | null;
  symptoms: string | null;
  diagnosis: string | null;
  clinicalNotes: string | null;
  advice: string | null;
  followUpDate: string | null;
  status: ConsultationStatus;
  bloodPressureSystolic: number | null;
  bloodPressureDiastolic: number | null;
  pulseRate: number | null;
  temperature: number | null;
  temperatureUnit: TemperatureUnit | null;
  weightKg: number | null;
  heightCm: number | null;
  spo2: number | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ConsultationInput = {
  patientId: string;
  doctorUserId: string;
  appointmentId: string | null;
  chiefComplaints: string | null;
  symptoms: string | null;
  diagnosis: string | null;
  clinicalNotes: string | null;
  advice: string | null;
  followUpDate: string | null;
  bloodPressureSystolic: number | null;
  bloodPressureDiastolic: number | null;
  pulseRate: number | null;
  temperature: number | null;
  temperatureUnit: TemperatureUnit | null;
  weightKg: number | null;
  heightCm: number | null;
  spo2: number | null;
};

export type PrescriptionMedicine = {
  medicineName: string;
  medicineType: MedicineType | null;
  strength: string | null;
  dosage: string;
  frequency: string;
  duration: string;
  timing: Timing | null;
  instructions: string | null;
  sortOrder: number | null;
};

export type PrescriptionTest = {
  testName: string;
  instructions: string | null;
  sortOrder: number | null;
};

export type Prescription = {
  id: string;
  tenantId: string;
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  doctorUserId: string;
  doctorName: string | null;
  consultationId: string;
  appointmentId: string | null;
  prescriptionNumber: string;
  diagnosisSnapshot: string | null;
  advice: string | null;
  followUpDate: string | null;
  status: PrescriptionStatus;
  finalizedAt: string | null;
  printedAt: string | null;
  sentAt: string | null;
  createdAt: string;
  updatedAt: string;
  medicines: PrescriptionMedicine[];
  recommendedTests: PrescriptionTest[];
};

export type BillStatus = "DRAFT" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
export type BillItemType = "CONSULTATION" | "MEDICINE" | "TEST" | "VACCINATION" | "PROCEDURE" | "OTHER";
export type PaymentMode = "CASH" | "UPI" | "CARD" | "BANK_TRANSFER" | "OTHER";

export type BillLine = {
  id: string | null;
  itemType: BillItemType;
  itemName: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  referenceId: string | null;
  sortOrder: number | null;
};

export type Bill = {
  id: string;
  tenantId: string;
  billNumber: string;
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  consultationId: string | null;
  appointmentId: string | null;
  billDate: string;
  status: BillStatus;
  subtotalAmount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  dueAmount: number;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  lines: BillLine[];
};

export type BillInput = {
  patientId: string;
  consultationId: string | null;
  appointmentId: string | null;
  billDate: string;
  discountAmount: number | null;
  taxAmount: number | null;
  notes: string | null;
  lines: Array<Omit<BillLine, "id" | "totalPrice"> & { unitPrice: number; quantity: number; referenceId: string | null }>;
};

export type Payment = {
  id: string;
  tenantId: string;
  billId: string;
  paymentDate: string;
  amount: number;
  paymentMode: PaymentMode;
  referenceNumber: string | null;
  notes: string | null;
  receiptId: string | null;
  receiptNumber: string | null;
  receiptDate: string | null;
  createdAt: string;
};

export type PaymentInput = {
  paymentDate: string;
  amount: number;
  paymentMode: PaymentMode;
  referenceNumber: string | null;
  notes: string | null;
};

export type Receipt = {
  id: string;
  tenantId: string;
  receiptNumber: string;
  billId: string;
  paymentId: string;
  receiptDate: string;
  amount: number;
  createdAt: string;
};

export type VaccineMaster = {
  id: string;
  tenantId: string;
  vaccineName: string;
  description: string | null;
  ageGroup: string | null;
  recommendedGapDays: number | null;
  defaultPrice: number | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type VaccineInput = {
  vaccineName: string;
  description: string | null;
  ageGroup: string | null;
  recommendedGapDays: number | null;
  defaultPrice: number | null;
  active: boolean;
};

export type PatientVaccination = {
  id: string;
  tenantId: string;
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  vaccineId: string | null;
  vaccineName: string;
  doseNumber: number | null;
  givenDate: string;
  nextDueDate: string | null;
  batchNumber: string | null;
  notes: string | null;
  administeredByUserId: string | null;
  administeredByUserName: string | null;
  createdAt: string;
};

export type PatientVaccinationInput = {
  vaccineId: string;
  doseNumber: number | null;
  givenDate: string | null;
  nextDueDate: string | null;
  batchNumber: string | null;
  notes: string | null;
  administeredByUserId: string | null;
  billId: string | null;
  addToBill: boolean;
  billItemUnitPrice: number | null;
};

export type MedicineInput = {
  medicineName: string;
  medicineType: MedicineType;
  strength: string | null;
  defaultDosage: string | null;
  defaultFrequency: string | null;
  defaultDurationDays: number | null;
  defaultTiming: Timing | null;
  defaultInstructions: string | null;
  defaultPrice: number | null;
  active: boolean;
};

export type Medicine = MedicineInput & {
  id: string;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
};

export type StockInput = {
  medicineId: string;
  batchNumber: string | null;
  expiryDate: string | null;
  quantityOnHand: number;
  lowStockThreshold: number | null;
  unitCost: number | null;
  sellingPrice: number | null;
  active: boolean;
};

export type Stock = StockInput & {
  id: string;
  tenantId: string;
  medicineName: string;
  medicineType: MedicineType;
  createdAt: string;
  updatedAt: string;
};

export type InventoryTransaction = {
  id: string;
  tenantId: string;
  medicineId: string;
  stockBatchId: string | null;
  transactionType: InventoryTransactionType;
  quantity: number;
  referenceType: string | null;
  referenceId: string | null;
  notes: string | null;
  createdAt: string;
};

export type InventoryTransactionInput = {
  medicineId: string;
  stockBatchId: string | null;
  transactionType: InventoryTransactionType;
  quantity: number;
  referenceType: string | null;
  referenceId: string | null;
  notes: string | null;
};

export type LowStockItem = {
  stockId: string;
  medicineId: string;
  medicineName: string;
  batchNumber: string | null;
  expiryDate: string | null;
  quantityOnHand: number;
  lowStockThreshold: number | null;
};

export type ConsultationInputBody = ConsultationInput;
export type PrescriptionInput = {
  patientId: string;
  doctorUserId: string;
  consultationId: string;
  appointmentId: string | null;
  diagnosisSnapshot: string | null;
  advice: string | null;
  followUpDate: string | null;
  medicines: PrescriptionMedicine[];
  recommendedTests: PrescriptionTest[];
};

export type PatientSearchParams = {
  patientNumber?: string;
  mobile?: string;
  name?: string;
  active?: boolean | null;
};

export async function getClinicProfile(token: string, tenantId: string) {
  return httpGet<ClinicProfile>("/api/clinic/profile", { token, tenantId });
}

export async function updateClinicProfile(token: string, tenantId: string, profile: ClinicProfileInput) {
  return httpPut<ClinicProfile>("/api/clinic/profile", profile, { token, tenantId });
}

export async function getClinicUsers(token: string, tenantId: string) {
  return httpGet<ClinicUser[]>("/api/clinic/users", { token, tenantId });
}

export async function getClinicRoles(token: string, tenantId: string) {
  return httpGet<ClinicRole[]>("/api/clinic/roles", { token, tenantId });
}

export async function searchPatients(token: string, tenantId: string, params: PatientSearchParams = {}) {
  const query = new URLSearchParams();
  if (params.patientNumber) query.set("patientNumber", params.patientNumber);
  if (params.mobile) query.set("mobile", params.mobile);
  if (params.name) query.set("name", params.name);
  if (params.active !== undefined && params.active !== null) query.set("active", String(params.active));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<Patient[]>(`/api/patients${suffix}`, { token, tenantId });
}

export async function getPatient(token: string, tenantId: string, id: string) {
  return httpGet<PatientDetail>(`/api/patients/${id}`, { token, tenantId });
}

export async function getPatientConsultations(token: string, tenantId: string, patientId: string) {
  return httpGet<Consultation[]>(`/api/patients/${patientId}/consultations`, { token, tenantId });
}

export async function getPatientPrescriptions(token: string, tenantId: string, patientId: string) {
  return httpGet<Prescription[]>(`/api/patients/${patientId}/prescriptions`, { token, tenantId });
}

export async function createPatient(token: string, tenantId: string, body: PatientInput) {
  return httpPost<Patient>("/api/patients", body, { token, tenantId });
}

export async function updatePatient(token: string, tenantId: string, id: string, body: PatientInput) {
  return httpPut<Patient>(`/api/patients/${id}`, body, { token, tenantId });
}

export async function deactivatePatient(token: string, tenantId: string, id: string) {
  return httpPatch<Patient>(`/api/patients/${id}/deactivate`, undefined, { token, tenantId });
}

export async function searchAppointments(
  token: string,
  tenantId: string,
  params: {
    doctorUserId?: string;
    patientId?: string;
    appointmentDate?: string;
    status?: AppointmentStatus | null;
    type?: AppointmentType | null;
  } = {},
) {
  const query = new URLSearchParams();
  if (params.doctorUserId) query.set("doctorUserId", params.doctorUserId);
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.appointmentDate) query.set("appointmentDate", params.appointmentDate);
  if (params.status) query.set("status", params.status);
  if (params.type) query.set("type", params.type);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<Appointment[]>(`/api/appointments${suffix}`, { token, tenantId });
}

export async function getAppointment(token: string, tenantId: string, id: string) {
  return httpGet<Appointment>(`/api/appointments/${id}`, { token, tenantId });
}

export async function createAppointment(token: string, tenantId: string, body: AppointmentInput) {
  return httpPost<Appointment>("/api/appointments", body, { token, tenantId });
}

export async function createWalkInAppointment(token: string, tenantId: string, body: WalkInAppointmentInput) {
  return httpPost<Appointment>("/api/appointments/walk-in", body, { token, tenantId });
}

export async function updateAppointmentStatus(token: string, tenantId: string, id: string, status: AppointmentStatus) {
  return httpPatch<Appointment>(`/api/appointments/${id}/status`, { status }, { token, tenantId });
}

export async function getTodayAppointments(token: string, tenantId: string) {
  return httpGet<Appointment[]>("/api/appointments/today", { token, tenantId });
}

export async function getDoctorQueueToday(token: string, tenantId: string, doctorUserId: string) {
  return httpGet<Appointment[]>(`/api/doctors/${doctorUserId}/queue/today`, { token, tenantId });
}

export async function getDoctorAvailability(token: string, tenantId: string) {
  return httpGet<DoctorAvailability[]>("/api/doctors/availability", { token, tenantId });
}

export async function createDoctorAvailability(token: string, tenantId: string, doctorUserId: string, body: DoctorAvailabilityInput) {
  return httpPost<DoctorAvailability>(`/api/doctors/${doctorUserId}/availability`, body, { token, tenantId });
}

export async function updateDoctorAvailability(token: string, tenantId: string, id: string, body: DoctorAvailabilityInput) {
  return httpPut<DoctorAvailability>(`/api/doctors/availability/${id}`, body, { token, tenantId });
}

export async function deactivateDoctorAvailability(token: string, tenantId: string, id: string) {
  return httpPatch<DoctorAvailability>(`/api/doctors/availability/${id}/deactivate`, undefined, { token, tenantId });
}

export async function getConsultations(token: string, tenantId: string) {
  return httpGet<Consultation[]>("/api/consultations", { token, tenantId });
}

export async function getConsultation(token: string, tenantId: string, id: string) {
  return httpGet<Consultation>(`/api/consultations/${id}`, { token, tenantId });
}

export async function createConsultation(token: string, tenantId: string, body: ConsultationInputBody) {
  return httpPost<Consultation>("/api/consultations", body, { token, tenantId });
}

export async function updateConsultation(token: string, tenantId: string, id: string, body: ConsultationInputBody) {
  return httpPut<Consultation>(`/api/consultations/${id}`, body, { token, tenantId });
}

export async function completeConsultation(token: string, tenantId: string, id: string) {
  return httpPatch<Consultation>(`/api/consultations/${id}/complete`, undefined, { token, tenantId });
}

export async function cancelConsultation(token: string, tenantId: string, id: string) {
  return httpPatch<Consultation>(`/api/consultations/${id}/cancel`, undefined, { token, tenantId });
}

export async function startConsultationFromAppointment(token: string, tenantId: string, appointmentId: string) {
  return httpPost<Consultation>(`/api/appointments/${appointmentId}/start-consultation`, undefined, { token, tenantId });
}

export async function getPrescriptions(token: string, tenantId: string) {
  return httpGet<Prescription[]>("/api/prescriptions", { token, tenantId });
}

export async function getPrescription(token: string, tenantId: string, id: string) {
  return httpGet<Prescription>(`/api/prescriptions/${id}`, { token, tenantId });
}

export async function getConsultationPrescription(token: string, tenantId: string, consultationId: string) {
  return httpGet<Prescription>(`/api/prescriptions/consultations/${consultationId}`, { token, tenantId });
}

export async function createPrescription(token: string, tenantId: string, body: PrescriptionInput) {
  return httpPost<Prescription>("/api/prescriptions", body, { token, tenantId });
}

export async function updatePrescription(token: string, tenantId: string, id: string, body: PrescriptionInput) {
  return httpPut<Prescription>(`/api/prescriptions/${id}`, body, { token, tenantId });
}

export async function finalizePrescription(token: string, tenantId: string, id: string) {
  return httpPatch<Prescription>(`/api/prescriptions/${id}/finalize`, undefined, { token, tenantId });
}

export async function printPrescription(token: string, tenantId: string, id: string) {
  return httpPost<Prescription>(`/api/prescriptions/${id}/print`, undefined, { token, tenantId });
}

export async function markPrescriptionSent(token: string, tenantId: string, id: string) {
  return httpPost<Prescription>(`/api/prescriptions/${id}/mark-sent`, undefined, { token, tenantId });
}

export async function getPrescriptionPdf(token: string, tenantId: string, id: string) {
  const res = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/prescriptions/${id}/pdf`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
      Accept: "application/pdf",
    },
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  }
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") || "";
  const match = disposition.match(/filename="?([^"]+)"?/i);
  return {
    blob,
    filename: match?.[1] || `prescription-${id}.pdf`,
  };
}

export async function searchBills(
  token: string,
  tenantId: string,
  params: { patientId?: string; status?: BillStatus | null } = {},
) {
  const query = new URLSearchParams();
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.status) query.set("status", params.status);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<Bill[]>(`/api/bills${suffix}`, { token, tenantId });
}

export async function getBill(token: string, tenantId: string, id: string) {
  return httpGet<Bill>(`/api/bills/${id}`, { token, tenantId });
}

export async function createBill(token: string, tenantId: string, body: BillInput) {
  return httpPost<Bill>("/api/bills", body, { token, tenantId });
}

export async function updateBill(token: string, tenantId: string, id: string, body: BillInput) {
  return httpPut<Bill>(`/api/bills/${id}`, body, { token, tenantId });
}

export async function issueBill(token: string, tenantId: string, id: string) {
  return httpPatch<Bill>(`/api/bills/${id}/issue`, undefined, { token, tenantId });
}

export async function cancelBill(token: string, tenantId: string, id: string) {
  return httpPatch<Bill>(`/api/bills/${id}/cancel`, undefined, { token, tenantId });
}

export async function addBillPayment(token: string, tenantId: string, billId: string, body: PaymentInput) {
  return httpPost<Payment>(`/api/bills/${billId}/payments`, body, { token, tenantId });
}

export async function listBillPayments(token: string, tenantId: string, billId: string) {
  return httpGet<Payment[]>(`/api/bills/${billId}/payments`, { token, tenantId });
}

export async function listBillReceipts(token: string, tenantId: string, billId: string) {
  return httpGet<Receipt[]>(`/api/bills/${billId}/receipts`, { token, tenantId });
}

export async function getReceipt(token: string, tenantId: string, id: string) {
  return httpGet<Receipt>(`/api/receipts/${id}`, { token, tenantId });
}

export async function getBillPdf(token: string, tenantId: string, id: string) {
  const res = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/bills/${id}/pdf`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
      Accept: "application/pdf",
    },
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  }
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") || "";
  const match = disposition.match(/filename="?([^"]+)"?/i);
  return {
    blob,
    filename: match?.[1] || `bill-${id}.pdf`,
  };
}

export async function getReceiptPdf(token: string, tenantId: string, id: string) {
  const res = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/receipts/${id}/pdf`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
      Accept: "application/pdf",
    },
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  }
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") || "";
  const match = disposition.match(/filename="?([^"]+)"?/i);
  return {
    blob,
    filename: match?.[1] || `receipt-${id}.pdf`,
  };
}

export async function getVaccines(token: string, tenantId: string) {
  return httpGet<VaccineMaster[]>("/api/vaccines", { token, tenantId });
}

export async function createVaccine(token: string, tenantId: string, body: VaccineInput) {
  return httpPost<VaccineMaster>("/api/vaccines", body, { token, tenantId });
}

export async function updateVaccine(token: string, tenantId: string, id: string, body: VaccineInput) {
  return httpPut<VaccineMaster>(`/api/vaccines/${id}`, body, { token, tenantId });
}

export async function deactivateVaccine(token: string, tenantId: string, id: string) {
  return httpPatch<VaccineMaster>(`/api/vaccines/${id}/deactivate`, undefined, { token, tenantId });
}

export async function getPatientVaccinations(token: string, tenantId: string, patientId: string) {
  return httpGet<PatientVaccination[]>(`/api/patients/${patientId}/vaccinations`, { token, tenantId });
}

export async function recordPatientVaccination(token: string, tenantId: string, patientId: string, body: PatientVaccinationInput) {
  return httpPost<PatientVaccination>(`/api/patients/${patientId}/vaccinations`, body, { token, tenantId });
}

export async function getDueVaccinations(token: string, tenantId: string) {
  return httpGet<PatientVaccination[]>("/api/vaccinations/due", { token, tenantId });
}

export async function getOverdueVaccinations(token: string, tenantId: string) {
  return httpGet<PatientVaccination[]>("/api/vaccinations/overdue", { token, tenantId });
}

export async function getNotifications(
  token: string,
  tenantId: string,
  params: {
    status?: NotificationStatus | null;
    eventType?: NotificationEventType | null;
    channel?: NotificationChannel | null;
    patientId?: string | null;
    from?: string | null;
    to?: string | null;
  } = {},
) {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.eventType) query.set("eventType", params.eventType);
  if (params.channel) query.set("channel", params.channel);
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<NotificationHistory[]>(`/api/notifications${suffix}`, { token, tenantId });
}

export async function getPatientNotifications(token: string, tenantId: string, patientId: string) {
  return httpGet<NotificationHistory[]>(`/api/notifications/patients/${patientId}`, { token, tenantId });
}

export async function retryNotification(token: string, tenantId: string, id: string) {
  return httpPost<NotificationHistory>(`/api/notifications/${id}/retry`, undefined, { token, tenantId });
}

export async function sendPrescription(token: string, tenantId: string, id: string, channel: string = "email") {
  return httpPost<Prescription>(`/api/prescriptions/${id}/send`, { channel }, { token, tenantId });
}

export async function sendReceipt(token: string, tenantId: string, id: string, channel: string = "email") {
  return httpPost<Receipt>(`/api/receipts/${id}/send?channel=${encodeURIComponent(channel)}`, undefined, { token, tenantId });
}

export async function getDashboardSummary(token: string, tenantId: string) {
  return httpGet<DashboardSummary>("/api/dashboard/summary", { token, tenantId });
}

function buildReportQuery(params: {
  from?: string | null;
  to?: string | null;
  doctorUserId?: string | null;
  patientId?: string | null;
  status?: string | null;
} = {}) {
  const query = new URLSearchParams();
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  if (params.doctorUserId) query.set("doctorUserId", params.doctorUserId);
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.status) query.set("status", params.status);
  return query.toString() ? `?${query.toString()}` : "";
}

export async function getPatientVisitsReport(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; doctorUserId?: string | null; patientId?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/patient-visits${buildReportQuery(params)}`, { token, tenantId });
}

export async function getDoctorConsultationsReport(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; doctorUserId?: string | null; patientId?: string | null; status?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/doctor-consultations${buildReportQuery(params)}`, { token, tenantId });
}

export async function getRevenueReport(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; doctorUserId?: string | null; patientId?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/revenue${buildReportQuery(params)}`, { token, tenantId });
}

export async function getPaymentModesReport(token: string, tenantId: string, params?: { from?: string | null; to?: string | null }) {
  return httpGet<ReportRow[]>(`/api/reports/payment-modes${buildReportQuery(params)}`, { token, tenantId });
}

export async function getPendingDuesReport(token: string, tenantId: string) {
  return httpGet<ReportRow[]>("/api/reports/pending-dues", { token, tenantId });
}

export async function getVaccinationsDueReport(token: string, tenantId: string) {
  return httpGet<ReportRow[]>("/api/reports/vaccinations-due", { token, tenantId });
}

export async function getFollowUpsReport(token: string, tenantId: string) {
  return httpGet<ReportRow[]>("/api/reports/follow-ups", { token, tenantId });
}

export async function getLowStockReport(token: string, tenantId: string) {
  return httpGet<ReportRow[]>("/api/reports/low-stock", { token, tenantId });
}

export async function getPrescriptionsReport(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; doctorUserId?: string | null; patientId?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/prescriptions${buildReportQuery(params)}`, { token, tenantId });
}

export async function getMedicines(token: string, tenantId: string) {
  return httpGet<Medicine[]>("/api/medicines", { token, tenantId });
}

export async function getMedicine(token: string, tenantId: string, id: string) {
  return httpGet<Medicine>(`/api/medicines/${id}`, { token, tenantId });
}

export async function createMedicine(token: string, tenantId: string, body: MedicineInput) {
  return httpPost<Medicine>("/api/medicines", body, { token, tenantId });
}

export async function updateMedicine(token: string, tenantId: string, id: string, body: MedicineInput) {
  return httpPut<Medicine>(`/api/medicines/${id}`, body, { token, tenantId });
}

export async function deactivateMedicine(token: string, tenantId: string, id: string) {
  return httpPatch<Medicine>(`/api/medicines/${id}/deactivate`, undefined, { token, tenantId });
}

export async function getStocks(token: string, tenantId: string) {
  return httpGet<Stock[]>("/api/inventory/stocks", { token, tenantId });
}

export async function createStock(token: string, tenantId: string, body: StockInput) {
  return httpPost<Stock>("/api/inventory/stocks", body, { token, tenantId });
}

export async function updateStock(token: string, tenantId: string, id: string, body: StockInput) {
  return httpPut<Stock>(`/api/inventory/stocks/${id}`, body, { token, tenantId });
}

export async function getInventoryTransactions(token: string, tenantId: string) {
  return httpGet<InventoryTransaction[]>("/api/inventory/transactions", { token, tenantId });
}

export async function createInventoryTransaction(token: string, tenantId: string, body: InventoryTransactionInput) {
  return httpPost<InventoryTransaction>("/api/inventory/transactions", body, { token, tenantId });
}

export async function getLowStock(token: string, tenantId: string) {
  return httpGet<LowStockItem[]>("/api/inventory/low-stock", { token, tenantId });
}
