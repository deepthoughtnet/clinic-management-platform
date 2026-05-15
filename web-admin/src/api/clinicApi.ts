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
export type InventoryTransactionType =
  | "STOCK_IN"
  | "DISPENSED"
  | "ADJUSTMENT_IN"
  | "ADJUSTMENT_OUT"
  | "RETURN"
  | "EXPIRED"
  | "CANCELLED_DISPENSE"
  | "OPENING"
  | "PURCHASE"
  | "SALE"
  | "ADJUSTMENT";
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

export type ClinicDashboard = {
  startDate: string;
  endDate: string;
  tenantId: string | null;
  appointmentSummary: {
    totalToday: number;
    scheduled: number;
    checkedIn: number;
    inConsultation: number;
    completed: number;
    noShow: number;
    cancelled: number;
  } | null;
  queueSummary: {
    waiting: number;
    inConsultation: number;
    completed: number;
    noShow: number;
    cancelled: number;
    averageWaitTimeMinutes: number;
  } | null;
  consultationSummary: {
    started: number;
    completed: number;
    activeNow: number;
    consultationsWithPrescriptions: number;
  } | null;
  prescriptionSummary: {
    prescriptionsGenerated: number;
    consultationsWithPrescriptions: number;
    avgPrescriptionsPerConsultation: number;
  } | null;
  billingSummary: {
    billsCreated: number;
    paidBills: number;
    pendingBills: number;
    totalBilled: number;
    totalPaid: number;
    pendingAmount: number;
  } | null;
  followUpSummary: {
    dueInRange: number;
    overdue: number;
    upcomingNext7Days: number;
  } | null;
  doctorSummaries: Array<{
    doctorUserId: string;
    doctorName: string;
    appointmentsToday: number;
    checkedIn: number;
    completed: number;
    noShow: number;
    cancelled: number;
    nextAppointmentTime: string | null;
    revenue: number;
    prescriptionsGenerated: number;
    consultationsCompleted: number;
    avgConsultationLoad: number;
  }>;
  currentWaitingList: Array<{
    appointmentId: string;
    patientId: string;
    patientName: string | null;
    patientNumber: string | null;
    doctorUserId: string | null;
    doctorName: string | null;
    tokenNumber: number | null;
    appointmentTime: string | null;
    waitingSince: string | null;
    status: string;
  }>;
  recentUnpaidBills: Array<{
    billId: string;
    billNumber: string;
    patientId: string;
    patientName: string | null;
    dueAmount: number;
    billDate: string;
    status: string | null;
  }>;
  recentActivity: Array<{
    timestamp: string;
    type: string;
    title: string;
    description: string;
    relatedPatientName: string | null;
    relatedDoctorName: string | null;
  }>;
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

export type DoctorAvailabilitySlotStatus = "AVAILABLE" | "PARTIALLY_BOOKED" | "FULL" | "BREAK" | "LEAVE" | "UNAVAILABLE" | "CONFLICTED";

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

export type DoctorUnavailabilityType = "LEAVE" | "HOLIDAY" | "UNAVAILABLE" | "EMERGENCY_BLOCK";

export type DoctorUnavailability = {
  id: string;
  tenantId: string;
  doctorUserId: string;
  startAt: string;
  endAt: string;
  type: DoctorUnavailabilityType;
  reason: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type DoctorUnavailabilityInput = {
  startAt: string;
  endAt: string;
  type: DoctorUnavailabilityType;
  reason: string | null;
  active: boolean;
};

export type WaitlistStatus = "WAITING" | "CONTACTED" | "BOOKED" | "CANCELLED";

export type AppointmentWaitlist = {
  id: string;
  tenantId: string;
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  doctorUserId: string | null;
  doctorName: string | null;
  preferredDate: string;
  preferredStartTime: string | null;
  preferredEndTime: string | null;
  reason: string | null;
  notes: string | null;
  status: WaitlistStatus;
  bookedAppointmentId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AppointmentWaitlistInput = {
  patientId: string;
  doctorUserId: string | null;
  preferredDate: string;
  preferredStartTime: string | null;
  preferredEndTime: string | null;
  reason: string | null;
  notes: string | null;
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

export type BillStatus = "DRAFT" | "UNPAID" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "PARTIALLY_REFUNDED" | "REFUNDED" | "CANCELLED";
export type BillItemType = "CONSULTATION" | "MEDICINE" | "TEST" | "VACCINATION" | "PROCEDURE" | "OTHER";
export type PaymentMode = "CASH" | "CARD" | "UPI" | "PAYTM" | "PHONEPE" | "GOOGLE_PAY" | "BANK_TRANSFER" | "CHEQUE" | "OTHER";
export type DiscountType = "NONE" | "AMOUNT" | "PERCENTAGE";

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
  discountType: DiscountType;
  discountValue: number;
  discountAmount: number;
  discountReason: string | null;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  refundedAmount: number;
  netPaidAmount: number;
  invoiceEmailedAt: string | null;
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
  discountType: DiscountType | null;
  discountValue: number | null;
  discountReason: string | null;
  taxAmount: number | null;
  notes: string | null;
  lines: Array<Omit<BillLine, "id" | "totalPrice"> & { unitPrice: number; quantity: number; referenceId: string | null }>;
};

export type Payment = {
  id: string;
  tenantId: string;
  billId: string;
  paymentDate: string;
  paymentDateTime?: string | null;
  amount: number;
  paymentMode: PaymentMode;
  referenceNumber: string | null;
  notes: string | null;
  receivedBy?: string | null;
  receiptId: string | null;
  receiptNumber: string | null;
  receiptDate: string | null;
  createdAt: string;
};

export type Refund = {
  id: string;
  billId: string;
  paymentId: string | null;
  tenantId: string;
  amount: number;
  reason: string;
  refundMode: PaymentMode | null;
  refundedBy: string | null;
  refundedAt: string;
  notes: string | null;
  createdAt: string;
};

export type PaymentLedgerRow = {
  id: string;
  tenantId: string;
  billId: string;
  billNumber: string;
  patientId: string | null;
  patientName: string | null;
  patientNumber: string | null;
  paymentDate: string;
  paymentDateTime: string | null;
  amount: number;
  paymentMode: PaymentMode;
  referenceNumber: string | null;
  notes: string | null;
  receivedBy: string | null;
  receiptId: string | null;
  receiptNumber: string | null;
  receiptDate: string | null;
  billStatus: BillStatus;
  billDueAmount: number;
  createdAt: string;
};

export type RefundLedgerRow = {
  id: string;
  tenantId: string;
  billId: string;
  billNumber: string;
  patientId: string | null;
  patientName: string | null;
  patientNumber: string | null;
  paymentId: string | null;
  amount: number;
  reason: string;
  refundMode: PaymentMode | null;
  refundedBy: string | null;
  refundedAt: string;
  notes: string | null;
  billStatus: BillStatus;
  billDueAmount: number;
  createdAt: string;
};

export type RefundInput = {
  paymentId: string | null;
  amount: number;
  reason: string;
  refundMode: PaymentMode | null;
  refundedAt: string | null;
  notes: string | null;
};

export type InvoiceEmailSendResponse = {
  sent: boolean;
  message: string;
  recipientEmail: string | null;
  sentAt: string | null;
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
  genericName: string | null;
  brandName: string | null;
  category: string | null;
  dosageForm: string | null;
  strength: string | null;
  unit: string | null;
  manufacturer: string | null;
  defaultDosage: string | null;
  defaultFrequency: string | null;
  defaultDurationDays: number | null;
  defaultTiming: Timing | null;
  defaultInstructions: string | null;
  defaultPrice: number | null;
  taxRate: number | null;
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
  purchaseDate: string | null;
  supplierName: string | null;
  quantityReceived: number | null;
  quantityOnHand: number;
  lowStockThreshold: number | null;
  unitCost: number | null;
  purchasePrice: number | null;
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
  reason: string | null;
  referenceType: string | null;
  referenceId: string | null;
  createdBy: string | null;
  notes: string | null;
  createdAt: string;
};

export type InventoryTransactionInput = {
  medicineId: string;
  stockBatchId: string | null;
  transactionType: InventoryTransactionType;
  quantity: number;
  reason: string | null;
  referenceType: string | null;
  referenceId: string | null;
  createdBy?: string | null;
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

export async function rescheduleAppointment(
  token: string,
  tenantId: string,
  id: string,
  body: { doctorUserId: string | null; appointmentDate: string; appointmentTime: string; reason: string | null },
) {
  return httpPatch<Appointment>(`/api/appointments/${id}/reschedule`, body, { token, tenantId });
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

export async function getDoctorUnavailability(token: string, tenantId: string, doctorUserId: string) {
  return httpGet<DoctorUnavailability[]>(`/api/doctors/${doctorUserId}/unavailability`, { token, tenantId });
}

export async function createDoctorUnavailability(token: string, tenantId: string, doctorUserId: string, body: DoctorUnavailabilityInput) {
  return httpPost<DoctorUnavailability>(`/api/doctors/${doctorUserId}/unavailability`, body, { token, tenantId });
}

export async function deactivateDoctorUnavailability(token: string, tenantId: string, id: string) {
  return httpPatch<DoctorUnavailability>(`/api/doctors/unavailability/${id}/deactivate`, undefined, { token, tenantId });
}

export async function getWaitlist(
  token: string,
  tenantId: string,
  params: { doctorUserId?: string; preferredDate?: string; status?: WaitlistStatus } = {},
) {
  const query = new URLSearchParams();
  if (params.doctorUserId) query.set("doctorUserId", params.doctorUserId);
  if (params.preferredDate) query.set("preferredDate", params.preferredDate);
  if (params.status) query.set("status", params.status);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<AppointmentWaitlist[]>(`/api/appointments/waitlist${suffix}`, { token, tenantId });
}

export async function createWaitlist(token: string, tenantId: string, body: AppointmentWaitlistInput) {
  return httpPost<AppointmentWaitlist>("/api/appointments/waitlist", body, { token, tenantId });
}

export async function updateWaitlistStatus(token: string, tenantId: string, id: string, status: WaitlistStatus) {
  return httpPatch<AppointmentWaitlist>(`/api/appointments/waitlist/${id}/status`, { status }, { token, tenantId });
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
  params: { patientId?: string; status?: BillStatus | null; fromDate?: string; toDate?: string; paymentMode?: PaymentMode | null } = {},
) {
  const query = new URLSearchParams();
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.status) query.set("status", params.status);
  if (params.fromDate) query.set("fromDate", params.fromDate);
  if (params.toDate) query.set("toDate", params.toDate);
  if (params.paymentMode) query.set("paymentMode", params.paymentMode);
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

export async function listBillRefunds(token: string, tenantId: string, billId: string) {
  return httpGet<Refund[]>(`/api/bills/${billId}/refunds`, { token, tenantId });
}

export async function addBillRefund(token: string, tenantId: string, billId: string, body: RefundInput) {
  return httpPost<Refund>(`/api/bills/${billId}/refunds`, body, { token, tenantId });
}

export async function listPaymentsLedger(
  token: string,
  tenantId: string,
  params: {
    fromDate?: string;
    toDate?: string;
    patientId?: string;
    search?: string;
    billNumber?: string;
    paymentMode?: PaymentMode | null;
    receivedBy?: string;
    page?: number;
    size?: number;
  } = {},
) {
  const query = new URLSearchParams();
  if (params.fromDate) query.set("fromDate", params.fromDate);
  if (params.toDate) query.set("toDate", params.toDate);
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.search) query.set("search", params.search);
  if (params.billNumber) query.set("billNumber", params.billNumber);
  if (params.paymentMode) query.set("paymentMode", params.paymentMode);
  if (params.receivedBy) query.set("receivedBy", params.receivedBy);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 200));
  return httpGet<PaymentLedgerRow[]>(`/api/bills/payments?${query.toString()}`, { token, tenantId });
}

export async function listRefundsLedger(
  token: string,
  tenantId: string,
  params: {
    fromDate?: string;
    toDate?: string;
    patientId?: string;
    search?: string;
    billNumber?: string;
    refundMode?: PaymentMode | null;
    page?: number;
    size?: number;
  } = {},
) {
  const query = new URLSearchParams();
  if (params.fromDate) query.set("fromDate", params.fromDate);
  if (params.toDate) query.set("toDate", params.toDate);
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.search) query.set("search", params.search);
  if (params.billNumber) query.set("billNumber", params.billNumber);
  if (params.refundMode) query.set("refundMode", params.refundMode);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 200));
  return httpGet<RefundLedgerRow[]>(`/api/bills/refunds?${query.toString()}`, { token, tenantId });
}

export async function sendBillInvoiceEmail(token: string, tenantId: string, billId: string) {
  return httpPost<InvoiceEmailSendResponse>(`/api/bills/${billId}/send-invoice-email`, undefined, { token, tenantId });
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

export async function getClinicDashboard(
  token: string,
  tenantId: string,
  params: { date?: string; startDate?: string; endDate?: string; doctorUserId?: string } = {},
) {
  const query = new URLSearchParams();
  if (params.date) query.set("date", params.date);
  if (params.startDate) query.set("startDate", params.startDate);
  if (params.endDate) query.set("endDate", params.endDate);
  if (params.doctorUserId) query.set("doctorUserId", params.doctorUserId);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<ClinicDashboard>(`/api/reports/clinic-dashboard${suffix}`, { token, tenantId });
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

export async function activateMedicine(token: string, tenantId: string, id: string) {
  return httpPatch<Medicine>(`/api/medicines/${id}/activate`, undefined, { token, tenantId });
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

export async function getExpiredStock(token: string, tenantId: string) {
  return httpGet<Stock[]>("/api/inventory/alerts/expired", { token, tenantId });
}

export async function getExpiringStock(token: string, tenantId: string, days = 30) {
  return httpGet<Stock[]>(`/api/inventory/alerts/expiring?days=${days}`, { token, tenantId });
}

export type DispenseLine = {
  itemId: string | null;
  prescribedMedicineName: string;
  medicineId: string | null;
  prescribedQuantity: number;
  dispensedQuantity: number;
  status: "NOT_DISPENSED" | "PARTIALLY_DISPENSED" | "DISPENSED" | string;
  availableQuantity: number | null;
  lastBatchId: string | null;
};

export type PrescriptionDispense = {
  prescriptionId: string;
  prescriptionNumber: string;
  patientId: string;
  patientName: string | null;
  billingStatus: "NOT_BILLED" | "BILLED" | "PAID" | string;
  billedBillId: string | null;
  lines: DispenseLine[];
};

export type DispenseInput = {
  prescribedMedicineName: string;
  medicineId: string | null;
  quantity: number;
  batchId: string | null;
  allowBatchOverride: boolean;
};

export async function getDispensingQueue(token: string, tenantId: string) {
  return httpGet<PrescriptionDispense[]>("/api/inventory/dispensing/queue", { token, tenantId });
}

export async function getPrescriptionDispense(token: string, tenantId: string, prescriptionId: string) {
  return httpGet<PrescriptionDispense>(`/api/inventory/dispensing/${prescriptionId}`, { token, tenantId });
}

export async function dispensePrescriptionMedicine(token: string, tenantId: string, prescriptionId: string, body: DispenseInput) {
  return httpPost<PrescriptionDispense>(`/api/inventory/dispensing/${prescriptionId}/dispense`, body, { token, tenantId });
}

export async function generateMedicineBillFromDispense(token: string, tenantId: string, prescriptionId: string) {
  return httpPost<Bill>(`/api/inventory/dispensing/${prescriptionId}/bill`, {}, { token, tenantId });
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

export type CarePilotCampaignType =
  | "APPOINTMENT_REMINDER"
  | "MISSED_APPOINTMENT_FOLLOW_UP"
  | "FOLLOW_UP_REMINDER"
  | "LEAD_FOLLOW_UP_REMINDER"
  | "WEBINAR_CONFIRMATION"
  | "WEBINAR_REMINDER"
  | "WEBINAR_FOLLOW_UP"
  | "REFILL_REMINDER"
  | "VACCINATION_REMINDER"
  | "BILLING_REMINDER"
  | "WELLNESS_MESSAGE"
  | "CUSTOM";
export type CarePilotCampaignStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "ARCHIVED";
export type CarePilotTriggerType = "MANUAL" | "SCHEDULED" | "EVENT_BASED";
export type CarePilotAudienceType =
  | "ALL_PATIENTS"
  | "SPECIFIC_PATIENTS"
  | "TAG_BASED"
  | "RULE_BASED"
  | "HIGH_RISK_PATIENTS"
  | "INACTIVE_PATIENTS"
  | "REFILL_RISK_PATIENTS"
  | "FOLLOW_UP_OVERDUE_PATIENTS";
export type CarePilotChannelType = "EMAIL" | "SMS" | "WHATSAPP" | "IN_APP" | "APP_NOTIFICATION";
export type CarePilotExecutionStatus = "QUEUED" | "PROCESSING" | "SUCCEEDED" | "FAILED" | "DEAD_LETTER" | "RETRY_SCHEDULED" | "CANCELLED" | "SUPPRESSED";
export type CarePilotDeliveryStatus =
  | "QUEUED"
  | "SENT"
  | "DELIVERED"
  | "READ"
  | "FAILED"
  | "BOUNCED"
  | "UNDELIVERED"
  | "SKIPPED"
  | "PROVIDER_NOT_AVAILABLE"
  | "NOT_CONFIGURED"
  | "UNKNOWN";
export type CarePilotProviderReadinessStatus = "READY" | "DISABLED" | "NOT_CONFIGURED" | "ERROR";

export type CarePilotCampaign = {
  id: string;
  tenantId: string;
  name: string;
  campaignType: CarePilotCampaignType;
  status: CarePilotCampaignStatus;
  triggerType: CarePilotTriggerType;
  audienceType: CarePilotAudienceType;
  templateId: string | null;
  active: boolean;
  notes: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CarePilotTemplate = {
  id: string;
  tenantId: string;
  name: string;
  channelType: CarePilotChannelType;
  subjectLine: string | null;
  bodyTemplate: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CarePilotExecution = {
  id: string;
  tenantId: string;
  campaignId: string;
  templateId: string | null;
  channelType: CarePilotChannelType;
  recipientPatientId: string | null;
  scheduledAt: string;
  status: CarePilotExecutionStatus;
  attemptCount: number;
  lastError: string | null;
  executedAt: string | null;
  nextAttemptAt: string | null;
  deliveryStatus: CarePilotDeliveryStatus | null;
  providerName: string | null;
  providerMessageId: string | null;
  sourceType: string | null;
  sourceReferenceId: string | null;
  reminderWindow: string | null;
  referenceDateTime: string | null;
  lastAttemptAt: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CarePilotDeliveryAttempt = {
  id: string;
  tenantId: string;
  executionId: string;
  attemptNumber: number;
  providerName: string | null;
  channelType: CarePilotChannelType;
  deliveryStatus: CarePilotDeliveryStatus;
  errorCode: string | null;
  errorMessage: string | null;
  attemptedAt: string;
};

export type CarePilotCampaignExecutionBreakdown = {
  campaignId: string;
  campaignName: string;
  totalExecutions: number;
  successfulExecutions: number;
  failedExecutions: number;
  successRate: number;
};

export type CarePilotProviderFailureSummary = {
  providerName: string;
  failureCount: number;
};

export type CarePilotAnalyticsSummary = {
  startDate: string;
  endDate: string;
  totalCampaigns: number;
  activeCampaigns: number;
  totalExecutions: number;
  pendingExecutions: number;
  scheduledExecutions: number;
  successfulExecutions: number;
  failedExecutions: number;
  retryingExecutions: number;
  skippedExecutions: number;
  deliveredExecutions: number;
  readExecutions: number;
  bouncedExecutions: number;
  undeliveredExecutions: number;
  successRate: number;
  failureRate: number;
  retryRate: number;
  executionsByStatus: Record<string, number>;
  executionsByChannel: Record<string, number>;
  executionsByCampaign: CarePilotCampaignExecutionBreakdown[];
  providerFailureSummary: CarePilotProviderFailureSummary[];
  recentFailures: CarePilotExecution[];
  recentSuccesses: CarePilotExecution[];
};

export type CarePilotTimelineEvent = {
  type: string;
  status: string;
  detail: string | null;
  at: string | null;
};

export type CarePilotExecutionTimeline = {
  execution: CarePilotExecution;
  deliveryAttempts: CarePilotDeliveryAttempt[];
  deliveryEvents: CarePilotDeliveryEvent[];
  statusEvents: CarePilotTimelineEvent[];
};

export type CarePilotDeliveryEvent = {
  id: string | null;
  executionId: string | null;
  providerName: string | null;
  providerMessageId: string | null;
  channelType: CarePilotChannelType;
  externalStatus: string | null;
  internalStatus: CarePilotDeliveryStatus;
  eventType: string;
  eventTimestamp: string | null;
  receivedAt: string | null;
};

export type CarePilotCampaignRuntimeSummary = {
  totalExecutions: number;
  scheduled: number;
  sent: number;
  failed: number;
  retrying: number;
  skipped: number;
  lastSentAt: string | null;
  lastFailedAt: string | null;
};

export type CarePilotCampaignRuntimeExecution = {
  executionId: string;
  recipientPatientId: string | null;
  recipientPatientName: string | null;
  recipientEmail: string | null;
  recipientPhone: string | null;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  relatedEntityLabel: string | null;
  doctorName: string | null;
  reminderWindow: string | null;
  createdAt: string;
  scheduledAt: string | null;
  attemptedAt: string | null;
  sentAt: string | null;
  failedAt: string | null;
  nextRetryAt: string | null;
  channel: string | null;
  providerName: string | null;
  providerMessageId: string | null;
  status: string | null;
  failureReason: string | null;
  retryCount: number;
};

export type CarePilotCampaignRuntime = {
  campaignId: string;
  campaignName: string;
  active: boolean;
  triggerType: CarePilotTriggerType;
  campaignType: CarePilotCampaignType;
  nextExpectedExecutionAt: string | null;
  schedulerStatus: string;
  lastSchedulerScanAt: string | null;
  summary: CarePilotCampaignRuntimeSummary;
  recentExecutions: CarePilotCampaignRuntimeExecution[];
};

export type CarePilotMessagingProviderStatus = {
  channel: "EMAIL" | "SMS" | "WHATSAPP" | "IN_APP";
  providerName: string;
  enabled: boolean;
  configured: boolean;
  available: boolean;
  status: CarePilotProviderReadinessStatus;
  missingConfigurationKeys: string[];
  message: string;
  supportsTestSend: boolean;
  lastCheckedAt: string;
  providerConfigured: boolean;
  fromAddressConfigured: boolean;
  fromNumberConfigured: boolean;
  smtpHostConfigured: boolean;
};

export type CarePilotProviderTestSendInput = {
  recipient: string;
  subject?: string | null;
  body: string;
};

export type CarePilotProviderTestSendResult = {
  channel: "EMAIL" | "SMS" | "WHATSAPP" | "IN_APP";
  success: boolean;
  status: CarePilotDeliveryStatus;
  providerName: string;
  providerMessageId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  sentAt: string | null;
};

export type CarePilotReminderRow = {
  executionId: string;
  campaignId: string;
  campaignName: string;
  campaignType: CarePilotCampaignType | null;
  triggerType: CarePilotTriggerType | null;
  patientId: string | null;
  patientName: string | null;
  patientEmail: string | null;
  patientPhone: string | null;
  channel: CarePilotChannelType;
  providerName: string | null;
  providerMessageId: string | null;
  executionStatus: CarePilotExecutionStatus;
  deliveryStatus: CarePilotDeliveryStatus | null;
  scheduledAt: string | null;
  attemptedAt: string | null;
  sentAt: string | null;
  deliveredAt: string | null;
  readAt: string | null;
  failedAt: string | null;
  nextRetryAt: string | null;
  retryCount: number;
  failureReason: string | null;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  relatedEntityLabel: string | null;
  reminderReason: string | null;
  createdAt: string;
};

export type CarePilotReminderListResponse = {
  page: number;
  size: number;
  total: number;
  rows: CarePilotReminderRow[];
};

export type CarePilotReminderDetail = {
  reminder: CarePilotReminderRow;
  timeline: CarePilotExecutionTimeline;
};

export type CarePilotEngagementLevel = "HIGH" | "MEDIUM" | "LOW" | "CRITICAL";
export type CarePilotRiskLevel = "LOW" | "MEDIUM" | "HIGH";
export type CarePilotEngagementCohort =
  | "HIGH_RISK_PATIENTS"
  | "INACTIVE_PATIENTS"
  | "HIGH_NO_SHOW_RISK"
  | "OVERDUE_BILL_PATIENTS"
  | "REFILL_RISK_PATIENTS"
  | "VACCINATION_OVERDUE_PATIENTS"
  | "HIGH_ENGAGEMENT_PATIENTS"
  | "LOW_ENGAGEMENT_PATIENTS"
  | "FOLLOW_UP_OVERDUE_PATIENTS";

export type CarePilotEngagementProfile = {
  patientId: string;
  tenantId: string;
  patientNumber: string;
  patientName: string;
  patientEmail: string | null;
  patientMobile: string | null;
  engagementScore: number;
  engagementLevel: CarePilotEngagementLevel;
  inactiveRisk: CarePilotRiskLevel;
  noShowRisk: CarePilotRiskLevel;
  refillRisk: CarePilotRiskLevel;
  followUpRisk: CarePilotRiskLevel;
  overdueBalanceRisk: CarePilotRiskLevel;
  vaccinationCompliance: CarePilotRiskLevel;
  lastAppointmentAt: string | null;
  lastConsultationAt: string | null;
  lastCampaignEngagementAt: string | null;
  missedAppointmentsCount: number;
  completedAppointmentsCount: number;
  overdueBillsCount: number;
  overdueVaccinationsCount: number;
  pendingRefillCount: number;
  followUpMissedCount: number;
  inactive: boolean;
  riskReasons: string[];
  suggestedCampaignType: string;
  generatedAt: string;
};

export type CarePilotEngagementOverview = {
  totalActivePatients: number;
  highEngagementCount: number;
  mediumEngagementCount: number;
  lowEngagementCount: number;
  criticalEngagementCount: number;
  inactivePatientsCount: number;
  highRiskPatientsCount: number;
  refillRiskCount: number;
  followUpRiskCount: number;
  overdueVaccinationCount: number;
  overdueBillsRiskCount: number;
  engagementDistribution: Record<string, number>;
  cohortCounts: Record<string, number>;
  generatedAt: string;
};

export type CarePilotEngagementCohortResponse = {
  cohort: CarePilotEngagementCohort;
  offset: number;
  limit: number;
  count: number;
  rows: CarePilotEngagementProfile[];
  generatedAt: string;
};

export type CarePilotLeadStatus =
  | "NEW"
  | "CONTACTED"
  | "QUALIFIED"
  | "FOLLOW_UP_REQUIRED"
  | "APPOINTMENT_BOOKED"
  | "CONVERTED"
  | "LOST"
  | "SPAM";
export type CarePilotLeadPriority = "LOW" | "MEDIUM" | "HIGH";
export type CarePilotLeadSource = "WEBSITE" | "WALK_IN" | "PHONE_CALL" | "WHATSAPP" | "FACEBOOK" | "GOOGLE_ADS" | "REFERRAL" | "CAMPAIGN" | "MANUAL" | "OTHER";

export type CarePilotLead = {
  id: string;
  tenantId: string;
  firstName: string;
  lastName: string | null;
  fullName: string | null;
  phone: string;
  email: string | null;
  gender: PatientGender | null;
  dateOfBirth: string | null;
  source: CarePilotLeadSource;
  sourceDetails: string | null;
  campaignId: string | null;
  assignedToAppUserId: string | null;
  status: CarePilotLeadStatus;
  priority: CarePilotLeadPriority;
  notes: string | null;
  tags: string | null;
  convertedPatientId: string | null;
  bookedAppointmentId: string | null;
  lastContactedAt: string | null;
  nextFollowUpAt: string | null;
  lastActivityAt: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CarePilotLeadListResponse = {
  page: number;
  size: number;
  total: number;
  rows: CarePilotLead[];
};

export type CarePilotLeadAnalyticsSummary = {
  totalLeads: number;
  newLeads: number;
  qualifiedLeads: number;
  convertedLeads: number;
  lostLeads: number;
  followUpsDue: number;
  followUpsDueToday: number;
  overdueFollowUps: number;
  conversionRate: number;
  sourceBreakdown: Record<string, number>;
  staleLeads: number;
  highPriorityActiveLeads: number;
  conversionsWithAppointment: number;
  avgHoursToConversion: number | null;
};

export type CarePilotLeadActivityType =
  | "CREATED"
  | "UPDATED"
  | "STATUS_CHANGED"
  | "NOTE_ADDED"
  | "FOLLOW_UP_SCHEDULED"
  | "FOLLOW_UP_COMPLETED"
  | "CONVERTED_TO_PATIENT"
  | "APPOINTMENT_BOOKED"
  | "CAMPAIGN_LINKED"
  | "LOST"
  | "SPAM_MARKED";

export type CarePilotLeadActivity = {
  id: string;
  tenantId: string;
  leadId: string;
  activityType: CarePilotLeadActivityType;
  title: string;
  description: string | null;
  oldStatus: CarePilotLeadStatus | null;
  newStatus: CarePilotLeadStatus | null;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  createdByAppUserId: string | null;
  createdAt: string;
};

export type CarePilotLeadActivityListResponse = {
  page: number;
  size: number;
  total: number;
  rows: CarePilotLeadActivity[];
};

export type CarePilotWebinarStatus = "DRAFT" | "SCHEDULED" | "LIVE" | "COMPLETED" | "CANCELLED";
export type CarePilotWebinarType = "HEALTH_AWARENESS" | "WELLNESS" | "CLINIC_EVENT" | "MARKETING" | "EDUCATIONAL" | "OTHER";
export type CarePilotWebinarRegistrationStatus = "REGISTERED" | "CONFIRMED" | "CANCELLED" | "NO_SHOW" | "ATTENDED";
export type CarePilotWebinarRegistrationSource = "MANUAL" | "PATIENT" | "LEAD" | "CAMPAIGN" | "OTHER";

export type CarePilotWebinar = {
  id: string;
  tenantId: string;
  title: string;
  description: string | null;
  webinarType: CarePilotWebinarType;
  status: CarePilotWebinarStatus;
  webinarUrl: string | null;
  organizerName: string | null;
  organizerEmail: string | null;
  scheduledStartAt: string;
  scheduledEndAt: string;
  timezone: string;
  capacity: number | null;
  registrationEnabled: boolean;
  reminderEnabled: boolean;
  followupEnabled: boolean;
  tags: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CarePilotWebinarListResponse = {
  page: number;
  size: number;
  total: number;
  rows: CarePilotWebinar[];
};

export type CarePilotWebinarRegistration = {
  id: string;
  tenantId: string;
  webinarId: string;
  patientId: string | null;
  leadId: string | null;
  attendeeName: string;
  attendeeEmail: string | null;
  attendeePhone: string | null;
  registrationStatus: CarePilotWebinarRegistrationStatus;
  attended: boolean;
  attendedAt: string | null;
  source: CarePilotWebinarRegistrationSource;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CarePilotWebinarRegistrationListResponse = {
  page: number;
  size: number;
  total: number;
  rows: CarePilotWebinarRegistration[];
};

export type CarePilotWebinarAnalyticsSummary = {
  totalWebinars: number;
  upcomingWebinars: number;
  completedWebinars: number;
  totalRegistrations: number;
  attendedCount: number;
  noShowCount: number;
  attendanceRate: number;
  noShowRate: number;
  webinarConversions: number;
  registrationsBySource: Record<string, number>;
  attendeeEngagementCount: number;
};

export type CarePilotAiCallCampaignStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "COMPLETED" | "CANCELLED";
export type CarePilotAiCallType =
  | "APPOINTMENT_REMINDER"
  | "MISSED_APPOINTMENT"
  | "REFILL_REMINDER"
  | "BILLING_REMINDER"
  | "WELLNESS_OUTREACH"
  | "LEAD_FOLLOW_UP"
  | "WEBINAR_REMINDER"
  | "MANUAL_OUTREACH";
export type CarePilotAiCallExecutionStatus =
  | "PENDING"
  | "QUEUED"
  | "DIALING"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "FAILED"
  | "NO_ANSWER"
  | "BUSY"
  | "CANCELLED"
  | "ESCALATED"
  | "SKIPPED"
  | "SUPPRESSED";
export type CarePilotAiCallEventType =
  | "QUEUED"
  | "DISPATCHED"
  | "PROVIDER_ACCEPTED"
  | "RINGING"
  | "ANSWERED"
  | "COMPLETED"
  | "FAILED"
  | "NO_ANSWER"
  | "BUSY"
  | "CANCELLED"
  | "ESCALATED"
  | "SKIPPED"
  | "SUPPRESSED"
  | "TRANSCRIPT_RECEIVED"
  | "RETRY_SCHEDULED"
  | "FAILOVER_ATTEMPTED";
export type CarePilotAiCallCampaign = {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  callType: CarePilotAiCallType;
  status: CarePilotAiCallCampaignStatus;
  templateId: string | null;
  channel: CarePilotChannelType;
  retryEnabled: boolean;
  maxAttempts: number;
  escalationEnabled: boolean;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
};
export type CarePilotAiCallExecution = {
  id: string;
  tenantId: string;
  campaignId: string;
  patientId: string | null;
  leadId: string | null;
  phoneNumber: string;
  executionStatus: CarePilotAiCallExecutionStatus;
  providerName: string | null;
  providerCallId: string | null;
  scheduledAt: string;
  startedAt: string | null;
  endedAt: string | null;
  retryCount: number;
  nextRetryAt: string | null;
  lastAttemptAt: string | null;
  failureReason: string | null;
  suppressionReason: string | null;
  escalationRequired: boolean;
  escalationReason: string | null;
  failoverAttempted: boolean;
  failoverReason: string | null;
  transcriptId: string | null;
  createdAt: string;
  updatedAt: string;
  durationSeconds: number;
  transcriptSummary: string | null;
};
export type CarePilotAiCallExecutionListResponse = {
  page: number;
  size: number;
  total: number;
  rows: CarePilotAiCallExecution[];
};
export type CarePilotAiCallTranscript = {
  id: string;
  executionId: string;
  transcriptText: string | null;
  summary: string | null;
  sentiment: string | null;
  outcome: string | null;
  intent: string | null;
  requiresFollowUp: boolean;
  escalationReason: string | null;
  extractedEntitiesJson: string | null;
  createdAt: string;
};
export type CarePilotAiCallEvent = {
  id: string;
  executionId: string | null;
  providerName: string;
  providerCallId: string | null;
  eventType: CarePilotAiCallEventType;
  externalStatus: string | null;
  internalStatus: CarePilotAiCallExecutionStatus | null;
  eventTimestamp: string;
  rawPayloadRedacted: string | null;
  createdAt: string;
};
export type CarePilotAiCallSchedulerHealth = {
  enabled: boolean;
  lastRunAt: string | null;
  nextEstimatedRunAt: string | null;
  lastProcessedCount: number;
  lastDispatchedCount: number;
  lastFailedCount: number;
  lastSkippedCount: number;
  lastDurationMs: number;
};
export type CarePilotAiCallAnalyticsSummary = {
  totalCalls: number;
  completedCalls: number;
  failedCalls: number;
  escalations: number;
  noAnswerRate: number;
  averageDurationSeconds: number;
  retryRate: number;
  queuedCalls: number;
  suppressedCalls: number;
  skippedCalls: number;
};

export type CreateCarePilotCampaignInput = {
  name: string;
  campaignType: CarePilotCampaignType;
  triggerType: CarePilotTriggerType;
  audienceType: CarePilotAudienceType;
  templateId?: string | null;
  notes?: string | null;
};

export type CreateCarePilotTemplateInput = {
  name: string;
  channelType: CarePilotChannelType;
  subjectLine?: string | null;
  bodyTemplate: string;
  active?: boolean | null;
};

export type PatchCarePilotTemplateInput = {
  name?: string | null;
  subjectLine?: string | null;
  bodyTemplate?: string | null;
  active?: boolean | null;
};

export type AdminTemplateType = "CAMPAIGN" | "REMINDER" | "WEBINAR" | "BILLING" | "LEAD" | "NOTIFICATION" | "AI_PROMPT" | "GENERAL";
export type AdminTemplateChannel = "EMAIL" | "SMS" | "WHATSAPP" | "INTERNAL" | "VOICE";
export type AdminTemplateCategory = "APPOINTMENT_REMINDER" | "REFILL_REMINDER" | "BILLING" | "WEBINAR" | "FOLLOW_UP" | "LEAD" | "VACCINATION" | "WELLNESS" | "GENERAL";

export type AdminTemplate = {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  templateType: AdminTemplateType;
  channel: AdminTemplateChannel;
  category: AdminTemplateCategory;
  subject: string | null;
  body: string;
  variablesJson: string | null;
  active: boolean;
  systemTemplate: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
};

export type AdminTemplateUpsertInput = {
  name: string;
  description: string | null;
  templateType: AdminTemplateType;
  channel: AdminTemplateChannel;
  category: AdminTemplateCategory;
  subject: string | null;
  body: string;
  variablesJson: string | null;
  active: boolean;
};

export type AdminNotificationChannel = "EMAIL" | "SMS" | "WHATSAPP" | "IN_APP";

export type AdminNotificationSettings = {
  id: string;
  tenantId: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
  whatsappEnabled: boolean;
  inAppEnabled: boolean;
  appointmentRemindersEnabled: boolean;
  appointmentReminder24hEnabled: boolean;
  appointmentReminder2hEnabled: boolean;
  followUpRemindersEnabled: boolean;
  billingRemindersEnabled: boolean;
  refillRemindersEnabled: boolean;
  vaccinationRemindersEnabled: boolean;
  leadFollowUpRemindersEnabled: boolean;
  webinarRemindersEnabled: boolean;
  birthdayWellnessEnabled: boolean;
  quietHoursEnabled: boolean;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  timezone: string | null;
  defaultChannel: AdminNotificationChannel;
  fallbackChannel: AdminNotificationChannel | null;
  allowMarketingMessages: boolean;
  requirePatientConsent: boolean;
  unsubscribeFooterEnabled: boolean;
  maxMessagesPerPatientPerDay: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
  emailReady: boolean;
  smsReady: boolean;
  whatsappReady: boolean;
  warnings: string[];
};

export type AdminNotificationSettingsUpdateInput = {
  emailEnabled: boolean;
  smsEnabled: boolean;
  whatsappEnabled: boolean;
  inAppEnabled: boolean;
  appointmentRemindersEnabled: boolean;
  appointmentReminder24hEnabled: boolean;
  appointmentReminder2hEnabled: boolean;
  followUpRemindersEnabled: boolean;
  billingRemindersEnabled: boolean;
  refillRemindersEnabled: boolean;
  vaccinationRemindersEnabled: boolean;
  leadFollowUpRemindersEnabled: boolean;
  webinarRemindersEnabled: boolean;
  birthdayWellnessEnabled: boolean;
  quietHoursEnabled: boolean;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  timezone: string | null;
  defaultChannel: AdminNotificationChannel;
  fallbackChannel: AdminNotificationChannel | null;
  allowMarketingMessages: boolean;
  requirePatientConsent: boolean;
  unsubscribeFooterEnabled: boolean;
  maxMessagesPerPatientPerDay: number;
};

export type AdminIntegrationStatus = "READY" | "DISABLED" | "NOT_CONFIGURED" | "ERROR" | "FUTURE";

export type AdminIntegrationStatusRow = {
  key: string;
  name: string;
  category: string;
  status: AdminIntegrationStatus;
  enabled: boolean;
  configured: boolean;
  providerName: string | null;
  missingConfigurationKeys: string[];
  safeConfigurationHints: string[];
  message: string;
  lastCheckedAt: string;
  supportsTestAction: boolean;
};

export type AdminIntegrationStatusResponse = {
  rows: AdminIntegrationStatusRow[];
};

export async function listCarePilotCampaigns(token: string, tenantId: string) {
  return httpGet<CarePilotCampaign[]>("/api/carepilot/campaigns", { token, tenantId });
}

export async function getCarePilotCampaign(token: string, tenantId: string, campaignId: string) {
  return httpGet<CarePilotCampaign>(`/api/carepilot/campaigns/${campaignId}`, { token, tenantId });
}

export async function getCarePilotCampaignRuntime(token: string, tenantId: string, campaignId: string) {
  return httpGet<CarePilotCampaignRuntime>(`/api/carepilot/campaigns/${campaignId}/runtime`, { token, tenantId });
}

export async function createCarePilotCampaign(token: string, tenantId: string, body: CreateCarePilotCampaignInput) {
  return httpPost<CarePilotCampaign>("/api/carepilot/campaigns", body, { token, tenantId });
}

export async function activateCarePilotCampaign(token: string, tenantId: string, campaignId: string) {
  return httpPatch<CarePilotCampaign>(`/api/carepilot/campaigns/${campaignId}/activate`, undefined, { token, tenantId });
}

export async function deactivateCarePilotCampaign(token: string, tenantId: string, campaignId: string) {
  return httpPatch<CarePilotCampaign>(`/api/carepilot/campaigns/${campaignId}/deactivate`, undefined, { token, tenantId });
}

export async function listCarePilotTemplates(token: string, tenantId: string) {
  return httpGet<CarePilotTemplate[]>("/api/carepilot/templates", { token, tenantId });
}

export async function createCarePilotTemplate(token: string, tenantId: string, body: CreateCarePilotTemplateInput) {
  return httpPost<CarePilotTemplate>("/api/carepilot/templates", body, { token, tenantId });
}

export async function patchCarePilotTemplate(token: string, tenantId: string, templateId: string, body: PatchCarePilotTemplateInput) {
  return httpPatch<CarePilotTemplate>(`/api/carepilot/templates/${templateId}`, body, { token, tenantId });
}

export async function listAdminTemplates(
  token: string,
  tenantId: string,
  filters: { templateType?: AdminTemplateType; channel?: AdminTemplateChannel; category?: AdminTemplateCategory; active?: boolean; search?: string } = {},
) {
  const query = new URLSearchParams();
  if (filters.templateType) query.set("templateType", filters.templateType);
  if (filters.channel) query.set("channel", filters.channel);
  if (filters.category) query.set("category", filters.category);
  if (filters.active !== undefined) query.set("active", String(filters.active));
  if (filters.search) query.set("search", filters.search);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<AdminTemplate[]>(`/api/admin/templates${suffix}`, { token, tenantId });
}

export async function getAdminTemplate(token: string, tenantId: string, templateId: string) {
  return httpGet<AdminTemplate>(`/api/admin/templates/${templateId}`, { token, tenantId });
}

export async function createAdminTemplate(token: string, tenantId: string, body: AdminTemplateUpsertInput) {
  return httpPost<AdminTemplate>("/api/admin/templates", body, { token, tenantId });
}

export async function updateAdminTemplate(token: string, tenantId: string, templateId: string, body: AdminTemplateUpsertInput) {
  return httpPut<AdminTemplate>(`/api/admin/templates/${templateId}`, body, { token, tenantId });
}

export async function activateAdminTemplate(token: string, tenantId: string, templateId: string) {
  return httpPost<AdminTemplate>(`/api/admin/templates/${templateId}/activate`, undefined, { token, tenantId });
}

export async function deactivateAdminTemplate(token: string, tenantId: string, templateId: string) {
  return httpPost<AdminTemplate>(`/api/admin/templates/${templateId}/deactivate`, undefined, { token, tenantId });
}

export async function deleteAdminTemplate(token: string, tenantId: string, templateId: string) {
  await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/admin/templates/${templateId}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
    },
  }).then(async (res) => {
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  });
}

export async function previewAdminTemplate(token: string, tenantId: string, templateId: string, variables: Record<string, string>) {
  return httpPost<{ renderedSubject: string; renderedBody: string }>(`/api/admin/templates/${templateId}/preview`, { variables }, { token, tenantId });
}

export async function getAdminNotificationSettings(token: string, tenantId: string) {
  return httpGet<AdminNotificationSettings>("/api/admin/notification-settings", { token, tenantId });
}

export async function updateAdminNotificationSettings(
  token: string,
  tenantId: string,
  body: AdminNotificationSettingsUpdateInput
) {
  return httpPut<AdminNotificationSettings>("/api/admin/notification-settings", body, { token, tenantId });
}

export async function getAdminIntegrationsStatus(token: string, tenantId: string) {
  return httpGet<AdminIntegrationStatusResponse>("/api/admin/integrations/status", { token, tenantId });
}

export type PlatformHealthStatus = "HEALTHY" | "DEGRADED" | "WARNING" | "CRITICAL";
export type IntegrationReadinessStatus = "READY" | "DISABLED" | "NOT_CONFIGURED" | "ERROR" | "FUTURE";

export type PlatformSchedulerStatus = {
  schedulerName: string;
  enabled: boolean;
  lastRunAt: string | null;
  nextRunEstimate: string | null;
  executionCount: number;
  failureCount: number;
  skippedCount: number;
  avgExecutionTimeMs: number;
  lastFailureMessage: string | null;
  lockLastAcquiredAt: string | null;
  lockLastSkippedAt: string | null;
  lockAcquireCount: number;
  lockSkipCount: number;
};

export type PlatformQueueMetrics = {
  queueName: string;
  queueSize: number;
  pending: number;
  retrying: number;
  failed: number;
  processing: number;
  stale: number;
  throttled: number;
  suppressed: number;
};

export type PlatformProviderMetrics = {
  key: string;
  name: string;
  category: string;
  status: IntegrationReadinessStatus;
  enabled: boolean;
  configured: boolean;
  providerName: string | null;
  successCount: number;
  failureCount: number;
  timeoutCount: number;
  avgLatencyMs: number;
  lastFailure: string | null;
};

export type PlatformAiMetrics = {
  totalCalls: number;
  successfulCalls: number;
  failedCalls: number;
  inputTokens: number;
  outputTokens: number;
  estimatedCost: number;
  avgLatencyMs: number;
  callsByProvider: Record<string, number>;
  callsByUseCase: Record<string, number>;
  callsByStatus: Record<string, number>;
};

export type PlatformWebhookMetrics = {
  incomingWebhookCount: number;
  failedWebhookProcessingCount: number;
  invalidSignatureCount: number;
  retryProcessingCount: number;
  staleWebhookCount: number;
  providerCallbackFailureCount: number;
  replayAttemptCount: number;
  unknownProviderPayloadCount: number;
  dlqWebhookFailures: number;
  avgProcessingLatencyMs: number;
};

export type PlatformHealthResponse = {
  overallStatus: PlatformHealthStatus;
  degradedServices: string[];
  reminderScheduler: PlatformSchedulerStatus | null;
  aiCallScheduler: PlatformSchedulerStatus | null;
  queueMetrics: { queues: PlatformQueueMetrics[] };
  providerMetrics: { providers: PlatformProviderMetrics[] };
  aiMetrics: PlatformAiMetrics;
  webhookMetrics: PlatformWebhookMetrics;
  databaseHealthy: boolean;
  integrationsReady: boolean;
};

export type PlatformOpsAlertSeverity = "WARNING" | "CRITICAL";

export type PlatformOpsAlert = {
  id: string;
  tenantId: string | null;
  ruleKey: string | null;
  correlationId: string | null;
  sourceEntityId: string | null;
  alertType: string;
  severity: PlatformOpsAlertSeverity;
  source: string;
  message: string;
  status: string;
  occurrenceCount: number;
  firstSeenAt: string;
  lastSeenAt: string;
  createdAt: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  resolvedBy: string | null;
  resolutionNotes: string | null;
  resolvedAt: string | null;
};

export type PlatformProviderSlo = {
  provider: string;
  providerType: string;
  attempts: number;
  webhookCallbacks: number;
  retries: number;
  failures: number;
  timeouts: number;
  avgLatencyMs: number;
  successRatePct: number;
  timeoutRatePct: number;
  retryRatePct: number;
  failoverUsageCount: number;
  deliverySlaBreached: boolean;
  providerDegraded: boolean;
};

export type PlatformRuntimeSummary = {
  recentFailures: number;
  retryStormSignals: number;
  repeatedProviderFailures: number;
  staleExecutions: number;
  notes: string[];
};

export type DeadLetterRow = {
  id: string;
  tenantId: string;
  sourceType: string;
  sourceExecutionId: string;
  failureReason: string | null;
  payloadSummary: string | null;
  retryCount: number;
  deadLetteredAt: string;
  recoveryStatus: string;
  lastRecoveryError: string | null;
};

export async function getPlatformHealth(token: string, tenantId: string) {
  return httpGet<PlatformHealthResponse>("/api/ops/platform-health", { token, tenantId });
}

export async function getPlatformSchedulers(token: string, tenantId: string) {
  return httpGet<{ schedulers: PlatformSchedulerStatus[] }>("/api/ops/schedulers", { token, tenantId });
}

export async function getPlatformQueues(token: string, tenantId: string) {
  return httpGet<{ queues: PlatformQueueMetrics[] }>("/api/ops/queues", { token, tenantId });
}

export async function getPlatformProviders(token: string, tenantId: string) {
  return httpGet<{ providers: PlatformProviderMetrics[] }>("/api/ops/providers", { token, tenantId });
}

export async function getPlatformProviderSlos(token: string, tenantId: string) {
  return httpGet<{ providers: PlatformProviderSlo[] }>("/api/ops/provider-slos", { token, tenantId });
}

export async function getPlatformAiMetrics(token: string, tenantId: string) {
  return httpGet<PlatformAiMetrics>("/api/ops/ai-metrics", { token, tenantId });
}

export async function getPlatformWebhooks(token: string, tenantId: string) {
  return httpGet<PlatformWebhookMetrics>("/api/ops/webhooks", { token, tenantId });
}

export async function getPlatformAlerts(token: string, tenantId: string) {
  return httpGet<{ alerts: PlatformOpsAlert[] }>("/api/ops/alerts", { token, tenantId });
}

export async function acknowledgePlatformAlert(token: string, tenantId: string, id: string) {
  return httpPost<PlatformOpsAlert>("/api/ops/alerts/" + id + "/acknowledge", {}, { token, tenantId });
}

export async function resolvePlatformAlert(token: string, tenantId: string, id: string, notes?: string) {
  return httpPost<PlatformOpsAlert>("/api/ops/alerts/" + id + "/resolve", { notes }, { token, tenantId });
}

export async function suppressPlatformAlert(token: string, tenantId: string, id: string) {
  return httpPost<PlatformOpsAlert>("/api/ops/alerts/" + id + "/suppress", {}, { token, tenantId });
}

export async function getPlatformAlertRules(token: string, tenantId: string) {
  return httpGet<{ rules: Array<{
    id: string;
    tenantId: string | null;
    ruleKey: string;
    sourceType: string;
    enabled: boolean;
    severity: PlatformOpsAlertSeverity;
    thresholdType: string;
    thresholdValue: number;
    cooldownMinutes: number;
    autoResolveEnabled: boolean;
  }> }>("/api/ops/alerts/rules", { token, tenantId });
}

export async function getPlatformRuntimeSummary(token: string, tenantId: string) {
  return httpGet<PlatformRuntimeSummary>("/api/ops/runtime/summary", { token, tenantId });
}

export async function getPlatformDeadLetter(token: string, tenantId: string) {
  return httpGet<{ items: DeadLetterRow[] }>("/api/ops/dead-letter", { token, tenantId });
}

export async function replayPlatformDeadLetter(token: string, tenantId: string, id: string) {
  return httpPost<DeadLetterRow>(`/api/ops/dead-letter/${id}/replay`, {}, { token, tenantId });
}

export async function listCarePilotExecutions(token: string, tenantId: string) {
  return httpGet<CarePilotExecution[]>("/api/carepilot/executions", { token, tenantId });
}

export async function listCarePilotFailedExecutions(token: string, tenantId: string) {
  return httpGet<CarePilotExecution[]>("/api/carepilot/executions/failed", { token, tenantId });
}

export async function retryCarePilotExecution(token: string, tenantId: string, executionId: string) {
  return httpPatch<CarePilotExecution>(`/api/carepilot/executions/${executionId}/retry`, undefined, { token, tenantId });
}

export async function resendCarePilotExecution(token: string, tenantId: string, executionId: string) {
  return httpPatch<CarePilotExecution>(`/api/carepilot/executions/${executionId}/resend`, undefined, { token, tenantId });
}

export async function listCarePilotDeliveryAttempts(token: string, tenantId: string, executionId: string) {
  return httpGet<CarePilotDeliveryAttempt[]>(`/api/carepilot/executions/${executionId}/attempts`, { token, tenantId });
}

export async function getCarePilotAnalyticsSummary(token: string, tenantId: string, filters?: { startDate?: string; endDate?: string; campaignId?: string }) {
  const query = new URLSearchParams();
  if (filters?.startDate) query.set("startDate", filters.startDate);
  if (filters?.endDate) query.set("endDate", filters.endDate);
  if (filters?.campaignId) query.set("campaignId", filters.campaignId);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<CarePilotAnalyticsSummary>(`/api/carepilot/analytics/summary${suffix}`, { token, tenantId });
}

export async function listCarePilotOpsFailedExecutions(token: string, tenantId: string, filters?: {
  startDate?: string;
  endDate?: string;
  campaignId?: string;
  channel?: CarePilotChannelType;
  status?: CarePilotExecutionStatus;
  providerName?: string;
  retryableOnly?: boolean;
}) {
  const query = new URLSearchParams();
  if (filters?.startDate) query.set("startDate", filters.startDate);
  if (filters?.endDate) query.set("endDate", filters.endDate);
  if (filters?.campaignId) query.set("campaignId", filters.campaignId);
  if (filters?.channel) query.set("channel", filters.channel);
  if (filters?.status) query.set("status", filters.status);
  if (filters?.providerName) query.set("providerName", filters.providerName);
  if (filters?.retryableOnly) query.set("retryableOnly", "true");
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<CarePilotExecution[]>(`/api/carepilot/ops/failed-executions${suffix}`, { token, tenantId });
}

export async function getCarePilotExecutionTimeline(token: string, tenantId: string, executionId: string) {
  return httpGet<CarePilotExecutionTimeline>(`/api/carepilot/ops/executions/${executionId}/timeline`, { token, tenantId });
}

export async function listCarePilotMessagingProviderStatuses(token: string, tenantId: string) {
  return httpGet<CarePilotMessagingProviderStatus[]>("/api/carepilot/messaging/providers/status", { token, tenantId });
}

export async function sendCarePilotProviderTestMessage(
  token: string,
  tenantId: string,
  channel: "EMAIL" | "SMS" | "WHATSAPP",
  body: CarePilotProviderTestSendInput
) {
  return httpPost<CarePilotProviderTestSendResult>(`/api/carepilot/messaging/providers/${channel}/test-send`, body, { token, tenantId });
}

export async function listCarePilotReminders(token: string, tenantId: string, filters?: {
  status?: string;
  campaignId?: string;
  campaignType?: CarePilotCampaignType;
  channel?: CarePilotChannelType;
  patientId?: string;
  patientName?: string;
  fromDate?: string;
  toDate?: string;
  providerName?: string;
  page?: number;
  size?: number;
}) {
  const query = new URLSearchParams();
  if (filters?.status) query.set("status", filters.status);
  if (filters?.campaignId) query.set("campaignId", filters.campaignId);
  if (filters?.campaignType) query.set("campaignType", filters.campaignType);
  if (filters?.channel) query.set("channel", filters.channel);
  if (filters?.patientId) query.set("patientId", filters.patientId);
  if (filters?.patientName) query.set("patientName", filters.patientName);
  if (filters?.fromDate) query.set("fromDate", filters.fromDate);
  if (filters?.toDate) query.set("toDate", filters.toDate);
  if (filters?.providerName) query.set("providerName", filters.providerName);
  if (filters?.page != null) query.set("page", String(filters.page));
  if (filters?.size != null) query.set("size", String(filters.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<CarePilotReminderListResponse>(`/api/carepilot/reminders${suffix}`, { token, tenantId });
}

export async function getCarePilotReminder(token: string, tenantId: string, executionId: string) {
  return httpGet<CarePilotReminderDetail>(`/api/carepilot/reminders/${executionId}`, { token, tenantId });
}

export async function retryCarePilotReminder(token: string, tenantId: string, executionId: string) {
  return httpPatch<CarePilotExecution>(`/api/carepilot/reminders/${executionId}/retry`, undefined, { token, tenantId });
}

export async function resendCarePilotReminder(token: string, tenantId: string, executionId: string) {
  return httpPatch<CarePilotExecution>(`/api/carepilot/reminders/${executionId}/resend`, undefined, { token, tenantId });
}

export async function cancelCarePilotReminder(
  token: string,
  tenantId: string,
  executionId: string,
  body?: { reason?: string | null }
) {
  return httpPost<CarePilotExecution>(`/api/carepilot/reminders/${executionId}/cancel`, body ?? {}, { token, tenantId });
}

export async function suppressCarePilotReminder(
  token: string,
  tenantId: string,
  executionId: string,
  body?: { reason?: string | null }
) {
  return httpPost<CarePilotExecution>(`/api/carepilot/reminders/${executionId}/suppress`, body ?? {}, { token, tenantId });
}

export async function rescheduleCarePilotReminder(
  token: string,
  tenantId: string,
  executionId: string,
  body: { newScheduledAt: string; reason?: string | null }
) {
  return httpPost<CarePilotExecution>(`/api/carepilot/reminders/${executionId}/reschedule`, body, { token, tenantId });
}

export async function getCarePilotEngagementOverview(token: string, tenantId: string) {
  return httpGet<CarePilotEngagementOverview>("/api/carepilot/engagement/overview", { token, tenantId });
}

export async function listCarePilotEngagementCohort(
  token: string,
  tenantId: string,
  cohort: CarePilotEngagementCohort,
  params?: { offset?: number; limit?: number }
) {
  const query = new URLSearchParams();
  query.set("cohort", cohort);
  if (params?.offset != null) query.set("offset", String(params.offset));
  if (params?.limit != null) query.set("limit", String(params.limit));
  return httpGet<CarePilotEngagementCohortResponse>(`/api/carepilot/engagement/cohorts?${query.toString()}`, { token, tenantId });
}

export async function listCarePilotLeads(token: string, tenantId: string, filters?: {
  status?: CarePilotLeadStatus;
  source?: CarePilotLeadSource;
  assignedToAppUserId?: string;
  priority?: CarePilotLeadPriority;
  search?: string;
  followUpDue?: boolean;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}) {
  const query = new URLSearchParams();
  if (filters?.status) query.set("status", filters.status);
  if (filters?.source) query.set("source", filters.source);
  if (filters?.assignedToAppUserId) query.set("assignedToAppUserId", filters.assignedToAppUserId);
  if (filters?.priority) query.set("priority", filters.priority);
  if (filters?.search) query.set("search", filters.search);
  if (filters?.followUpDue) query.set("followUpDue", "true");
  if (filters?.createdFrom) query.set("createdFrom", filters.createdFrom);
  if (filters?.createdTo) query.set("createdTo", filters.createdTo);
  if (filters?.page != null) query.set("page", String(filters.page));
  if (filters?.size != null) query.set("size", String(filters.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<CarePilotLeadListResponse>(`/api/carepilot/leads${suffix}`, { token, tenantId });
}

export async function getCarePilotLead(token: string, tenantId: string, leadId: string) {
  return httpGet<CarePilotLead>(`/api/carepilot/leads/${leadId}`, { token, tenantId });
}

export async function createCarePilotLead(token: string, tenantId: string, body: Partial<CarePilotLead>) {
  return httpPost<CarePilotLead>("/api/carepilot/leads", body, { token, tenantId });
}

export async function updateCarePilotLead(token: string, tenantId: string, leadId: string, body: Partial<CarePilotLead>) {
  return httpPut<CarePilotLead>(`/api/carepilot/leads/${leadId}`, body, { token, tenantId });
}

export async function updateCarePilotLeadStatus(
  token: string,
  tenantId: string,
  leadId: string,
  body: { status?: CarePilotLeadStatus; priority?: CarePilotLeadPriority; assignedToAppUserId?: string | null; lastContactedAt?: string | null; nextFollowUpAt?: string | null; comment?: string | null }
) {
  return httpPost<CarePilotLead>(`/api/carepilot/leads/${leadId}/status`, body, { token, tenantId });
}

export async function addCarePilotLeadNote(token: string, tenantId: string, leadId: string, note: string) {
  return httpPost<CarePilotLead>(`/api/carepilot/leads/${leadId}/notes`, { note }, { token, tenantId });
}

export async function listCarePilotLeadActivities(token: string, tenantId: string, leadId: string, page = 0, size = 25) {
  return httpGet<CarePilotLeadActivityListResponse>(`/api/carepilot/leads/${leadId}/activities?page=${page}&size=${size}`, { token, tenantId });
}

export async function convertCarePilotLead(
  token: string,
  tenantId: string,
  leadId: string,
  body?: {
    bookAppointment?: boolean;
    appointment?: {
      doctorUserId: string;
      appointmentDate: string;
      appointmentTime?: string | null;
      reason?: string | null;
      notes?: string | null;
      priority?: AppointmentPriority | null;
    } | null;
  }
) {
  return httpPost<{ leadId: string; patientId: string; newlyCreated: boolean; appointmentId: string | null; appointmentError: string | null }>(
    `/api/carepilot/leads/${leadId}/convert`,
    body ?? {},
    { token, tenantId },
  );
}

export async function getCarePilotLeadAnalyticsSummary(token: string, tenantId: string, filters?: { startDate?: string; endDate?: string }) {
  const query = new URLSearchParams();
  if (filters?.startDate) query.set("startDate", filters.startDate);
  if (filters?.endDate) query.set("endDate", filters.endDate);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<CarePilotLeadAnalyticsSummary>(`/api/carepilot/leads/analytics/summary${suffix}`, { token, tenantId });
}

export async function listCarePilotWebinars(token: string, tenantId: string, filters?: {
  status?: CarePilotWebinarStatus;
  webinarType?: CarePilotWebinarType;
  scheduledFrom?: string;
  scheduledTo?: string;
  upcoming?: boolean;
  completed?: boolean;
  page?: number;
  size?: number;
}) {
  const query = new URLSearchParams();
  if (filters?.status) query.set("status", filters.status);
  if (filters?.webinarType) query.set("webinarType", filters.webinarType);
  if (filters?.scheduledFrom) query.set("scheduledFrom", filters.scheduledFrom);
  if (filters?.scheduledTo) query.set("scheduledTo", filters.scheduledTo);
  if (filters?.upcoming) query.set("upcoming", "true");
  if (filters?.completed) query.set("completed", "true");
  if (filters?.page != null) query.set("page", String(filters.page));
  if (filters?.size != null) query.set("size", String(filters.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<CarePilotWebinarListResponse>(`/api/carepilot/webinars${suffix}`, { token, tenantId });
}

export async function getCarePilotWebinar(token: string, tenantId: string, webinarId: string) {
  return httpGet<CarePilotWebinar>(`/api/carepilot/webinars/${webinarId}`, { token, tenantId });
}

export async function createCarePilotWebinar(token: string, tenantId: string, body: Partial<CarePilotWebinar>) {
  return httpPost<CarePilotWebinar>("/api/carepilot/webinars", body, { token, tenantId });
}

export async function updateCarePilotWebinar(token: string, tenantId: string, webinarId: string, body: Partial<CarePilotWebinar>) {
  return httpPut<CarePilotWebinar>(`/api/carepilot/webinars/${webinarId}`, body, { token, tenantId });
}

export async function updateCarePilotWebinarStatus(token: string, tenantId: string, webinarId: string, status: CarePilotWebinarStatus) {
  return httpPost<CarePilotWebinar>(`/api/carepilot/webinars/${webinarId}/status`, { status }, { token, tenantId });
}

export async function listCarePilotWebinarRegistrations(token: string, tenantId: string, webinarId: string, page = 0, size = 25) {
  return httpGet<CarePilotWebinarRegistrationListResponse>(`/api/carepilot/webinars/${webinarId}/registrations?page=${page}&size=${size}`, { token, tenantId });
}

export async function registerCarePilotWebinarAttendee(
  token: string,
  tenantId: string,
  webinarId: string,
  body: {
    patientId?: string | null;
    leadId?: string | null;
    attendeeName: string;
    attendeeEmail?: string | null;
    attendeePhone?: string | null;
    source?: CarePilotWebinarRegistrationSource;
    notes?: string | null;
  }
) {
  return httpPost<CarePilotWebinarRegistration>(`/api/carepilot/webinars/${webinarId}/register`, body, { token, tenantId });
}

export async function markCarePilotWebinarAttendance(
  token: string,
  tenantId: string,
  webinarId: string,
  body: {
    registrationId: string;
    registrationStatus: CarePilotWebinarRegistrationStatus;
    notes?: string | null;
  }
) {
  return httpPost<CarePilotWebinarRegistration>(`/api/carepilot/webinars/${webinarId}/attendance`, body, { token, tenantId });
}

export async function getCarePilotWebinarAnalyticsSummary(token: string, tenantId: string) {
  return httpGet<CarePilotWebinarAnalyticsSummary>("/api/carepilot/webinars/analytics/summary", { token, tenantId });
}

export async function listCarePilotAiCallCampaigns(token: string, tenantId: string) {
  return httpGet<CarePilotAiCallCampaign[]>("/api/carepilot/ai-calls/campaigns", { token, tenantId });
}

export async function updateCarePilotAiCallCampaignStatus(
  token: string,
  tenantId: string,
  campaignId: string,
  status: CarePilotAiCallCampaignStatus
) {
  return httpPost<CarePilotAiCallCampaign>(`/api/carepilot/ai-calls/campaigns/${campaignId}/status`, { status }, { token, tenantId });
}

export async function triggerCarePilotAiCallCampaign(
  token: string,
  tenantId: string,
  campaignId: string,
  body: { targets: Array<{ patientId?: string | null; leadId?: string | null; phoneNumber: string; script?: string | null; scheduledAt?: string | null }> }
) {
  return httpPost<CarePilotAiCallExecution[]>(`/api/carepilot/ai-calls/campaigns/${campaignId}/trigger`, body, { token, tenantId });
}

export async function listCarePilotAiCallExecutions(token: string, tenantId: string, filters?: {
  status?: CarePilotAiCallExecutionStatus;
  callType?: CarePilotAiCallType;
  patientId?: string;
  leadId?: string;
  startDate?: string;
  endDate?: string;
  escalationRequired?: boolean;
  provider?: string;
  campaignId?: string;
  page?: number;
  size?: number;
}) {
  const query = new URLSearchParams();
  if (filters?.status) query.set("status", filters.status);
  if (filters?.callType) query.set("callType", filters.callType);
  if (filters?.patientId) query.set("patientId", filters.patientId);
  if (filters?.leadId) query.set("leadId", filters.leadId);
  if (filters?.startDate) query.set("startDate", filters.startDate);
  if (filters?.endDate) query.set("endDate", filters.endDate);
  if (filters?.escalationRequired != null) query.set("escalationRequired", String(filters.escalationRequired));
  if (filters?.provider) query.set("provider", filters.provider);
  if (filters?.campaignId) query.set("campaignId", filters.campaignId);
  if (filters?.page != null) query.set("page", String(filters.page));
  if (filters?.size != null) query.set("size", String(filters.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<CarePilotAiCallExecutionListResponse>(`/api/carepilot/ai-calls/executions${suffix}`, { token, tenantId });
}

export async function retryCarePilotAiCallExecution(token: string, tenantId: string, executionId: string) {
  return httpPost<CarePilotAiCallExecution>(`/api/carepilot/ai-calls/executions/${executionId}/retry`, {}, { token, tenantId });
}

export async function cancelCarePilotAiCallExecution(token: string, tenantId: string, executionId: string, reason?: string | null) {
  return httpPost<CarePilotAiCallExecution>(`/api/carepilot/ai-calls/executions/${executionId}/cancel`, { reason: reason ?? null }, { token, tenantId });
}

export async function suppressCarePilotAiCallExecution(token: string, tenantId: string, executionId: string, reason?: string | null) {
  return httpPost<CarePilotAiCallExecution>(`/api/carepilot/ai-calls/executions/${executionId}/suppress`, { reason: reason ?? null }, { token, tenantId });
}

export async function rescheduleCarePilotAiCallExecution(
  token: string,
  tenantId: string,
  executionId: string,
  body: { scheduledAt: string; reason?: string | null }
) {
  return httpPost<CarePilotAiCallExecution>(`/api/carepilot/ai-calls/executions/${executionId}/reschedule`, body, { token, tenantId });
}

export async function getCarePilotAiCallTranscript(token: string, tenantId: string, executionId: string) {
  return httpGet<CarePilotAiCallTranscript>(`/api/carepilot/ai-calls/executions/${executionId}/transcript`, { token, tenantId });
}

export async function listCarePilotAiCallEvents(token: string, tenantId: string, executionId: string) {
  return httpGet<CarePilotAiCallEvent[]>(`/api/carepilot/ai-calls/executions/${executionId}/events`, { token, tenantId });
}

export async function dispatchDueCarePilotAiCalls(token: string, tenantId: string) {
  return httpPost<{ processed: number; dispatched: number; failed: number; skipped: number }>(
    "/api/carepilot/ai-calls/executions/dispatch-due",
    {},
    { token, tenantId }
  );
}

export async function getCarePilotAiCallSchedulerHealth(token: string, tenantId: string) {
  return httpGet<CarePilotAiCallSchedulerHealth>("/api/carepilot/ai-calls/scheduler-health", { token, tenantId });
}

export async function getCarePilotAiCallAnalyticsSummary(token: string, tenantId: string) {
  return httpGet<CarePilotAiCallAnalyticsSummary>("/api/carepilot/ai-calls/analytics/summary", { token, tenantId });
}

export type AiOpsPromptDefinition = {
  id: string;
  tenantId: string | null;
  promptKey: string;
  name: string;
  description: string | null;
  domain: string | null;
  useCase: string | null;
  activeVersion: number | null;
  systemPrompt: boolean;
  updatedAt: string;
};

export type AiOpsPromptVersion = {
  id: string;
  version: number;
  status: "DRAFT" | "ACTIVE" | "ARCHIVED";
  modelHint: string | null;
  temperature: number | null;
  maxTokens: number | null;
  systemPrompt: string;
  userPromptTemplate: string;
  variablesJson: string | null;
  guardrailProfile: string | null;
  createdAt: string;
  activatedAt: string | null;
};

export type AiOpsPromptDetail = {
  definition: AiOpsPromptDefinition;
  versions: AiOpsPromptVersion[];
};

export type AiOpsInvocationLog = {
  id: string;
  requestId: string | null;
  domain: string | null;
  useCase: string | null;
  promptKey: string | null;
  promptVersion: number | null;
  providerName: string | null;
  modelName: string | null;
  status: string;
  inputTokenCount: number | null;
  outputTokenCount: number | null;
  estimatedCost: number | null;
  latencyMs: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
};

export type AiOpsUsageSummary = {
  totalCalls: number;
  successfulCalls: number;
  failedCalls: number;
  inputTokens: number;
  outputTokens: number;
  estimatedCost: number;
  avgLatencyMs: number;
  callsByProvider: Record<string, number>;
  callsByUseCase: Record<string, number>;
  callsByStatus: Record<string, number>;
};

export type AiOpsTool = {
  id: string;
  tenantId: string | null;
  toolKey: string;
  name: string;
  description: string | null;
  category: string | null;
  enabled: boolean;
  riskLevel: string | null;
  requiresApproval: boolean;
  inputSchemaJson: string | null;
  outputSchemaJson: string | null;
  updatedAt: string;
};

export type AiOpsGuardrail = {
  id: string;
  tenantId: string | null;
  profileKey: string;
  name: string;
  description: string | null;
  enabled: boolean;
  blockedTopicsJson: string | null;
  piiRedactionEnabled: boolean;
  humanApprovalRequired: boolean;
  maxOutputTokens: number | null;
  updatedAt: string;
};

export type AiOpsWorkflowRun = {
  id: string;
  workflowKey: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  failureReason: string | null;
  inputSummary: string | null;
  outputSummary: string | null;
};

export type AiOpsWorkflowStep = {
  id: string;
  workflowRunId: string;
  stepName: string;
  stepType: string | null;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
  providerName: string | null;
  toolKey: string | null;
  errorMessage: string | null;
};

export async function listAiOpsPrompts(token: string, tenantId: string) {
  return httpGet<AiOpsPromptDefinition[]>("/api/ai/prompts", { token, tenantId });
}

export async function getAiOpsPrompt(token: string, tenantId: string, id: string) {
  return httpGet<AiOpsPromptDetail>(`/api/ai/prompts/${id}`, { token, tenantId });
}

export async function createAiOpsPrompt(
  token: string,
  tenantId: string,
  body: { promptKey: string; name: string; description?: string | null; domain?: string | null; useCase?: string | null; systemPrompt?: boolean }
) {
  return httpPost<AiOpsPromptDefinition>("/api/ai/prompts", body, { token, tenantId });
}

export async function createAiOpsPromptVersion(
  token: string,
  tenantId: string,
  promptId: string,
  body: { modelHint?: string | null; temperature?: number | null; maxTokens?: number | null; systemPrompt: string; userPromptTemplate: string; variablesJson?: string | null; guardrailProfile?: string | null }
) {
  return httpPost<AiOpsPromptVersion>(`/api/ai/prompts/${promptId}/versions`, body, { token, tenantId });
}

export async function activateAiOpsPromptVersion(token: string, tenantId: string, promptId: string, versionId: string) {
  return httpPost<AiOpsPromptDetail>(`/api/ai/prompts/${promptId}/versions/${versionId}/activate`, {}, { token, tenantId });
}

export async function archiveAiOpsPromptVersion(token: string, tenantId: string, promptId: string, versionId: string) {
  return httpPost<AiOpsPromptDetail>(`/api/ai/prompts/${promptId}/versions/${versionId}/archive`, {}, { token, tenantId });
}

export async function listAiOpsInvocations(token: string, tenantId: string) {
  return httpGet<AiOpsInvocationLog[]>("/api/ai/invocations", { token, tenantId });
}

export async function getAiOpsUsageSummary(
  token: string,
  tenantId: string,
  params: { fromDate?: string; toDate?: string; provider?: string; useCase?: string } = {}
) {
  const query = new URLSearchParams();
  if (params.fromDate) query.set("fromDate", params.fromDate);
  if (params.toDate) query.set("toDate", params.toDate);
  if (params.provider) query.set("provider", params.provider);
  if (params.useCase) query.set("useCase", params.useCase);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<AiOpsUsageSummary>(`/api/ai/usage/summary${suffix}`, { token, tenantId });
}

export async function listAiOpsTools(token: string, tenantId: string) {
  return httpGet<AiOpsTool[]>("/api/ai/tools", { token, tenantId });
}

export async function listAiOpsGuardrails(token: string, tenantId: string) {
  return httpGet<AiOpsGuardrail[]>("/api/ai/guardrails", { token, tenantId });
}

export async function listAiOpsWorkflowRuns(token: string, tenantId: string) {
  return httpGet<AiOpsWorkflowRun[]>("/api/ai/workflows/runs", { token, tenantId });
}

export async function listAiOpsWorkflowSteps(token: string, tenantId: string, runId: string) {
  return httpGet<AiOpsWorkflowStep[]>(`/api/ai/workflows/runs/${runId}/steps`, { token, tenantId });
}

export type RealtimeVoiceSessionType =
  | "AI_RECEPTIONIST"
  | "APPOINTMENT_BOOKING"
  | "FOLLOW_UP_CALL"
  | "LEAD_QUALIFICATION"
  | "FAQ_ASSISTANT"
  | "MANUAL_TRANSFER";

export type RealtimeVoiceSessionStatus =
  | "CREATED"
  | "CONNECTING"
  | "ACTIVE"
  | "PAUSED"
  | "ESCALATED"
  | "COMPLETED"
  | "FAILED";

export type RealtimeVoiceSession = {
  id: string;
  tenantId: string;
  sessionType: RealtimeVoiceSessionType;
  sessionStatus: RealtimeVoiceSessionStatus;
  patientId: string | null;
  leadId: string | null;
  startedAt: string;
  endedAt: string | null;
  escalationRequired: boolean;
  escalationReason: string | null;
  assignedHumanUserId: string | null;
  aiProvider: string | null;
  sttProvider: string | null;
  ttsProvider: string | null;
  metadataJson: string | null;
};

export type RealtimeVoiceEvent = {
  id: string;
  sessionId: string;
  eventType: string;
  eventTimestamp: string;
  sequenceNumber: number;
  payloadSummary: string | null;
  correlationId: string | null;
};

export type RealtimeVoiceTranscript = {
  id: string;
  sessionId: string;
  speakerType: "USER" | "AI" | "HUMAN_AGENT" | "SYSTEM";
  text: string;
  timestamp: string;
  confidence: number | null;
};

export type RealtimeVoiceSummary = {
  activeSessions: number;
  escalationCount: number;
  failedSessions: number;
  avgAiLatencyMs: number;
  avgSttLatencyMs: number;
  avgTtsLatencyMs: number;
  avgTranscriptLatencyMs: number;
  websocketDisconnects: number;
  sttFailures: number;
  ttsFailures: number;
  interruptionCount: number;
  sttProviders: Array<{ providerName: string; ready: boolean }>;
  ttsProviders: Array<{ providerName: string; ready: boolean }>;
  runtimeStatus: {
    status: string;
    sttReady: boolean;
    ttsReady: boolean;
    modelReady: boolean;
    activeSessions: number;
    uptimeSeconds: number;
    error: string | null;
  };
};

export async function getRealtimeVoiceSummary(token: string, tenantId: string) {
  return httpGet<RealtimeVoiceSummary>("/api/realtime-ai/summary", { token, tenantId });
}

export async function listRealtimeVoiceSessions(token: string, tenantId: string) {
  return httpGet<{ sessions: RealtimeVoiceSession[] }>("/api/realtime-ai/sessions", { token, tenantId });
}

export async function createRealtimeVoiceSession(token: string, tenantId: string, input: {
  sessionType: RealtimeVoiceSessionType;
  patientId?: string | null;
  leadId?: string | null;
  metadataJson?: string | null;
}) {
  return httpPost<{ session: RealtimeVoiceSession }>("/api/realtime-ai/sessions", input, { token, tenantId });
}

export async function getRealtimeVoiceSession(token: string, tenantId: string, sessionId: string) {
  return httpGet<{ session: RealtimeVoiceSession }>(`/api/realtime-ai/sessions/${sessionId}`, { token, tenantId });
}

export async function getRealtimeVoiceSessionEvents(token: string, tenantId: string, sessionId: string) {
  return httpGet<{ events: RealtimeVoiceEvent[] }>(`/api/realtime-ai/sessions/${sessionId}/events`, { token, tenantId });
}

export async function getRealtimeVoiceSessionTranscripts(token: string, tenantId: string, sessionId: string) {
  return httpGet<{ transcripts: RealtimeVoiceTranscript[] }>(`/api/realtime-ai/sessions/${sessionId}/transcripts`, { token, tenantId });
}

export async function sendRealtimeVoiceTurn(token: string, tenantId: string, sessionId: string, input: {
  text: string;
  promptKey: string;
  patientContextJson?: string | null;
}) {
  return httpPost<{ userTranscript: RealtimeVoiceTranscript; aiTranscript: RealtimeVoiceTranscript; escalationReason: string | null; aiProvider: string; aiLatencyMs: number }>(
    `/api/realtime-ai/sessions/${sessionId}/turns`,
    input,
    { token, tenantId }
  );
}

export async function completeRealtimeVoiceSession(token: string, tenantId: string, sessionId: string) {
  return httpPost<{ session: RealtimeVoiceSession }>(`/api/realtime-ai/sessions/${sessionId}/complete`, {}, { token, tenantId });
}
