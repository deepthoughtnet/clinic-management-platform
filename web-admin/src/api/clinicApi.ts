import { httpGet, httpPatch, httpPost, httpPostForm, httpPut } from "./restClient";

export type PatientGender = "MALE" | "FEMALE" | "OTHER" | "UNKNOWN";
export type AppointmentStatus = "BOOKED" | "WAITING" | "IN_CONSULTATION" | "COMPLETED" | "CANCELLED" | "NO_SHOW";
export type AppointmentType = "WALK_IN" | "SCHEDULED" | "FOLLOW_UP" | "VACCINATION";
export type AppointmentPriority = "URGENT" | "MANUAL_PRIORITY" | "FOLLOW_UP" | "CHILD" | "ELDERLY" | "NORMAL";
export type ConsultationStatus = "DRAFT" | "COMPLETED" | "CANCELLED";
export type TemperatureUnit = "CELSIUS" | "FAHRENHEIT";
export type PrescriptionStatus = "DRAFT" | "PREVIEWED" | "FINALIZED" | "CORRECTED" | "SUPERSEDED" | "PRINTED" | "SENT" | "CANCELLED";
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
  | "APPOINTMENT_REMINDER"
  | "PAYMENT_REMINDER"
  | "MISSED_APPOINTMENT_REMINDER";
export type InventoryTransactionType = "OPENING" | "PURCHASE" | "SALE" | "ADJUSTMENT" | "RETURN";
export type ClinicalDocumentType =
  | "LAB_REPORT"
  | "PRESCRIPTION"
  | "X_RAY"
  | "MRI_CT"
  | "REFERRAL"
  | "DISCHARGE_SUMMARY"
  | "INSURANCE"
  | "VACCINATION"
  | "OTHER";

export type ClinicalDocument = {
  id: string;
  patientId: string;
  consultationId: string | null;
  appointmentId: string | null;
  uploadedByAppUserId: string;
  documentType: ClinicalDocumentType;
  originalFilename: string;
  mediaType: string;
  sizeBytes: number;
  checksumSha256: string;
  notes: string | null;
  referredDoctor: string | null;
  referredHospital: string | null;
  referralNotes: string | null;
  aiExtractionStatus: string | null;
  aiExtractionProvider: string | null;
  aiExtractionModel: string | null;
  aiExtractionConfidence: number | null;
  aiExtractionSummary: string | null;
  aiExtractionStructuredJson: string | null;
  aiExtractionReviewNotes: string | null;
  aiExtractionAcceptedJson: string | null;
  aiExtractionOverrideReason: string | null;
  aiExtractionReviewedByAppUserId: string | null;
  aiExtractionReviewedAt: string | null;
  ocrStatus: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AiClinicalAnalytics = {
  tenantId: string;
  from: string;
  to: string;
  requestCount: number;
  successCount: number;
  failedCount: number;
  fallbackCount: number;
  documentCount: number;
  reviewRequiredCount: number;
  approvedCount: number;
  rejectedCount: number;
  retryCount: number;
  averageConfidence: number;
  acceptanceRate: number;
};

export type AiStatus = {
  tenantModuleEnabled: boolean;
  runtimeEnabled: boolean;
  provider: string;
  providerConfigured: boolean;
  geminiEnabled: boolean;
  geminiConfigured: boolean;
  ocrEnabled: boolean;
  ocrProvider: string;
  userCanUseAi: boolean;
  effectiveStatus: string;
  message: string;
};

export type PatientTimelineItem = {
  id: string;
  itemType: "DOCUMENT" | "CONSULTATION" | "PRESCRIPTION" | string;
  title: string;
  subtitle: string | null;
  occurredAt: string;
  status: string | null;
  documentType: ClinicalDocumentType | null;
  documentId: string | null;
  consultationId: string | null;
  prescriptionId: string | null;
};

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
  pendingNotifications: number;
  failedNotifications: number;
  sentNotificationsToday: number;
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

export type DoctorProfile = {
  doctorUserId: string;
  doctorName: string | null;
  email: string | null;
  membershipRole: string | null;
  mobile: string | null;
  specialization: string | null;
  qualification: string | null;
  registrationNumber: string | null;
  consultationRoom: string | null;
  active: boolean;
  updatedAt: string | null;
};

export type DoctorProfileInput = {
  mobile: string | null;
  specialization: string | null;
  qualification: string | null;
  registrationNumber: string | null;
  consultationRoom: string | null;
  active: boolean | null;
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
  longTermMedications: string | null;
  surgicalHistory: string | null;
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
  patientMobile: string | null;
  doctorUserId: string;
  doctorName: string | null;
  consultationId: string | null;
  appointmentDate: string;
  appointmentTime: string | null;
  tokenNumber: number | null;
  reason: string | null;
  type: AppointmentType;
  priority: AppointmentPriority;
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
  priority: AppointmentPriority | null;
};

export type WalkInAppointmentInput = {
  patientId: string;
  doctorUserId: string;
  appointmentDate: string;
  reason: string | null;
  priority: AppointmentPriority | null;
};

export type DoctorAvailability = {
  id: string;
  tenantId: string;
  doctorUserId: string;
  doctorName: string | null;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  breakStartTime: string | null;
  breakEndTime: string | null;
  consultationDurationMinutes: number;
  maxPatientsPerSlot: number | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type DoctorAvailabilityInput = {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  breakStartTime: string | null;
  breakEndTime: string | null;
  consultationDurationMinutes: number;
  maxPatientsPerSlot: number | null;
  active: boolean;
};

export type DoctorAvailabilitySlotStatus = "AVAILABLE" | "BOOKED" | "UNAVAILABLE";

export type DoctorAvailabilitySlot = {
  doctorUserId: string;
  doctorName: string | null;
  appointmentDate: string;
  slotTime: string;
  slotEndTime: string;
  status: DoctorAvailabilitySlotStatus;
  bookedCount: number;
  maxPatientsPerSlot: number;
  selectable: boolean;
  appointmentId: string | null;
  patientId: string | null;
  patientNumber: string | null;
  patientName: string | null;
  tokenNumber: number | null;
  appointmentStatus: AppointmentStatus | null;
  reason: string | null;
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
  respiratoryRate: number | null;
  bmi: number | null;
  bmiCategory: string | null;
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
  respiratoryRate: number | null;
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
  versionNumber: number | null;
  parentPrescriptionId: string | null;
  correctionReason: string | null;
  flowType: string | null;
  correctedAt: string | null;
  supersededByPrescriptionId: string | null;
  supersededAt: string | null;
  diagnosisSnapshot: string | null;
  advice: string | null;
  followUpDate: string | null;
  status: PrescriptionStatus;
  finalizedAt: string | null;
  finalizedByDoctorUserId: string | null;
  printedAt: string | null;
  sentAt: string | null;
  createdAt: string;
  updatedAt: string;
  medicines: PrescriptionMedicine[];
  recommendedTests: PrescriptionTest[];
};

export type BillStatus = "DRAFT" | "UNPAID" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
export type BillItemType = "CONSULTATION" | "MEDICINE" | "TEST" | "VACCINATION" | "PROCEDURE" | "OTHER";
export type PaymentMode = "CASH" | "CARD" | "UPI" | "PAYTM" | "PHONEPE" | "GOOGLE_PAY" | "BANK_TRANSFER" | "CHEQUE" | "OTHER";

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

export type AiDraftResponse = {
  enabled: boolean;
  fallbackUsed: boolean;
  message: string;
  provider: string | null;
  model: string | null;
  draft: string | null;
  structuredData: Record<string, unknown>;
  confidence: number | null;
  suggestedActions: string[];
  warnings: string[];
};

export type AiRecentRequestRecord = {
  auditId: string;
  productCode: string;
  tenantId: string;
  actorAppUserId: string | null;
  useCaseCode: string | null;
  taskType: string;
  promptTemplateCode: string | null;
  promptTemplateVersion: string | null;
  provider: string | null;
  model: string | null;
  status: string;
  confidence: number | null;
  latencyMs: number | null;
  fallbackUsed: boolean;
  inputSummary: string | null;
  outputSummary: string | null;
  correlationId: string | null;
  createdAt: string;
};

export type AiClinicalSummaryInput = {
  patientId: string;
  patientName?: string | null;
  historyText?: string | null;
  chronicHistory?: string | null;
  recentConsultationSummary?: string | null;
  recentConsultations?: string[];
  currentMedications?: string[];
  allergies?: string[];
  uploadedReportsSummary?: string | null;
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

export async function getDoctorProfile(token: string, tenantId: string, doctorUserId: string) {
  return httpGet<DoctorProfile>(`/api/doctors/${doctorUserId}/profile`, { token, tenantId });
}

export async function updateDoctorProfile(token: string, tenantId: string, doctorUserId: string, body: DoctorProfileInput) {
  return httpPut<DoctorProfile>(`/api/doctors/${doctorUserId}/profile`, body, { token, tenantId });
}

export async function createTenantUser(token: string, tenantId: string, body: {
  email: string;
  username?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  role: string;
  temporaryPassword?: string | null;
  active: boolean;
}) {
  return httpPost<ClinicUser>("/api/tenant/users", {
    email: body.email,
    username: body.username ?? null,
    firstName: body.firstName ?? null,
    lastName: body.lastName ?? null,
    role: body.role,
    temporaryPassword: body.temporaryPassword ?? null,
    active: body.active,
  }, { token, tenantId });
}

export async function updateTenantUser(token: string, tenantId: string, appUserId: string, body: {
  active: boolean;
  role?: string | null;
}) {
  return httpPut<ClinicUser>(`/api/tenant/users/${appUserId}`, body, { token, tenantId });
}

export async function assignTenantUserRole(token: string, tenantId: string, appUserId: string, role: string) {
  return httpPost<ClinicUser>(`/api/tenant/users/${appUserId}/roles`, { role }, { token, tenantId });
}

export async function resetTenantUserPassword(token: string, tenantId: string, appUserId: string, tempPassword: string, temporary = true) {
  return httpPost<ClinicUser>(`/api/tenant/users/${appUserId}/reset-password`, { tempPassword, temporary }, { token, tenantId });
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

export async function updateAppointmentStatus(token: string, tenantId: string, id: string, status: AppointmentStatus, comment?: string | null) {
  return httpPatch<Appointment>(`/api/appointments/${id}/status`, { status, comment: comment || null }, { token, tenantId });
}

export async function updateAppointmentPriority(token: string, tenantId: string, id: string, priority: AppointmentPriority) {
  return httpPatch<Appointment>(`/api/appointments/${id}/priority`, { priority }, { token, tenantId });
}

export async function getTodayAppointments(token: string, tenantId: string) {
  return httpGet<Appointment[]>("/api/appointments/today", { token, tenantId });
}

export async function getDoctorQueueToday(token: string, tenantId: string, doctorUserId: string) {
  return httpGet<Appointment[]>(`/api/doctors/${doctorUserId}/queue/today`, { token, tenantId });
}

export async function reorderDoctorQueueToday(token: string, tenantId: string, doctorUserId: string, orderedAppointmentIds: string[]) {
  return httpPost<Appointment[]>(`/api/appointments/queue/reorder?doctorUserId=${encodeURIComponent(doctorUserId)}`, { orderedAppointmentIds }, { token, tenantId });
}

export async function getDoctorAvailability(token: string, tenantId: string) {
  return httpGet<DoctorAvailability[]>("/api/doctors/availability", { token, tenantId });
}

export async function getDoctorSlots(token: string, tenantId: string, doctorUserId: string, date: string) {
  return httpGet<DoctorAvailabilitySlot[]>(`/api/doctors/${doctorUserId}/slots?date=${encodeURIComponent(date)}`, { token, tenantId });
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

export async function getPrescriptionHistory(token: string, tenantId: string, prescriptionId: string) {
  return httpGet<Prescription[]>(`/api/prescriptions/${prescriptionId}/history`, { token, tenantId });
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

export async function previewPrescription(token: string, tenantId: string, id: string) {
  return httpPost<Prescription>(`/api/prescriptions/${id}/preview`, undefined, { token, tenantId });
}

export async function createPrescriptionCorrection(token: string, tenantId: string, id: string, body: {
  correctionReason: string;
  flowType: "SAME_DAY_CORRECTION" | "FOLLOW_UP";
  prescription: PrescriptionInput;
}) {
  return httpPost<Prescription>(`/api/prescriptions/${id}/corrections`, body, { token, tenantId });
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

export async function aiPatientSummary(token: string, tenantId: string, body: {
  patientId: string;
  patientName?: string | null;
  historyText?: string | null;
  activeConditions?: string | null;
  currentMedications?: string | null;
  allergies?: string | null;
  recentVisits?: string | null;
}) {
  return httpPost<AiDraftResponse>("/api/ai/patient-summary", body, { token, tenantId });
}

export async function aiClinicalSummary(token: string, tenantId: string, body: AiClinicalSummaryInput) {
  return httpPost<AiDraftResponse>("/api/ai/clinical-summary", body, { token, tenantId });
}

export async function aiStructureConsultationNotes(token: string, tenantId: string, body: {
  consultationId: string;
  patientId: string;
  doctorNotes?: string | null;
  symptoms?: string | null;
  vitals?: string | null;
  observations?: string | null;
}) {
  return httpPost<AiDraftResponse>("/api/ai/consultation/structure-notes", body, { token, tenantId });
}

export async function aiSuggestDiagnosis(token: string, tenantId: string, body: {
  consultationId: string;
  patientId: string;
  symptoms?: string | null;
  findings?: string | null;
  doctorNotes?: string | null;
  knownConditions?: string | null;
  allergies?: string | null;
}) {
  return httpPost<AiDraftResponse>("/api/ai/consultation/suggest-diagnosis", body, { token, tenantId });
}

export async function aiSuggestPrescriptionTemplate(token: string, tenantId: string, body: {
  consultationId: string;
  patientId: string;
  diagnosis?: string | null;
  symptoms?: string | null;
  allergies?: string | null;
  currentMedications?: string | null;
  doctorNotes?: string | null;
}) {
  return httpPost<AiDraftResponse>("/api/ai/prescription/suggest-template", body, { token, tenantId });
}

export async function aiPatientInstructions(token: string, tenantId: string, body: {
  consultationId: string;
  patientId: string;
  diagnosis?: string | null;
  prescription?: string | null;
  instructionsContext?: string | null;
  language?: string | null;
  literacyLevel?: string | null;
  allergies?: string | null;
  warnings?: string | null;
}) {
  return httpPost<AiDraftResponse>("/api/ai/patient-instructions", body, { token, tenantId });
}

export async function reviewClinicalDocumentExtraction(token: string, tenantId: string, documentId: string, body: {
  approved: boolean;
  saveToPatientHistory: boolean;
  reviewNotes?: string | null;
  acceptedStructuredJson?: string | null;
  overrideReason?: string | null;
  editedSummary?: string | null;
}) {
  return httpPost<ClinicalDocument>(`/api/patient-documents/${documentId}/ai-extraction/review`, body, { token, tenantId });
}

export async function reprocessClinicalDocumentExtraction(token: string, tenantId: string, documentId: string) {
  return httpPost<ClinicalDocument>(`/api/patient-documents/${documentId}/ai-extraction/reprocess`, undefined, { token, tenantId });
}

export async function getRecentAiRequests(token: string, tenantId: string) {
  return httpGet<AiRecentRequestRecord[]>("/api/ai/audit/recent", { token, tenantId });
}

export async function getAiClinicalAnalytics(token: string, tenantId: string) {
  return httpGet<AiClinicalAnalytics>("/api/ai/analytics", { token, tenantId });
}

export async function getAiStatus(token: string, tenantId: string) {
  return httpGet<AiStatus>("/api/ai/status", { token, tenantId });
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

export type PlatformTenant = {
  id: string;
  code: string;
  name: string;
  planId?: string | null;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
  modules?: Record<string, boolean> | null;
};

export type PlatformPlan = {
  id: string;
  name: string;
  features?: string | null;
};

export type PlatformTenantDetail = {
  tenant: PlatformTenant;
  clinicProfile?: {
    clinicName: string;
    displayName: string;
    city?: string | null;
    country?: string | null;
    email?: string | null;
    phone?: string | null;
  } | null;
  latestSubscription?: {
    planId: string;
    status: string;
    startDate: string;
    endDate?: string | null;
    trial?: boolean;
  } | null;
  modules: Record<string, boolean>;
  userCount: number;
  adminCount: number;
};

export async function getPlatformTenants(token: string) {
  return httpGet<PlatformTenant[]>("/api/platform/tenants", { token });
}

export async function getPlatformTenant(token: string, tenantId: string) {
  return httpGet<PlatformTenantDetail>(`/api/platform/tenants/${tenantId}`, { token });
}

export async function createPlatformTenant(token: string, body: {
  clinicName: string;
  tenantCode: string;
  displayName?: string | null;
  city: string;
  state?: string | null;
  country: string;
  postalCode?: string | null;
  phone?: string | null;
  clinicEmail?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  planId?: string | null;
  modules?: Record<string, boolean> | null;
  adminEmail?: string | null;
  adminFirstName?: string | null;
  adminLastName?: string | null;
  tempPassword?: string | null;
}) {
  return httpPost<PlatformTenantDetail>("/api/platform/tenants", body, { token });
}

export async function updatePlatformTenantStatus(token: string, tenantId: string, active: boolean) {
  return httpPatch<PlatformTenant>(`/api/platform/tenants/${tenantId}/status`, { active }, { token });
}

export async function updatePlatformTenantPlan(token: string, tenantId: string, planId: string) {
  return httpPut<PlatformTenant>(`/api/platform/tenants/${tenantId}/plan`, { planId }, { token });
}

export async function getPlatformPlans(token: string) {
  return httpGet<PlatformPlan[]>("/api/platform/plans", { token });
}

export async function getPlatformTenantModules(token: string, tenantId: string) {
  return httpGet<Record<string, boolean>>(`/api/platform/tenants/${tenantId}/modules`, { token });
}

export async function updatePlatformTenantModules(token: string, tenantId: string, modules: Record<string, boolean>) {
  return httpPut<PlatformTenant>(`/api/platform/tenants/${tenantId}/modules`, modules, { token });
}

export async function createPlatformTenantAdminUser(token: string, tenantId: string, body: {
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  tempPassword?: string | null;
}) {
  return httpPost(`/api/platform/tenants/${tenantId}/admin-user`, body, { token });
}

export async function getPatientDocuments(token: string, tenantId: string, patientId: string) {
  return httpGet<ClinicalDocument[]>(`/api/patients/${patientId}/documents`, { token, tenantId });
}

export async function uploadPatientDocument(token: string, tenantId: string, patientId: string, body: {
  file: File;
  documentType: ClinicalDocumentType;
  consultationId?: string | null;
  appointmentId?: string | null;
  notes?: string | null;
  referredDoctor?: string | null;
  referredHospital?: string | null;
  referralNotes?: string | null;
}) {
  const formData = new FormData();
  formData.append("file", body.file);
  formData.append("documentType", body.documentType);
  if (body.consultationId) formData.append("consultationId", body.consultationId);
  if (body.appointmentId) formData.append("appointmentId", body.appointmentId);
  if (body.notes) formData.append("notes", body.notes);
  if (body.referredDoctor) formData.append("referredDoctor", body.referredDoctor);
  if (body.referredHospital) formData.append("referredHospital", body.referredHospital);
  if (body.referralNotes) formData.append("referralNotes", body.referralNotes);
  return httpPostForm<ClinicalDocument>(`/api/patients/${patientId}/documents`, formData, { token, tenantId });
}

export async function getPatientDocumentDownloadUrl(token: string, tenantId: string, documentId: string) {
  return httpGet<{ url: string; expiresInSeconds: string }>(`/api/patient-documents/${documentId}/download-url`, { token, tenantId });
}

export async function getPatientTimeline(token: string, tenantId: string, patientId: string) {
  return httpGet<PatientTimelineItem[]>(`/api/patients/${patientId}/timeline`, { token, tenantId });
}

export type PrescriptionTemplateConfig = {
  id: string | null;
  tenantId: string;
  templateVersion: number;
  active: boolean;
  clinicLogoDocumentId: string | null;
  headerText: string | null;
  footerText: string | null;
  primaryColor: string | null;
  accentColor: string | null;
  disclaimer: string | null;
  doctorSignatureText: string | null;
  showQrCode: boolean;
  watermarkText: string | null;
  changedByAppUserId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type PrescriptionTemplateInput = {
  clinicLogoDocumentId?: string | null;
  headerText?: string | null;
  footerText?: string | null;
  primaryColor?: string | null;
  accentColor?: string | null;
  disclaimer?: string | null;
  doctorSignatureText?: string | null;
  showQrCode?: boolean;
  watermarkText?: string | null;
};

export async function getPrescriptionTemplate(token: string, tenantId: string) {
  return httpGet<PrescriptionTemplateConfig>("/api/settings/prescription-template", { token, tenantId });
}

export async function getPrescriptionTemplateHistory(token: string, tenantId: string) {
  return httpGet<PrescriptionTemplateConfig[]>("/api/settings/prescription-template/history", { token, tenantId });
}

export async function updatePrescriptionTemplate(token: string, tenantId: string, body: PrescriptionTemplateInput) {
  return httpPut<PrescriptionTemplateConfig>("/api/settings/prescription-template", body, { token, tenantId });
}

export async function previewPrescriptionTemplate(token: string, tenantId: string, body: PrescriptionTemplateInput) {
  const response = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/settings/prescription-template/preview`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
      "Content-Type": "application/json",
      Accept: "application/pdf",
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`HTTP ${response.status}: ${text || response.statusText}`);
  }
  return { blob: await response.blob() };
}
