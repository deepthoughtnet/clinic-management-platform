import { httpDelete, httpGet, httpGetText, httpPatch, httpPost, httpPostForm, httpPut, httpPutForm } from "./restClient";
import { buildHelpRequestOptions } from "./helpClient";

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
export type NotificationChannel = "EMAIL" | "WHATSAPP" | "SMS" | "PUSH" | "IN_APP";
export type NotificationEventType =
  | "APPOINTMENT_BOOKED"
  | "APPOINTMENT_RESCHEDULED"
  | "APPOINTMENT_CANCELLED"
  | "APPOINTMENT_NO_SHOW"
  | "PRESCRIPTION_READY"
  | "PRESCRIPTION_SENT"
  | "BILL_GENERATED"
  | "BILL_PAID"
  | "RECEIPT_SENT"
  | "REFUND_PROCESSED"
  | "LAB_ORDER_CREATED"
  | "LAB_SAMPLE_COLLECTED"
  | "LAB_REPORT_READY"
  | "LAB_REPORT_REVIEWED"
  | "LAB_CRITICAL_RESULT"
  | "FOLLOW_UP_DUE"
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
  | "CUSTOMER_RETURN_IN"
  | "CUSTOMER_RETURN_NON_SELLABLE"
  | "VENDOR_RETURN_OUT"
  | "WRITE_OFF"
  | "EXPIRED"
  | "CANCELLED_DISPENSE"
  | "OPENING"
  | "PURCHASE"
  | "SALE"
  | "ADJUSTMENT"
  | "TRANSFER_IN"
  | "TRANSFER_OUT";
export type ClinicalDocumentType =
  | "EXTERNAL_LAB_REPORT"
  | "RADIOLOGY_REPORT"
  | "REFERRAL_LETTER"
  | "DISCHARGE_SUMMARY"
  | "OLD_PRESCRIPTION"
  | "INTERNAL_LAB_REPORT"
  | "INSURANCE_DOCUMENT"
  | "IDENTITY_DOCUMENT"
  | "OTHER"
  | "LAB_REPORT"
  | "PRESCRIPTION"
  | "X_RAY"
  | "MRI_CT"
  | "REFERRAL"
  | "INSURANCE"
  | "VACCINATION"
  | "ATTACHMENT";

export type ClinicalDocumentUploadSource = "RECEPTION" | "DOCTOR" | "LABORATORY" | "PATIENT_PORTAL" | "IMPORT" | "OTHER";
export type ClinicalDocumentVisibility = "INTERNAL_ONLY" | "PATIENT_VISIBLE";
export type ClinicalDocumentVerificationStatus = "UNVERIFIED" | "VERIFIED";
export type ClinicalDocumentDocumentStatus = "NOT_STARTED" | "PENDING" | "COMPLETED" | "FAILED";

export type ConsultationGeneratedDocumentResponse = {
  documentId: string;
  downloadUrl: string;
  expiresInSeconds: string;
  filename: string;
  title: string;
  documentType: ClinicalDocumentType;
};

export type GeneratedVaccinationDocumentResponse = {
  documentId: string;
  downloadUrl: string;
  expiresInSeconds: string;
  filename: string;
  title: string;
  documentNumber: string;
  generatedAt: string;
  generatedBy: string;
};

export type ClinicalDocument = {
  id: string;
  patientId: string;
  consultationId: string | null;
  sourceModule: string | null;
  sourceEntityId: string | null;
  uploadedByUserId: string;
  uploadedByName: string;
  documentType: ClinicalDocumentType;
  title: string;
  description: string | null;
  reportDate: string | null;
  uploadSource: ClinicalDocumentUploadSource;
  originalFilename: string;
  mediaType: string;
  sizeBytes: number;
  checksumSha256: string;
  storageBucket: string;
  storageKey: string;
  visibility: ClinicalDocumentVisibility;
  verificationStatus: ClinicalDocumentVerificationStatus;
  ocrStatus: ClinicalDocumentDocumentStatus | string | null;
  aiIndexStatus: ClinicalDocumentDocumentStatus | string | null;
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
  aiOps: {
    lastAiRetryAt: string | null;
    lastAiRetryStatus: string | null;
    lastAiRetryMessage: string | null;
    lastAiRetryJobId: string | null;
    lastMemoryRepairAt: string | null;
    lastMemoryRepairStatus: string | null;
    lastMemoryRepairMessage: string | null;
    lastMemoryRepairBy: string | null;
    lastMemoryRepairDeletedPendingConceptCount: number | null;
    lastMemoryRepairInsertedConceptCount: number | null;
    lastMemoryRepairSkippedAcceptedConceptCount: number | null;
    lastMemoryRepairFilteredPollutedConceptCount: number | null;
    lastMemoryRepairCorrectedValues: string | null;
  } | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ClinicalMemoryRepairResult = {
  documentId: string | null;
  status: "SUCCESS" | "FAILED" | string;
  repairedAt: string;
  repairedBy: string | null;
  deletedPendingConceptCount: number;
  insertedConceptCount: number;
  skippedAcceptedConceptCount: number;
  correctedValues: Array<{
    conceptKey: string;
    oldValue: string | null;
    newValue: string | null;
    unit: string | null;
  }>;
  filteredPollutedConceptCount: number;
  message: string;
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

export type ClinicalContextResponse = {
  tenantId: string;
  patientId: string;
  consultationId: string | null;
  longitudinalMemory: {
    knownConditions: Array<{
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    }>;
    longTermMedications: Array<{
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    }>;
    latestHbA1c: {
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    } | null;
    latestBloodSugar: {
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    } | null;
    latestLipidSummary: Array<{
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    }>;
    latestBloodPressure: {
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    } | null;
    latestBmi: {
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    } | null;
    riskFlags: Array<{
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    }>;
    history: Array<{
      conceptFamily: string;
      conceptKey: string;
      label: string;
      valueText: string | null;
      valueUnit: string | null;
      sourceDocumentTitle: string | null;
      sourceDocumentType: string | null;
      sourceDocumentId: string | null;
      observedOn: string | null;
      confidence: number | null;
      verificationStatus: string | null;
      evidenceText: string | null;
    }>;
    mostRecentLaboratorySummary: string | null;
  };
  patientSummary: {
    patientName: string | null;
    ageYears: number | null;
    gender: string | null;
    chronicConditions: string | null;
    allergies: string | null;
    currentMedications: string[];
    lastConsultationDate: string | null;
  };
  previousVisits: Array<{
    consultationId: string;
    consultationDate: string | null;
    diagnosis: string | null;
    treatmentSummary: string | null;
    advice: string | null;
  }>;
  medicationHistory: {
    activeMedicines: string[];
    discontinuedMedicines: string[];
    recentAntibiotics: string[];
    duplicateMedicines: string[];
    alerts: string[];
  };
  diagnosisHistory: {
    lastVisitDiagnosis: string | null;
    previousDiagnoses: string[];
  };
  intakeSummary: {
    complete: boolean;
    chiefComplaint: string | null;
    latestVitals: {
      heightCm: number | null;
      weightKg: number | null;
      bmi: number | null;
      bmiCategory: string | null;
      bloodPressureSystolic: number | null;
      bloodPressureDiastolic: number | null;
      pulseRate: number | null;
      temperature: number | null;
      temperatureUnit: TemperatureUnit | null;
      spo2: number | null;
      respiratoryRate: number | null;
      randomBloodSugar: number | null;
      painScore: number | null;
    } | null;
    vitalsTrendSummary: string | null;
    abnormalVitalsAlerts: string[];
    uploadedDocumentSummary: string | null;
    notes: string | null;
    recordedByName: string | null;
    recordedAt: string | null;
  } | null;
  labIntelligence: {
    latestLabReport: string | null;
    abnormalValues: string[];
    previousTrends: string[];
    pendingInvestigations: string[];
    lastHbA1c: string | null;
    lastCbc: string | null;
    lastCreatinine: string | null;
    latestBloodSugar: string | null;
    latestLipidSummary: string | null;
    latestBloodPressure: string | null;
    latestBmi: string | null;
  };
  documentIntelligence: {
    recentReports: string[];
    radiology: string[];
    referrals: string[];
    dischargeSummaries: string[];
  };
  timelineSummary: {
    events: Array<{
      occurredOn: string | null;
      title: string;
      detail: string | null;
      type: string;
    }>;
    recentImportantEvents: string | null;
  };
  aiSummary: string;
  aiPromptContext: string;
  clinicalContextJson: string;
  generatedAt: string;
};

export type ClinicalReasoningResult = {
  consultationId: string;
  patientId: string;
  generatedAt: string;
  provider: string | null;
  model: string | null;
  confidence: string;
  primaryDiagnosis: {
    name: string | null;
    confidence: number | null;
    status: string | null;
    whyConsidered: string | null;
    whyLessLikely: string | null;
    supportingEvidence: Array<{
      text: string | null;
      source: string | null;
      observationDate: string | null;
      confidence: number | null;
      type: string | null;
      sourceType?: string | null;
      sourceTitle?: string | null;
      verificationStatus?: string | null;
    }>;
    contradictingEvidence: Array<{
      text: string | null;
      source: string | null;
      observationDate: string | null;
      confidence: number | null;
      type: string | null;
      sourceType?: string | null;
      sourceTitle?: string | null;
      verificationStatus?: string | null;
    }>;
    missingInformation: Array<{
      name: string | null;
      whyItMatters: string | null;
      requestedAction: string | null;
      confidence: number | null;
    }>;
    recommendedTests: Array<{
      name: string | null;
      reason: string | null;
      priority: string | null;
      timing: string | null;
      confidence: number | null;
      source: string | null;
      observationDate: string | null;
      sourceType?: string | null;
      sourceTitle?: string | null;
      verificationStatus?: string | null;
      alreadyAvailable?: boolean | null;
      pendingOrderExists?: boolean | null;
      actionType?: string | null;
    }>;
    redFlags: Array<{
      name: string | null;
      reason: string | null;
      severity: string | null;
      action: string | null;
      confidence: number | null;
      source: string | null;
      observationDate: string | null;
      sourceType?: string | null;
      sourceTitle?: string | null;
      verificationStatus?: string | null;
    }>;
  } | null;
  differentialDiagnoses: Array<{
    name: string | null;
    confidence: number | null;
    status: string | null;
    whyConsidered: string | null;
    whyLessLikely: string | null;
    supportingEvidence: Array<{
      text: string | null;
      source: string | null;
      observationDate: string | null;
      confidence: number | null;
      type: string | null;
    }>;
    contradictingEvidence: Array<{
      text: string | null;
      source: string | null;
      observationDate: string | null;
      confidence: number | null;
      type: string | null;
    }>;
    missingInformation: Array<{
      name: string | null;
      whyItMatters: string | null;
      requestedAction: string | null;
      confidence: number | null;
    }>;
    recommendedTests: Array<{
      name: string | null;
      reason: string | null;
      priority: string | null;
      timing: string | null;
      confidence: number | null;
      source: string | null;
      observationDate: string | null;
    }>;
    redFlags: Array<{
      name: string | null;
      reason: string | null;
      severity: string | null;
      action: string | null;
      confidence: number | null;
      source: string | null;
      observationDate: string | null;
    }>;
  }>;
  supportingEvidence: Array<{
    text: string | null;
    source: string | null;
    observationDate: string | null;
    confidence: number | null;
    type: string | null;
  }>;
  contradictingEvidence: Array<{
    text: string | null;
    source: string | null;
    observationDate: string | null;
    confidence: number | null;
    type: string | null;
  }>;
  missingInformation: Array<{
    name: string | null;
    whyItMatters: string | null;
    requestedAction: string | null;
    confidence: number | null;
  }>;
  redFlags: Array<{
    name: string | null;
    reason: string | null;
    severity: string | null;
    action: string | null;
    confidence: number | null;
    source: string | null;
    observationDate: string | null;
  }>;
  recommendedTests: Array<{
    name: string | null;
    reason: string | null;
    priority: string | null;
    timing: string | null;
    confidence: number | null;
    source: string | null;
    observationDate: string | null;
    sourceType?: string | null;
    sourceTitle?: string | null;
    verificationStatus?: string | null;
    alreadyAvailable?: boolean | null;
    pendingOrderExists?: boolean | null;
    actionType?: string | null;
  }>;
  reasoningSummary: string | null;
  safetyNotes: Array<{
      message: string | null;
      severity: string | null;
      rationale: string | null;
      action: string | null;
      sourceType?: string | null;
      sourceTitle?: string | null;
      verificationStatus?: string | null;
      actionType?: string | null;
    }>;
  followUpAdvice: string[];
  patientExplanation: string | null;
  sourceContextSummary: {
    chiefComplaint: string | null;
    symptoms: string[];
    vitals: string | null;
    vitalsSource?: string | null;
    knownConditions: string[];
    recentReports: string[];
    currentMedicines: string[];
  };
  metadata: {
    reasoningEngineVersion?: string | null;
    promptVersion: string | null;
    contextVersion: string | null;
    schemaVersion?: string | null;
    provider: string | null;
    model: string | null;
    tokens: Record<string, unknown>;
    parseStatus: string;
    requestId: string | null;
    correlationId: string | null;
    latencyMs: number | null;
    fallbackUsed: boolean;
    finishReason: string | null;
    normalizedFinishReason?: string | null;
    rawChars: number | null;
    errorMessage: string | null;
    responseChars?: number | null;
  };
};

export type ClinicalReasoningResponse = {
  consultation: {
    consultationId: string;
    patientId: string;
    status: string | null;
    chiefComplaint: string | null;
    symptoms: string | null;
    diagnosis: string | null;
    advice: string | null;
    clinicalNotes: string | null;
    vitals: string | null;
    vitalsSource: string | null;
    vitalsSourceTitle: string | null;
  } | null;
  clinicalContextSummary: {
    patientName: string | null;
    ageYears: number | null;
    gender: string | null;
    chiefComplaint: string | null;
    vitals: string | null;
    vitalsSource: string | null;
    chronicConditions: string | null;
    allergies: string | null;
    knownConditions: string[];
    latestLabs: string[];
    pendingInvestigations: string[];
    recentReports: string[];
    riskFlags: string[];
    lastVisitDiagnosis: string | null;
    safetyContext: string | null;
  } | null;
  reasoningResult: ClinicalReasoningResult | null;
  metadata: ClinicalReasoningResult["metadata"] | null;
  debug?: Record<string, unknown> | null;
};

export type ClinicalIntakeResponse = {
  id: string;
  tenantId: string;
  patientId: string;
  appointmentId: string | null;
  consultationId: string | null;
  status: "PENDING_INTAKE" | "INTAKE_COMPLETE" | string;
  chiefComplaint: string | null;
  heightCm: number | null;
  weightKg: number | null;
  bmi: number | null;
  bmiCategory: string | null;
  bloodPressureSystolic: number | null;
  bloodPressureDiastolic: number | null;
  pulseRate: number | null;
  temperature: number | null;
  temperatureUnit: TemperatureUnit | null;
  spo2: number | null;
  respiratoryRate: number | null;
  randomBloodSugar: number | null;
  painScore: number | null;
  notes: string | null;
  recordedByUserId: string | null;
  recordedByName: string | null;
  complete: boolean;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
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
    patientMobile: string | null;
    patientNumber: string | null;
    doctorUserId: string | null;
    doctorName: string | null;
    tokenNumber: number | null;
    appointmentTime: string | null;
    waitingSince: string | null;
    consultationFeeStatus: string | null;
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
  readAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type VoiceProviderTrace = {
  sttProvider: string | null;
  llmProvider: string | null;
  ttsProvider: string | null;
};

export type VoiceWorkflowMode = "generic" | "appointment-booking";

export type VoiceSuggestedSlot = {
  doctorUserId: string | null;
  doctorName: string | null;
  appointmentDate: string | null;
  slotTime: string | null;
  slotEndTime: string | null;
};

export type VoiceWorkflowSummary = {
  mode: string;
  intentState: string | null;
  bookingWorkflowState: string | null;
  language: string | null;
  contactChannel: string | null;
  patientId: string | null;
  patientName: string | null;
  patientPhone: string | null;
  patientNumber: string | null;
  patientMatchStatus: string | null;
  doctorUserId: string | null;
  doctorName: string | null;
  doctorMatchStatus: string | null;
  preferredDate: string | null;
  preferredTimeWindow: string | null;
  reason: string | null;
  missingFields: string[] | null;
  suggestedSlot: VoiceSuggestedSlot | null;
  slotSuggestions: VoiceSuggestedSlot[] | null;
  confirmationRequested: boolean;
  bookingConfirmed: boolean;
  booked: boolean;
  bookedAppointmentId: string | null;
  handoffRequired: boolean;
  handoffReason: string | null;
  nextPrompt: string | null;
  unresolvedTurns: number;
  patientOptions: string[] | null;
  doctorOptions: string[] | null;
};

export type VoiceDebugTraceEntry = {
  stage: string;
  ok: boolean;
  provider: string | null;
  from: string | null;
  to: string | null;
  filename: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  url: string | null;
  multipartField: string | null;
  status: number | null;
  bodyPreview: string | null;
  durationMs: number | null;
  transcriptLength: number | null;
  reason: string | null;
  savedPath: string | null;
};

export type VoiceTestResponse = {
  requestId: string;
  transcript: string | null;
  assistantText: string | null;
  audioContentType: string | null;
  audioBase64: string | null;
  providerTrace: VoiceProviderTrace | null;
  voiceDebugTrace: VoiceDebugTraceEntry[] | null;
  workflowSummary: VoiceWorkflowSummary | null;
};

export type VoiceSttDebugResponse = {
  requestId: string;
  transcript: string | null;
  sttProvider: string | null;
  voiceDebugTrace: VoiceDebugTraceEntry[] | null;
};

export type VoiceServiceStatus = {
  provider: string;
  reachable: boolean;
  ready: boolean;
  message: string | null;
};

export type VoiceStatusResponse = {
  enabled: boolean;
  stt: VoiceServiceStatus;
  tts: VoiceServiceStatus;
  providerTrace: VoiceProviderTrace | null;
  sttConfiguredLanguage: string | null;
  ttsConfiguredVoice: string | null;
  ttsConfiguredVoices: Record<string, string> | null;
  ttsHindiConfigured: boolean;
  ttsFallbackVoiceEnabled: boolean;
};

export type VoiceLiveStatusResponse = {
  websocketEnabled: boolean;
  websocketPath: string;
  authMode: string;
  tenantMode: string;
  vadMode: string;
  vadProvider: string;
  heartbeatIntervalMs: number;
  staleAfterMs: number;
  maxSessionDurationSeconds: number;
  maxIdleSeconds: number;
  maxTurnsPerSession: number;
  maxAudioBytesPerTurn: number;
};

export type ReportRow = Record<string, string | number | boolean | null>;

export type CashCounterSummary = {
  todayTotalCollected: number;
  clinicBillingCollected: number;
  pharmacyPosCollected: number;
  refundsReturns: number;
  netCash: number;
  netUpi: number;
  netCard: number;
  netOther: number;
  openCashierShifts: number;
  varianceAlerts: number;
};

export type CashCounterLedgerRow = {
  dateTime: string | null;
  source: string;
  businessReference: string;
  receiptNumber: string;
  patientCustomer: string;
  paymentMode: string;
  grossAmount: number;
  refundAmount: number;
  netAmount: number;
  cashier: string;
  shiftReference: string;
  status: string;
};

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
  publicListingEnabled: boolean;
  slug: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ClinicProfileInput = Omit<ClinicProfile, "id" | "tenantId" | "createdAt" | "updatedAt">;

export type ClinicUser = {
  appUserId: string;
  tenantId: string;
  keycloakSub: string | null;
  email: string | null;
  username: string | null;
  department: string | null;
  displayName: string | null;
  userStatus: string;
  membershipRole: string | null;
  membershipStatus: string | null;
  employeeCode: string | null;
  mobile: string | null;
  lastLoginAt: string | null;
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
  specializations: string[];
  qualification: string | null;
  registrationNumber: string | null;
  consultationRoom: string | null;
  consultationFee: number | null;
  opdFee: number | null;
  followUpFee: number | null;
  emergencyFee: number | null;
  yearsOfExperience: number | null;
  age: number | null;
  active: boolean;
  publicListingEnabled: boolean;
  slug: string | null;
  photoUrl: string | null;
  photoFileName: string | null;
  photoMimeType: string | null;
  photoSizeBytes: number | null;
  updatedAt: string | null;
};

export type DoctorProfileInput = {
  mobile: string | null;
  specialization: string | null;
  specializations?: string[] | null;
  qualification: string | null;
  registrationNumber: string | null;
  consultationRoom: string | null;
  consultationFee: number | null;
  opdFee?: number | null;
  followUpFee?: number | null;
  emergencyFee?: number | null;
  yearsOfExperience: number | null;
  age: number | null;
  active: boolean | null;
  publicListingEnabled?: boolean | null;
  slug?: string | null;
};

export type ClinicRole = {
  role: string;
  displayName: string;
  permissions: string[];
};

export type TenantOnboardingStatus = {
  tenantId: string;
  completed: boolean;
  skipped: boolean;
  completedAt: string | null;
  skippedAt: string | null;
  createdAt: string;
  updatedAt: string;
  requiresSetup: boolean;
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
  canEdit: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PatientDetail = {
  patient: Patient;
  upcomingAppointments: Appointment[];
  recentAppointments: Appointment[];
  previousConsultations: Consultation[];
};

export type PatientInput = Omit<Patient, "id" | "tenantId" | "patientNumber" | "canEdit" | "createdAt" | "updatedAt">;

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
  displayReference: string | null;
  reason: string | null;
  type: AppointmentType;
  priority: AppointmentPriority;
  status: AppointmentStatus;
  consultationFeeStatus?: "NOT_CONFIGURED" | "UNPAID" | "PARTIAL" | "PAID" | null;
  consultationFeeAmount?: number | null;
  consultationFeePaidAmount?: number | null;
  consultationFeeDueAmount?: number | null;
  paymentBypassReason: string | null;
  paymentBypassNotes: string | null;
  paymentBypassedBy: string | null;
  paymentBypassedAt: string | null;
  clinicalIntakeStatus?: string | null;
  clinicalIntakeChiefComplaint?: string | null;
  clinicalIntakeRecordedAt?: string | null;
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
  allowAdHocBooking?: boolean;
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

export type DoctorAvailabilitySlotStatus = "AVAILABLE" | "PARTIALLY_BOOKED" | "FULL" | "BREAK" | "LEAVE" | "HOLIDAY" | "UNAVAILABLE" | "CONFLICTED";

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
  timeState: "PAST" | "CURRENT" | "FUTURE";
  past: boolean;
  current: boolean;
  bookable: boolean;
  notBookableReason: string | null;
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

export type ConsultationAiSummary = {
  consultationId: string;
  summary: string | null;
  provider: string | null;
  model: string | null;
  generatedAt: string | null;
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

export type ConsultationAiSummaryInput = {
  summary: string;
  provider: string | null;
  model: string | null;
  generatedAt: string | null;
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

export type BillStatus = "DRAFT" | "UNPAID" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "REFUND_PENDING" | "PARTIALLY_REFUNDED" | "REFUNDED" | "CANCELLED" | "CANCELLED_REFUNDED";
export type BillItemType = "CONSULTATION" | "MEDICINE" | "TEST" | "VACCINATION" | "PROCEDURE" | "OTHER";
export type PaymentMode = "CASH" | "CARD" | "UPI" | "INSURANCE" | "PAYTM" | "PHONEPE" | "GOOGLE_PAY" | "BANK_TRANSFER" | "CHEQUE" | "OTHER";
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

export type PendingConsultationFee = {
  appointmentId: string;
  appointmentDate: string;
  appointmentTime: string | null;
  doctorUserId: string | null;
  doctorName: string | null;
  consultationFee: number;
  dueAmount: number;
  paymentBypassReason: string | null;
  paymentBypassedAt: string | null;
};

export type PatientBillingContext = {
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  billDueAmount: number;
  pendingConsultationFeeAmount: number;
  totalDueAmount: number;
  billCount: number;
  pendingConsultationFees: PendingConsultationFee[];
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
  lines: Array<
    Omit<BillLine, "id" | "totalPrice"> & {
      unitPrice: number;
      quantity: number;
      referenceId: string | null;
      lineDiscountAmount?: number | null;
      batchNumber?: string | null;
      dispensationReferenceId?: string | null;
    }
  >;
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
  receivedByLabel?: string | null;
  receiptId: string | null;
  receiptNumber: string | null;
  receiptDate: string | null;
  createdAt: string;
};

export type LabOrderPaymentReceipt = {
  receiptId: string | null;
  receiptNumber: string | null;
  billId: string | null;
  billNumber: string | null;
  amount: number | null;
  paymentMode: PaymentMode | null;
  referenceNumber: string | null;
  collectedBy: string | null;
  collectedAt: string | null;
  printUrl: string | null;
  downloadUrl: string | null;
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
  receivedByLabel?: string | null;
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
  billRefundableAmount: number;
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

export type ConsultationFeePaymentInput = {
  appointmentId: string;
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
  receivedByLabel?: string | null;
  createdAt: string;
};

export type PharmacyPosMedicine = {
  medicineId: string;
  medicineName: string;
  genericName: string | null;
  brandName: string | null;
  barcode: string | null;
  qrCode: string | null;
  externalCode: string | null;
  totalAvailableQuantity: number;
  defaultUnitPrice: number;
  taxRate: number;
  earliestExpiryDate: string | null;
};

export type PharmacyPosBatch = {
  stockBatchId: string;
  medicineId: string;
  batchNumber: string | null;
  expiryDate: string | null;
  availableQuantity: number;
  unitPrice: number;
  expired: boolean;
  locationName: string | null;
};

export type PharmacyPosSaleItem = {
  id: string;
  medicineId: string;
  medicineName: string;
  stockBatchId: string | null;
  batchNumber: string | null;
  expiryDate: string | null;
  quantity: number;
  returnedQuantity: number;
  unitPrice: number;
  discount: number;
  tax: number;
  lineTotal: number;
};

export type PharmacyPosSalePayment = {
  id: string;
  cashierShiftId: string | null;
  amount: number;
  paymentMode: PaymentMode;
  referenceNumber: string | null;
  receiptNumber: string;
  paymentDate: string;
  paymentDateTime: string | null;
  createdAt: string;
};

export type PharmacyPosSaleReturn = {
  id: string;
  returnNumber: string;
  saleItemId: string;
  medicineId: string;
  stockBatchId: string | null;
  quantity: number;
  grossAmount: number;
  discountAmount: number;
  taxAmount: number;
  refundAmount: number;
  reusable: boolean;
  reason: string;
  refundMode: PaymentMode | null;
  referenceNumber: string | null;
  notes: string | null;
  createdAt: string;
};

export type PharmacyPosSale = {
  id: string;
  saleNumber: string;
  patientId: string | null;
  patientName: string | null;
  customerName: string | null;
  customerMobile: string | null;
  prescriptionDocumentId: string | null;
  prescriptionFileName: string | null;
  prescriptionUploadedAt: string | null;
  saleDateTime: string;
  subtotal: number;
  discount: number;
  tax: number;
  total: number;
  paidAmount: number;
  dueAmount: number;
  status: string;
  notes: string | null;
  fefoExplanation: string;
  createdAt: string;
  items: PharmacyPosSaleItem[];
  payments: PharmacyPosSalePayment[];
  returns: PharmacyPosSaleReturn[];
};

export type PharmacyPosCreateSaleInput = {
  patientId: string | null;
  customerName: string | null;
  customerMobile: string | null;
  prescriptionDocumentId?: string | null;
  saleDateTime?: string | null;
  discount?: number | null;
  tax?: number | null;
  paidAmount?: number | null;
  paymentMode?: PaymentMode | null;
  paymentReference?: string | null;
  paymentNotes?: string | null;
  notes?: string | null;
  items: Array<{
    medicineId: string;
    quantity: number;
    unitPrice?: number | null;
    discount?: number | null;
    tax?: number | null;
  }>;
};

export type PharmacyPosPrescriptionUpload = {
  documentId: string;
  fileName: string;
  mediaType: string;
  sizeBytes: number;
  uploadedAt: string;
};

export type PharmacyPosPrescriptionDownloadUrl = {
  url: string;
  expiresInSeconds: string;
};

export type PharmacyPosPaymentInput = {
  amount: number;
  paymentMode: PaymentMode;
  referenceNumber?: string | null;
  notes?: string | null;
  paymentDate?: string | null;
  paymentDateTime?: string | null;
};

export type PharmacyPosReturnInput = {
  reason: string;
  refundMode?: PaymentMode | null;
  referenceNumber?: string | null;
  notes?: string | null;
  items: Array<{
    saleItemId: string;
    quantity: number;
    reusable: boolean;
  }>;
};

export type PharmacyPosShift = {
  id: string;
  cashierUserId: string;
  openedAt: string;
  openedBy: string;
  openingCashAmount: number;
  closedAt: string | null;
  closedBy: string | null;
  status: "OPEN" | "CLOSED" | "CANCELLED";
  expectedCashAmount: number;
  expectedUpiAmount: number;
  expectedCardAmount: number;
  expectedOtherAmount: number;
  expectedTotalAmount: number;
  actualCashAmount: number;
  actualUpiAmount: number;
  actualCardAmount: number;
  actualOtherAmount: number;
  actualTotalAmount: number;
  varianceAmount: number;
  openNotes: string | null;
  closeNotes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PharmacyPosOpenShiftInput = {
  openingCashAmount?: number | null;
  notes?: string | null;
};

export type PharmacyPosCloseShiftInput = {
  actualCashAmount?: number | null;
  actualUpiAmount?: number | null;
  actualCardAmount?: number | null;
  actualOtherAmount?: number | null;
  closeNotes?: string | null;
};

export type VaccineMaster = {
  id: string;
  tenantId: string;
  vaccineName: string;
  description: string | null;
  manufacturer: string | null;
  brandName: string | null;
  vaccineGroup: string | null;
  doseNumber: number | null;
  route: string | null;
  administrationSite: string | null;
  storageTemperature: string | null;
  ndcBarcode: string | null;
  inventoryItemId: string | null;
  inventoryItemCode: string | null;
  stockTrackingEnabled: boolean;
  scheduleType: string | null;
  ageGroup: string | null;
  minAgeDays: number | null;
  recommendedAgeDays: number | null;
  maxAgeDays: number | null;
  recommendedGapDays: number | null;
  gapDays: number | null;
  boosterGapDays: number | null;
  boosterRules: string | null;
  recurring: boolean;
  recurrenceDays: number | null;
  recommendationPolicy: string | null;
  catchUpPolicy: string | null;
  catchUpMaxAgeDays: number | null;
  applicableAgeGroup: string | null;
  clinicalIndications: string | null;
  defaultPrice: number | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type VaccineInput = {
  vaccineName: string;
  description: string | null;
  manufacturer: string | null;
  brandName: string | null;
  vaccineGroup: string | null;
  doseNumber: number | null;
  route: string | null;
  administrationSite: string | null;
  storageTemperature: string | null;
  ndcBarcode: string | null;
  inventoryItemId: string | null;
  inventoryItemCode: string | null;
  stockTrackingEnabled: boolean;
  scheduleType: string | null;
  ageGroup: string | null;
  minAgeDays: number | null;
  recommendedAgeDays: number | null;
  maxAgeDays: number | null;
  gapDays: number | null;
  recommendedGapDays: number | null;
  boosterGapDays: number | null;
  boosterRules: string | null;
  recurring: boolean;
  recurrenceDays: number | null;
  recommendationPolicy: string | null;
  catchUpPolicy: string | null;
  catchUpMaxAgeDays: number | null;
  applicableAgeGroup: string | null;
  clinicalIndications: string | null;
  defaultPrice: number | null;
  active: boolean;
};

export type VaccinationRecommendation = {
  vaccineId: string;
  vaccineName: string;
  brandName: string | null;
  manufacturer: string | null;
  vaccineGroup: string | null;
  doseNumber: number | null;
  route: string | null;
  administrationSite: string | null;
  scheduleType: string | null;
  dueDate: string | null;
  status: "DUE" | "OVERDUE" | "UPCOMING" | "COMPLETED" | "NOT_APPLICABLE" | "OPTIONAL_RISK_BASED";
  overdueDays: number | null;
  recommendedAgeDays: number | null;
  patientAgeDays: number | null;
  patientAgeGroup?: string | null;
  reasonText: string;
  completedDate: string | null;
};

export type VaccinationRecommendationSummary = {
  patientId: string;
  scheduleType: string;
  recommendedToday: VaccinationRecommendation[];
  overdue: VaccinationRecommendation[];
  upcoming: VaccinationRecommendation[];
  completed: VaccinationRecommendation[];
  optionalRiskBased: VaccinationRecommendation[];
  notApplicable: VaccinationRecommendation[];
};

export type VaccineCsvImportRowResult = {
  rowNumber: number;
  vaccineName: string;
  status: string;
  message: string;
};

export type VaccineCsvImportResponse = {
  totalRows: number;
  createdCount: number;
  failedCount: number;
  rows: VaccineCsvImportRowResult[];
};

export type LabTestCategory =
  | "Hematology"
  | "Biochemistry"
  | "Microbiology"
  | "Pathology"
  | "Radiology"
  | "Cardiology"
  | "Immunology"
  | "Serology"
  | "Endocrinology"
  | "Virology"
  | "Molecular"
  | "Cytology"
  | "Histopathology"
  | "Other";

export type LabTestInput = {
  testCode: string;
  testName: string;
  category: LabTestCategory | string;
  department: string | null;
  sampleType: string | null;
  unit: string | null;
  referenceRange: string | null;
  turnaroundTime: string | null;
  price: number;
  active: boolean;
  parameters: LabTestParameterInput[];
};

export type LabTest = LabTestInput & {
  id: string;
  tenantId: string;
  enabled: boolean;
  tenantPriceOverride: number | null;
  tenantTatOverride: string | null;
  displayOrder: number | null;
  createdAt: string;
  updatedAt: string;
  parameters: LabTestParameter[];
};

export type LabCategoryConfig = {
  categoryCode: string;
  displayName: string;
  active: boolean;
  displayOrder: number | null;
};

export type LabCategoryConfigInput = {
  displayName?: string | null;
  active?: boolean | null;
  displayOrder?: number | null;
};

export type LabTestCatalogueConfig = {
  id: string;
  tenantId: string;
  testCode: string;
  testName: string;
  category: string;
  enabled: boolean;
  active: boolean;
  price: number | null;
  turnaroundTime: string | null;
  tenantPriceOverride: number | null;
  tenantTatOverride: string | null;
  displayOrder: number | null;
};

export type LabTestCatalogueConfigInput = {
  enabled?: boolean | null;
  active?: boolean | null;
  tenantPriceOverride?: number | null;
  tenantTatOverride?: string | null;
  displayOrder?: number | null;
};

export type LabTestParameterInput = {
  parameterName: string;
  unit: string | null;
  normalRange: string | null;
  criticalRange: string | null;
  sortOrder: number;
};

export type LabTestParameter = {
  id: string;
  labTestId: string;
  parameterName: string;
  unit: string | null;
  normalRange: string | null;
  criticalRange: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type LabTestCsvImportRowError = {
  rowNumber: number;
  message: string;
};

export type LabTestCsvImportResult = {
  totalRows: number;
  createdCount: number;
  updatedCount: number;
  failedCount: number;
  rowErrors: LabTestCsvImportRowError[];
};

export type LabOrderStatus =
  | "ORDERED"
  | "PAYMENT_PENDING"
  | "PAID"
  | "READY_FOR_COLLECTION"
  | "SAMPLE_COLLECTED"
  | "PROCESSING"
  | "RESULT_ENTERED"
  | "REPORT_READY"
  | "REPORT_GENERATED"
  | "DOCTOR_REVIEWED"
  | "DELIVERED"
  | "CANCELLED";

export type LabSampleStatus =
  | "PENDING_COLLECTION"
  | "COLLECTED"
  | "RECEIVED"
  | "REJECTED"
  | "RECOLLECTION_REQUIRED"
  | "CANCELLED";

export type LabOrderOrigin =
  | "CONSULTATION"
  | "WALK_IN"
  | "DOCTOR_REFERRAL"
  | "HEALTH_PACKAGE"
  | "CORPORATE"
  | "HOME_COLLECTION"
  | "FOLLOW_UP";

export type LabOrderItem = {
  id: string;
  labTestId: string | null;
  testCode: string;
  testName: string;
  category: string;
  department: string | null;
  sampleType: string | null;
  unit: string | null;
  referenceRange: string | null;
  turnaroundTime: string | null;
  price: number;
  sortOrder: number;
  createdAt: string;
  parameters: LabTestParameter[];
};

export type LabOrderResult = {
  id: string;
  labOrderId: string;
  labOrderItemId: string;
  testCode: string;
  testName: string;
  parameterName: string | null;
  componentName: string | null;
  resultValue: string | null;
  unit: string | null;
  referenceRange: string | null;
  sortOrder: number | null;
  resultFlag: string | null;
  criticalResult: boolean;
  createdAt: string;
  updatedAt: string;
};

export type LabOrderAttachment = {
  id: string;
  labOrderId: string;
  attachmentType: string;
  originalFilename: string;
  mediaType: string;
  storageKey: string | null;
  sizeBytes: number | null;
  checksumSha256: string | null;
  dicomMetadataJson: string | null;
  uploadedByUserId: string | null;
  createdAt: string;
};

export type LabSample = {
  id: string;
  labOrderId: string;
  labOrderItemId: string | null;
  accessionNumber: string;
  barcodeValue: string;
  specimenType: string;
  containerType: string | null;
  status: LabSampleStatus;
  collectedAt: string | null;
  collectedBy: string | null;
  receivedAt: string | null;
  receivedBy: string | null;
  rejectionReason: string | null;
  recollectionRequired: boolean;
  notes: string | null;
};

export type LabOrder = {
  id: string;
  tenantId: string;
  orderNumber: string;
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  doctorUserId: string | null;
  doctorName: string | null;
  consultationId: string | null;
  orderOrigin: LabOrderOrigin;
  requestedByInternalDoctorId: string | null;
  externalDoctorName: string | null;
  externalDoctorMobile: string | null;
  externalClinicName: string | null;
  referralSource: string | null;
  notes: string | null;
  status: LabOrderStatus;
  orderedAt: string;
  billId: string | null;
  billNumber: string | null;
  billStatus: BillStatus | null;
  billTotalAmount: number | null;
  billDueAmount: number | null;
  externalLabVendor: string | null;
  externalReferenceNumber: string | null;
  deliveredAt: string | null;
  deliveredByUserId: string | null;
  paymentCollectedAt: string | null;
  readyForCollectionAt: string | null;
  sampleAccessionNumber: string | null;
  sampleBarcodeValue: string | null;
  sampleSummaryStatus: LabSampleStatus | null;
  sampleType: string | null;
  sampleCollectedAt: string | null;
  sampleCollectedByUserId: string | null;
  sampleCollectedBy: string | null;
  sampleCollectionNotes: string | null;
  processingStartedAt: string | null;
  resultEnteredAt: string | null;
  resultComments: string | null;
  reportGeneratedAt: string | null;
  reportGeneratedByUserId: string | null;
  reportGeneratedBy: string | null;
  reportFilename: string | null;
  reportPublishedAt: string | null;
  reportPublishedByUserId: string | null;
  reportDeliveryStatus: string | null;
  reportDeliveryChannels: string[];
  reportDeliveryNotes: string | null;
  reportDeliveryHistory: LabReportDeliveryEvent[];
  doctorReviewedAt: string | null;
  doctorReviewedByUserId: string | null;
  doctorReviewedBy: string | null;
  doctorReviewDecision: string | null;
  doctorReviewReason: string | null;
  doctorComments: string | null;
  labVerifiedAt: string | null;
  labVerifiedBy: string | null;
  labVerifiedByName: string | null;
  labVerificationDecision: string | null;
  labVerificationComments: string | null;
  labVerificationReason: string | null;
  paymentId?: string | null;
  receiptId?: string | null;
  receiptNumber?: string | null;
  receiptDate?: string | null;
  paymentDate?: string | null;
  paymentDateTime?: string | null;
  paymentAmount?: number | null;
  paymentMode?: PaymentMode | null;
  referenceNumber?: string | null;
  receivedBy?: string | null;
  paymentReceipt?: LabOrderPaymentReceipt | null;
  attachments: LabOrderAttachment[];
  items: LabOrderItem[];
  samples: LabSample[];
  results: LabOrderResult[];
  createdAt: string;
  updatedAt: string;
};

export type LabReportDeliveryEvent = {
  action: string;
  label: string;
  channel: string | null;
  occurredAt: string;
  actorAppUserId: string | null;
  summary: string | null;
};

export type LabOrderReportDeliveryActionInput = {
  action: string;
  channel?: string | null;
  notes?: string | null;
};

export type PatientVaccination = {
  id: string;
  tenantId: string;
  patientId: string;
  patientNumber: string | null;
  patientName: string | null;
  patientMobile?: string | null;
  patientAgeYears?: number | null;
  patientGender?: string | null;
  patientAllergies?: string | null;
  vaccineId: string | null;
  vaccineName: string;
  source?: "INTERNAL" | "EXTERNAL" | string;
  externalPlace?: string | null;
  proofDocumentId?: string | null;
  verifiedStatus?: "UNVERIFIED" | "VERIFIED" | "REJECTED" | string;
  verifiedByUserId?: string | null;
  verifiedByUserName?: string | null;
  verifiedAt?: string | null;
  doseNumber: number | null;
  givenDate: string;
  nextDueDate: string | null;
  batchNumber: string | null;
  stockBatchId?: string | null;
  notes: string | null;
  administeredByUserId: string | null;
  administeredByUserName: string | null;
  createdByUserId?: string | null;
  createdByUserName?: string | null;
  updatedByUserId?: string | null;
  updatedByUserName?: string | null;
  updatedAt?: string;
  billId?: string | null;
  billNumber?: string | null;
  billStatus?: string | null;
  billLineId?: string | null;
  inventoryTransactionId?: string | null;
  inventoryStockBatchId?: string | null;
  inventoryItemId?: string | null;
  inventoryItemCode?: string | null;
  inventoryBatchNumber?: string | null;
  inventoryBatchManufacturer?: string | null;
  inventoryBatchExpiryDate?: string | null;
  reminderNotificationId?: string | null;
  reminderQueuedAt?: string | null;
  reminderStatus?: string | null;
  adverseEventStatus?: "NONE" | "OBSERVED" | "REPORTED" | "RESOLVED" | string | null;
  adverseEventEventDateTime?: string | null;
  adverseEventOnsetTimeAfterVaccination?: string | null;
  adverseEventSeverity?: "MILD" | "MODERATE" | "SEVERE" | "SERIOUS" | string | null;
  adverseEventSymptoms?: string[] | null;
  adverseEventOtherSymptoms?: string | null;
  adverseEventActionTaken?: string | null;
  adverseEventTreatmentNotes?: string | null;
  adverseEventOutcome?: "ONGOING" | "RECOVERED" | "RECOVERED_WITH_SEQUELAE" | "UNKNOWN" | string | null;
  adverseEventFollowUpRequired?: boolean | null;
  adverseEventFollowUpDate?: string | null;
  adverseEventReportedToAuthority?: boolean | null;
  adverseEventReportReferenceNumber?: string | null;
  adverseEventNotes?: string | null;
  adverseEventFollowUpNotificationId?: string | null;
  adverseEventFollowUpQueuedAt?: string | null;
  workflowWarnings?: string[] | null;
  recordedByUserId?: string | null;
  recordedByUserName?: string | null;
  createdAt: string;
};

export type PatientVaccinationInput = {
  vaccineId: string | null;
  vaccineName?: string | null;
  doseNumber: number | null;
  givenDate: string | null;
  nextDueDate: string | null;
  batchNumber: string | null;
  notes: string | null;
  source?: "INTERNAL" | "EXTERNAL" | string | null;
  externalPlace?: string | null;
  proofDocumentId?: string | null;
  verifiedStatus?: "UNVERIFIED" | "VERIFIED" | "REJECTED" | string | null;
  administeredByUserId: string | null;
  billId: string | null;
  addToBill: boolean;
  billItemUnitPrice: number | null;
  inventoryOverride?: boolean;
};

export type PatientVaccinationAefiInput = {
  adverseEventStatus: "NONE" | "OBSERVED" | "REPORTED" | "RESOLVED" | string;
  eventDateTime: string | null;
  onsetTimeAfterVaccination: string | null;
  severity: "MILD" | "MODERATE" | "SEVERE" | "SERIOUS" | string | null;
  symptoms: string[];
  otherSymptoms: string | null;
  actionTaken: string | null;
  treatmentNotes: string | null;
  outcome: "ONGOING" | "RECOVERED" | "RECOVERED_WITH_SEQUELAE" | "UNKNOWN" | string | null;
  followUpRequired: boolean | null;
  followUpDate: string | null;
  reportedToAuthority: boolean | null;
  reportReferenceNumber: string | null;
  notes: string | null;
};

export type PatientVaccinationBillInput = {
  billId: string | null;
  createNewBill: boolean;
  billItemUnitPrice: number | null;
};

export type PatientVaccinationUpdateInput = {
  externalPlace: string | null;
  proofDocumentId: string | null;
  verifiedStatus: "UNVERIFIED" | "VERIFIED" | "REJECTED";
};

export type VaccinationCertificateInput = {
  certificateType: "CHILD_IMMUNIZATION" | "SCHOOL_VACCINATION" | "TRAVEL_VACCINATION" | "SINGLE_VACCINATION" | string;
  vaccinationId?: string | null;
};

export type VaccinationDocumentSendInput = {
  channel: "EMAIL" | "WHATSAPP" | "SMS" | string;
};

export type MedicineInput = {
  medicineName: string;
  medicineType: MedicineType;
  barcode: string | null;
  qrCode: string | null;
  externalCode: string | null;
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
  locationId: string | null;
  barcode: string | null;
  qrCode: string | null;
  externalCode: string | null;
  batchNumber: string | null;
  purchaseReferenceNumber: string | null;
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
  locationName: string | null;
  createdAt: string;
  updatedAt: string;
};

export type InventoryTransaction = {
  id: string;
  tenantId: string;
  medicineId: string;
  stockBatchId: string | null;
  locationId: string | null;
  targetLocationId: string | null;
  transactionType: InventoryTransactionType;
  quantity: number;
  beforeQuantity: number | null;
  afterQuantity: number | null;
  reason: string | null;
  referenceType: string | null;
  referenceId: string | null;
  createdBy: string | null;
  notes: string | null;
  createdAt: string;
  batchNumber: string | null;
  adjustedByName: string | null;
  businessReference: string | null;
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

export type MedicineImportRowResult = {
  rowNumber: number;
  medicineName: string;
  status: "CREATED" | "UPDATED" | "SKIPPED" | "FAILED" | string;
  message: string;
  medicineId: string | null;
  stockId: string | null;
};

export type MedicineImportResult = {
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  failed: number;
  rows: MedicineImportRowResult[];
  failedRowsCsv: string;
};

export type Supplier = {
  id: string;
  tenantId: string;
  supplierName: string;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  gstNumber: string | null;
  address: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type SupplierInput = {
  supplierName: string;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  gstNumber: string | null;
  address: string | null;
  notes: string | null;
  active: boolean;
};

export type InventoryLocation = {
  id: string;
  tenantId: string;
  locationName: string;
  locationCode: string | null;
  locationType: string;
  defaultLocation: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type InventoryLocationInput = {
  locationName: string;
  locationCode: string | null;
  locationType: string;
  defaultLocation: boolean;
  active: boolean;
};

export type InventoryTransferInput = {
  medicineId: string;
  stockBatchId: string | null;
  fromLocationId: string;
  toLocationId: string;
  quantity: number;
  reason: string | null;
};

export type OcrExtractionRow = {
  rowNumber: number;
  medicineCode: string | null;
  medicineName: string | null;
  batchNumber: string | null;
  physicalQuantity: number | null;
  expiryDate: string | null;
  notes: string | null;
  confidence: number | null;
  needsReview: boolean;
};

export type ReconciliationUploadResponse = {
  reconciliationId: string;
  fileName: string;
  mediaType: string | null;
  storageKey: string | null;
  extractionStatus: string | null;
  extractionProvider: string | null;
  extractionConfidence: number | null;
  extractedRows: OcrExtractionRow[];
};

export type PharmacyReconciliation = {
  id: string;
  tenantId: string;
  medicineId: string;
  medicineName: string | null;
  stockBatchId: string | null;
  batchNumber: string | null;
  supplierId: string | null;
  supplierName: string | null;
  systemQuantity: number;
  physicalQuantity: number | null;
  varianceQuantity: number | null;
  reason: string | null;
  status: string;
  createdBy: string | null;
  submittedBy: string | null;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewDecision: string | null;
  reviewReason: string | null;
  postedBy: string | null;
  postedAt: string | null;
  adjustedBy: string | null;
  sheetDocumentId: string | null;
  sheetFilename: string | null;
  sheetMediaType: string | null;
  sheetStorageKey: string | null;
  extractionStatus: string | null;
  extractionProvider: string | null;
  extractionConfidence: number | null;
  extractedRows: OcrExtractionRow[];
  locationId: string | null;
  locationName: string | null;
  confirmedAt: string | null;
  reviewedAt: string | null;
  appliedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PharmacyReconciliationInput = {
  medicineId: string;
  stockBatchId: string | null;
  supplierId: string | null;
  locationId: string | null;
  physicalQuantity: number | null;
  reason: string | null;
};

export type PharmacyReconciliationConfirmInput = {
  physicalQuantity: number | null;
  reason: string | null;
  adjustStock: boolean;
};

export type PharmacyReconciliationDecisionInput = {
  reason: string | null;
};

export type PharmacyReconciliationPostInput = {
  physicalQuantity: number | null;
  reason: string | null;
};

export type StockInwardInput = {
  medicineId: string;
  supplierId: string | null;
  locationId: string | null;
  purchaseReferenceNumber: string | null;
  batchNumber: string | null;
  barcode: string | null;
  qrCode: string | null;
  externalCode: string | null;
  expiryDate: string | null;
  purchaseDate: string | null;
  quantity: number;
  unitCost: number | null;
  sellingPrice: number | null;
  lowStockThreshold: number | null;
};

export type ProcurementLineInput = {
  medicineId: string | null;
  medicineName: string | null;
  quantity: number;
  expectedUnitCost: number | null;
  unitCost: number | null;
  taxPercent: number | null;
  batchNumber: string | null;
  expiryDate: string | null;
  sellingPrice: number | null;
  unit?: string | null;
  locationId?: string | null;
  remarks?: string | null;
};

export type PurchaseOrderInput = {
  supplierId: string;
  poNumber: string;
  orderDate: string;
  expectedDeliveryDate: string | null;
  items: ProcurementLineInput[];
  approvalNote: string | null;
};

export type PurchaseOrder = {
  id: string;
  tenantId: string;
  supplierId: string;
  supplierName: string | null;
  poNumber: string;
  orderDate: string;
  expectedDeliveryDate: string | null;
  itemsJson: string;
  matchingStatus: string;
  varianceSummary: string | null;
  approvalNote: string | null;
  createdAt: string;
  updatedAt: string;
};

export type SupplierInvoiceInput = {
  supplierId: string;
  purchaseOrderId: string | null;
  invoiceNumber: string;
  invoiceDate: string;
  invoiceAmount: number;
  taxAmount: number | null;
  discountAmount: number | null;
  totalAmount: number | null;
  items: ProcurementLineInput[];
  varianceReason: string | null;
  approvalNote: string | null;
};

export type SupplierInvoiceStatus = "DRAFT" | "MATCHED" | "READY_FOR_PAYMENT" | "APPROVED_FOR_PAYMENT" | "PAID" | "CANCELLED";

export type SupplierInvoice = {
  id: string;
  tenantId: string;
  supplierId: string;
  supplierName: string | null;
  purchaseOrderId: string | null;
  invoiceNumber: string;
  invoiceDate: string;
  invoiceAmount: number | null;
  taxAmount: number | null;
  discountAmount: number | null;
  totalAmount: number | null;
  status: SupplierInvoiceStatus;
  itemsJson: string;
  matchingStatus: string;
  varianceAmount: number | null;
  varianceReason: string | null;
  varianceSummary: string | null;
  cancelReason: string | null;
  attachmentFileName: string | null;
  attachmentMediaType: string | null;
  attachmentSizeBytes: number | null;
  approvalNote: string | null;
  createdAt: string;
  updatedAt: string;
};

export type SupplierInvoiceAttachment = {
  invoiceId: string;
  fileName: string;
  mediaType: string;
  sizeBytes: number | null;
};

export type GoodsReceiptInput = {
  supplierId: string;
  purchaseOrderId: string | null;
  supplierInvoiceId: string | null;
  receiptNumber: string;
  receivedAt: string;
  locationId: string;
  items: ProcurementLineInput[];
  approvalNote: string | null;
};

export type GoodsReceipt = {
  id: string;
  tenantId: string;
  supplierId: string;
  supplierName: string | null;
  purchaseOrderId: string | null;
  supplierInvoiceId: string | null;
  receiptNumber: string;
  receivedAt: string;
  locationId: string;
  locationName: string | null;
  itemsJson: string;
  matchingStatus: string;
  varianceSummary: string | null;
  approvalNote: string | null;
  confirmedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PharmacyDashboard = {
  medicinesCount: number;
  stockBatchesCount: number;
  lowStockCount: number;
  expiredCount: number;
  nearExpiryCount: number;
  pendingDispensingCount: number;
  partiallyDispensedCount: number;
  todayDispensedCount: number;
  recentStockMovements: InventoryTransaction[];
};

export type FastMovingMedicine = {
  medicineId: string;
  medicineName: string | null;
  dispensedQuantity: number;
  availableQuantity: number;
};

export type PharmacyAnalytics = {
  fastMovingMedicines: FastMovingMedicine[];
  lowStockMedicines: LowStockItem[];
  expiryRiskMedicines: Stock[];
  stockValueEstimate: number;
};

export type SubstituteSuggestion = {
  medicineId: string;
  medicineName: string;
  genericName: string | null;
  brandName: string | null;
  dosageForm: string | null;
  strength: string | null;
  availableQuantity: number;
  availabilityStatus: string;
  expiryStatus: string;
  nearestExpiryDate: string | null;
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

export type HelpPageStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type HelpContentStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type HelpSectionType =
  | "DESCRIPTION"
  | "WORKFLOW"
  | "FIELD_TABLE"
  | "VALIDATION_RULES"
  | "REPORT_TYPES"
  | "FILTERS"
  | "EXPORT_CSV"
  | "PERMISSIONS"
  | "COMMON_ERRORS"
  | "COMMON_ISSUES"
  | "BEST_PRACTICES"
  | "FAQ"
  | "RELATED_PAGES"
  | "VIDEOS"
  | "IMAGES"
  | "LINKS"
  | "AUDIT"
  | "ROLES"
  | "TIPS"
  | "KNOWN_LIMITATIONS";

export type HelpAttachmentRecord = {
  id: string;
  type: "IMAGE" | "VIDEO" | "PDF" | "LINK";
  url: string;
  displayOrder: number | null;
};

export type HelpContentRecord = {
  id: string;
  languageCode: string;
  contentJson: string;
  version: number;
  status: HelpContentStatus | string;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
};

export type HelpSectionRecord = {
  id: string;
  sectionKey: string;
  sectionType: HelpSectionType | string;
  displayOrder: number;
  collapsible: boolean;
  active: boolean;
  contentJson: string | null;
  contentLanguageCode: string | null;
  contentVersion: number | null;
  contentStatus: string | null;
  attachments: HelpAttachmentRecord[];
  contents: HelpContentRecord[];
};

export type HelpPageRecord = {
  id: string;
  moduleKey: string;
  pageKey: string;
  title: string;
  icon: string | null;
  status: HelpPageStatus | string;
  version: number;
  active: boolean;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
  availableVersions: number[];
  sections: HelpSectionRecord[];
};

export type HelpPageSummary = {
  id: string;
  moduleKey: string;
  pageKey: string;
  title: string;
  icon: string | null;
  status: HelpPageStatus | string;
  version: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type HelpSearchResult = {
  pageKey: string;
  pageTitle: string;
  moduleKey: string;
  sectionKey: string;
  sectionType: string;
  snippet: string;
  languageCode: string;
};

export type HelpSectionUpsertInput = {
  sectionKey: string;
  sectionType: string;
  displayOrder: number | null;
  collapsible: boolean;
  active: boolean;
  contentJson: string;
};

export type HelpPageUpsertInput = {
  moduleKey: string;
  pageKey: string;
  title: string;
  icon: string | null;
  status: HelpPageStatus | string;
  active: boolean;
  sections: HelpSectionUpsertInput[];
};

export type HelpPageLifecycleInput = {
  pageKey: string;
  version: number | null;
};

function isDevHelpLoggingEnabled(): boolean {
  return import.meta.env?.DEV ?? true;
}

function logHelpRequest(message: string, details: Record<string, unknown>) {
  if (isDevHelpLoggingEnabled()) {
    console.info(message, details);
  }
}

function logHelpResponse(message: string, details: Record<string, unknown>) {
  if (isDevHelpLoggingEnabled()) {
    console.info(message, details);
  }
}

export async function getClinicProfile(token: string, tenantId: string) {
  return httpGet<ClinicProfile>("/api/clinic/profile", { token, tenantId });
}

export async function listHelpPages(token?: string | null, tenantId?: string | null) {
  return httpGet<HelpPageSummary[]>("/api/platform/help/pages", {
    ...buildHelpRequestOptions(token),
    tenantId: tenantId ?? null,
  });
}

async function fetchHelpPage(pageKey: string, lang: string, token?: string | null, tenantId?: string | null) {
  const endpoint = `/api/help/page/${pageKey}?lang=${encodeURIComponent(lang)}`;
  const requestOptions = {
    ...buildHelpRequestOptions(token),
    tenantId: tenantId ?? null,
  };
  logHelpRequest("[help] calling help API endpoint", {
    endpoint,
    pageKey,
    lang,
    hasToken: Boolean(requestOptions.token),
    tenantId: tenantId || null,
  });
  try {
    const response = await httpGet<HelpPageRecord>(endpoint, requestOptions);
    logHelpResponse("[help] help API status", {
      endpoint,
      status: 200,
      pageKey: response.pageKey,
      sections: response.sections.length,
      sectionKeys: response.sections.map((section) => section.sectionKey),
    });
    return response;
  } catch (error) {
    if (isDevHelpLoggingEnabled()) {
      console.warn("[help] help API request failed", {
        endpoint,
        pageKey,
        lang,
        error,
      });
    }
    throw error;
  }
}

export async function getHelpPage(pageKey: string, lang = "en", token?: string | null, tenantId?: string | null) {
  if (!pageKey) {
    throw new Error("Missing help page key.");
  }
  return fetchHelpPage(pageKey, lang, token, tenantId);
}

export async function getHelpPageByKey(pageKey: string, lang = "en", token?: string | null, tenantId?: string | null) {
  return getHelpPage(pageKey, lang, token, tenantId);
}

export async function getHelpPageAdmin(token?: string | null, tenantId?: string | null, pageKey?: string) {
  if (!pageKey) {
    throw new Error("Missing help page key.");
  }
  return httpGet<HelpPageRecord>(`/api/platform/help/page/${pageKey}`, {
    ...buildHelpRequestOptions(token),
    tenantId: tenantId ?? null,
  });
}

async function fetchHelpSearch(query: string, lang: string, token?: string | null, tenantId?: string | null) {
  const suffix = query ? `?q=${encodeURIComponent(query)}&lang=${encodeURIComponent(lang)}` : `?lang=${encodeURIComponent(lang)}`;
  const endpoint = `/api/help/search${suffix}`;
  const requestOptions = {
    ...buildHelpRequestOptions(token),
    tenantId: tenantId ?? null,
  };
  logHelpRequest("[help] calling help search endpoint", {
    endpoint,
    query: query || "",
    lang,
    hasToken: Boolean(requestOptions.token),
    tenantId: tenantId || null,
  });
  try {
    const response = await httpGet<HelpSearchResult[]>(endpoint, requestOptions);
    logHelpResponse("[help] help search status", {
      endpoint,
      status: 200,
      results: response.length,
      pageKeys: Array.from(new Set(response.map((item) => item.pageKey))).slice(0, 5),
    });
    return response;
  } catch (error) {
    if (isDevHelpLoggingEnabled()) {
      console.warn("[help] help search failed", {
        endpoint,
        query: query || "",
        lang,
        error,
      });
    }
    throw error;
  }
}

export async function searchHelp(query: string, lang = "en", token?: string | null, tenantId?: string | null) {
  return fetchHelpSearch(query, lang, token, tenantId);
}

export async function searchHelpPages(token?: string | null, tenantId?: string | null, query?: string, lang = "en") {
  return fetchHelpSearch(query || "", lang, token, tenantId);
}

export async function createHelpPage(token: string, tenantId: string, body: HelpPageUpsertInput) {
  return httpPost<HelpPageRecord>("/api/platform/help/page", body, { token, tenantId });
}

export async function updateHelpPage(token: string, tenantId: string, body: HelpPageUpsertInput) {
  return httpPut<HelpPageRecord>("/api/platform/help/page", body, { token, tenantId });
}

export async function publishHelpPage(token: string, tenantId: string, body: HelpPageLifecycleInput) {
  return httpPost<HelpPageRecord>("/api/platform/help/publish", body, { token, tenantId });
}

export async function archiveHelpPage(token: string, tenantId: string, body: HelpPageLifecycleInput) {
  return httpPost<HelpPageRecord>("/api/platform/help/archive", body, { token, tenantId });
}

export async function rollbackHelpPage(token: string, tenantId: string, body: HelpPageLifecycleInput) {
  return httpPost<HelpPageRecord>("/api/platform/help/rollback", body, { token, tenantId });
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

export async function getTenantOnboardingStatus(token: string, tenantId: string) {
  return httpGet<TenantOnboardingStatus>("/api/clinic/onboarding", { token, tenantId });
}

export async function completeTenantOnboarding(token: string, tenantId: string) {
  return httpPost<TenantOnboardingStatus>("/api/clinic/onboarding/complete", {}, { token, tenantId });
}

export async function skipTenantOnboarding(token: string, tenantId: string) {
  return httpPost<TenantOnboardingStatus>("/api/clinic/onboarding/skip", {}, { token, tenantId });
}

export async function getDoctorProfile(token: string, tenantId: string, doctorUserId: string) {
  return httpGet<DoctorProfile>(`/api/doctors/${doctorUserId}/profile`, { token, tenantId });
}

export async function updateDoctorProfile(token: string, tenantId: string, doctorUserId: string, body: DoctorProfileInput) {
  return httpPut<DoctorProfile>(`/api/doctors/${doctorUserId}/profile`, body, { token, tenantId });
}

export async function updateDoctorProfileWithPhoto(
  token: string,
  tenantId: string,
  doctorUserId: string,
  body: DoctorProfileInput,
  file: File,
) {
  const formData = new FormData();
  formData.append("doctor", new Blob([JSON.stringify(body)], { type: "application/json" }));
  formData.append("photo", file);
  return httpPutForm<DoctorProfile>(`/api/doctors/${doctorUserId}/profile`, formData, { token, tenantId });
}

export async function uploadDoctorProfilePhoto(token: string, tenantId: string, doctorUserId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return httpPostForm<DoctorProfile>(`/api/doctors/${doctorUserId}/photo`, formData, { token, tenantId });
}

export async function createTenantUser(token: string, tenantId: string, body: {
  email: string;
  username?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  role: string;
  temporaryPassword?: string | null;
  employeeCode?: string | null;
  mobile?: string | null;
  department?: string | null;
  active: boolean;
}) {
  return httpPost<ClinicUser>("/api/tenant/users", {
    email: body.email,
    username: body.username ?? null,
    firstName: body.firstName ?? null,
    lastName: body.lastName ?? null,
    role: body.role,
    temporaryPassword: body.temporaryPassword ?? null,
    employeeCode: body.employeeCode ?? null,
    mobile: body.mobile ?? null,
    department: body.department ?? null,
    active: body.active,
  }, { token, tenantId });
}

export async function updateTenantUser(token: string, tenantId: string, appUserId: string, body: {
  active: boolean;
  role?: string | null;
}) {
  return httpPut<ClinicUser>(`/api/tenant/users/${appUserId}`, body, { token, tenantId });
}

export async function updateTenantUserProfile(token: string, tenantId: string, appUserId: string, body: {
  displayName: string;
  employeeCode?: string | null;
  mobile?: string | null;
  department?: string | null;
  role?: string | null;
  active: boolean;
}) {
  return httpPut<ClinicUser>(`/api/tenant/users/${appUserId}/profile`, {
    displayName: body.displayName,
    employeeCode: body.employeeCode ?? null,
    mobile: body.mobile ?? null,
    department: body.department ?? null,
    role: body.role ?? null,
    active: body.active,
  }, { token, tenantId });
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

export async function updateAppointmentStatus(
  token: string,
  tenantId: string,
  id: string,
  status: AppointmentStatus,
  comment?: string | null,
  options?: {
    paymentBypassReason?: string | null;
    paymentBypassNotes?: string | null;
  },
) {
  return httpPatch<Appointment>(
    `/api/appointments/${id}/status`,
    {
      status,
      comment: comment || null,
      paymentBypassReason: options?.paymentBypassReason || null,
      paymentBypassNotes: options?.paymentBypassNotes || null,
    },
    { token, tenantId },
  );
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
  const browserTimeZone = typeof Intl !== "undefined" ? Intl.DateTimeFormat().resolvedOptions().timeZone || null : null;
  return httpGet<DoctorAvailabilitySlot[]>(`/api/doctors/${doctorUserId}/slots?date=${encodeURIComponent(date)}`, {
    token,
    tenantId,
    clientTimeZone: browserTimeZone,
  });
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

export async function getConsultationAiSummary(token: string, tenantId: string, id: string) {
  return httpGet<ConsultationAiSummary>(`/api/consultations/${id}/ai-summary`, { token, tenantId });
}

export async function saveConsultationAiSummary(token: string, tenantId: string, id: string, body: ConsultationAiSummaryInput) {
  return httpPatch<ConsultationAiSummary>(`/api/consultations/${id}/ai-summary`, body, { token, tenantId });
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

export async function cancelPrescription(token: string, tenantId: string, id: string) {
  return httpPatch<Prescription>(`/api/prescriptions/${id}/cancel`, undefined, { token, tenantId });
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
    let message = `HTTP ${res.status}: ${res.statusText}`;
    const contentType = res.headers.get("content-type") || "";
    try {
      if (contentType.includes("application/json")) {
        const body = await res.json() as { message?: string };
        if (body?.message) {
          message = body.message;
        }
      } else {
        const text = (await res.text()).trim();
        if (text) {
          message = text;
        }
      }
    } catch {
      // Keep the fallback HTTP status message when the error body cannot be parsed.
    }
    if (res.status === 403) {
      throw new Error("You do not have permission to print this prescription.");
    }
    throw new Error(message);
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
  params: { patientId?: string; appointmentId?: string; status?: BillStatus | null; fromDate?: string; toDate?: string; paymentMode?: PaymentMode | null; search?: string } = {},
) {
  const query = new URLSearchParams();
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.appointmentId) query.set("appointmentId", params.appointmentId);
  if (params.status) query.set("status", params.status);
  if (params.fromDate) query.set("fromDate", params.fromDate);
  if (params.toDate) query.set("toDate", params.toDate);
  if (params.paymentMode) query.set("paymentMode", params.paymentMode);
  if (params.search) query.set("search", params.search);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<Bill[]>(`/api/bills${suffix}`, { token, tenantId });
}

export async function getPatientBillingContext(token: string, tenantId: string, patientId: string) {
  const query = new URLSearchParams();
  query.set("patientId", patientId);
  return httpGet<PatientBillingContext>(`/api/bills/patient-context?${query.toString()}`, { token, tenantId });
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

export async function collectConsultationFee(token: string, tenantId: string, body: ConsultationFeePaymentInput) {
  return httpPost<Payment>("/api/bills/consultation-fees", body, { token, tenantId });
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

export async function searchPharmacyPosMedicines(token: string, tenantId: string, query: string) {
  const suffix = query.trim() ? `?q=${encodeURIComponent(query.trim())}` : "";
  return httpGet<PharmacyPosMedicine[]>(`/api/pharmacy/pos/search-medicines${suffix}`, { token, tenantId });
}

export async function getPharmacyPosAvailableBatches(token: string, tenantId: string, medicineId: string) {
  return httpGet<PharmacyPosBatch[]>(`/api/pharmacy/pos/available-batches?medicineId=${encodeURIComponent(medicineId)}`, { token, tenantId });
}

export async function createPharmacyPosSale(token: string, tenantId: string, body: PharmacyPosCreateSaleInput) {
  return httpPost<PharmacyPosSale>("/api/pharmacy/pos/sales", body, { token, tenantId });
}

export async function uploadPharmacyPosPrescription(token: string, tenantId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return httpPostForm<PharmacyPosPrescriptionUpload>("/api/pharmacy/pos/prescriptions/upload", formData, { token, tenantId });
}

export async function getPharmacyPosPrescriptionDownloadUrl(token: string, tenantId: string, documentId: string) {
  return httpGet<PharmacyPosPrescriptionDownloadUrl>(`/api/pharmacy/pos/prescriptions/${documentId}/download-url`, { token, tenantId });
}

export async function listPharmacyPosSales(token: string, tenantId: string) {
  return httpGet<PharmacyPosSale[]>("/api/pharmacy/pos/sales", { token, tenantId });
}

export async function getPharmacyPosSale(token: string, tenantId: string, id: string) {
  return httpGet<PharmacyPosSale>(`/api/pharmacy/pos/sales/${id}`, { token, tenantId });
}

export async function addPharmacyPosPayment(token: string, tenantId: string, saleId: string, body: PharmacyPosPaymentInput) {
  return httpPost<PharmacyPosSale>(`/api/pharmacy/pos/sales/${saleId}/payment`, body, { token, tenantId });
}

export async function returnPharmacyPosSale(token: string, tenantId: string, saleId: string, body: PharmacyPosReturnInput) {
  return httpPost<PharmacyPosSale>(`/api/pharmacy/pos/sales/${saleId}/return`, body, { token, tenantId });
}

export async function getPharmacyPosReceiptPdf(token: string, tenantId: string, saleId: string) {
  const res = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/pharmacy/pos/sales/${saleId}/receipt`, {
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
    filename: match?.[1] || `sale-${saleId}-receipt.pdf`,
  };
}

export async function openPharmacyPosShift(token: string, tenantId: string, body: PharmacyPosOpenShiftInput) {
  return httpPost<PharmacyPosShift>("/api/pharmacy/pos/shifts/open", body, { token, tenantId });
}

export async function getCurrentPharmacyPosShift(token: string, tenantId: string) {
  return httpGet<PharmacyPosShift | null>("/api/pharmacy/pos/shifts/current", { token, tenantId });
}

export async function closePharmacyPosShift(token: string, tenantId: string, shiftId: string, body: PharmacyPosCloseShiftInput) {
  return httpPost<PharmacyPosShift>(`/api/pharmacy/pos/shifts/${shiftId}/close`, body, { token, tenantId });
}

export async function listPharmacyPosShifts(
  token: string,
  tenantId: string,
  params?: {
    dateFrom?: string;
    dateTo?: string;
    status?: string;
    cashier?: string;
  },
) {
  const query = new URLSearchParams();
  if (params?.dateFrom) query.set("dateFrom", params.dateFrom);
  if (params?.dateTo) query.set("dateTo", params.dateTo);
  if (params?.status) query.set("status", params.status);
  if (params?.cashier) query.set("cashier", params.cashier);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<PharmacyPosShift[]>(`/api/pharmacy/pos/shifts${suffix}`, { token, tenantId });
}

export async function getVaccines(token: string, tenantId: string) {
  return httpGet<VaccineMaster[]>("/api/vaccines", { token, tenantId });
}

export async function getVaccinationRecommendations(
  token: string,
  tenantId: string,
  patientId: string,
  scheduleType?: string | null,
) {
  const query = new URLSearchParams({ patientId });
  if (scheduleType) {
    query.set("scheduleType", scheduleType);
  }
  return httpGet<VaccinationRecommendationSummary>(`/api/vaccinations/recommendations?${query.toString()}`, { token, tenantId });
}

export async function createVaccine(token: string, tenantId: string, body: VaccineInput) {
  return httpPost<VaccineMaster>("/api/vaccines", body, { token, tenantId });
}

export async function getVaccineImportTemplate(token: string, tenantId: string) {
  return httpGetText("/api/vaccines/import-template", { token, tenantId, accept: "text/csv, */*" });
}

export async function exportVaccinesCsv(token: string, tenantId: string) {
  return httpGetText("/api/vaccines/export", { token, tenantId, accept: "text/csv, */*" });
}

export async function importVaccinesCsv(token: string, tenantId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return httpPostForm<VaccineCsvImportResponse>("/api/vaccines/import-csv", formData, { token, tenantId });
}

export async function getLabCategories(token: string, tenantId: string) {
  return httpGet<string[]>("/api/lab/categories", { token, tenantId });
}

export async function getLabCategoryConfig(token: string, tenantId: string) {
  return httpGet<LabCategoryConfig[]>("/api/lab/config/categories", { token, tenantId });
}

export async function updateLabCategoryConfig(token: string, tenantId: string, code: string, body: LabCategoryConfigInput) {
  return httpPut<LabCategoryConfig>(`/api/lab/config/categories/${encodeURIComponent(code)}`, body, { token, tenantId });
}

export async function getLabTests(token: string, tenantId: string, params: { search?: string; active?: boolean | null } = {}) {
  const query = new URLSearchParams();
  if (params.search) query.set("search", params.search);
  if (params.active !== undefined && params.active !== null) query.set("active", String(params.active));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<LabTest[]>(`/api/lab/tests${suffix}`, { token, tenantId });
}

export async function getLabTestConfig(token: string, tenantId: string) {
  return httpGet<LabTestCatalogueConfig[]>("/api/lab/config/tests", { token, tenantId });
}

export async function updateLabTestConfig(token: string, tenantId: string, id: string, body: LabTestCatalogueConfigInput) {
  return httpPut<LabTestCatalogueConfig>(`/api/lab/config/tests/${id}`, body, { token, tenantId });
}

export async function createLabTest(token: string, tenantId: string, body: LabTestInput) {
  return httpPost<LabTest>("/api/lab/tests", body, { token, tenantId });
}

export async function importLabTestsCsv(token: string, tenantId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return httpPostForm<LabTestCsvImportResult>("/api/lab/tests/import-csv", formData, { token, tenantId });
}

export async function getLabTestImportTemplate(token: string, tenantId: string) {
  return httpGetText("/api/lab/tests/import-template", { token, tenantId, accept: "text/csv, */*" });
}

export async function updateLabTest(token: string, tenantId: string, id: string, body: LabTestInput) {
  return httpPut<LabTest>(`/api/lab/tests/${id}`, body, { token, tenantId });
}

export async function deactivateLabTest(token: string, tenantId: string, id: string) {
  return httpPatch<LabTest>(`/api/lab/tests/${id}/deactivate`, undefined, { token, tenantId });
}

export async function getLabOrders(
  token: string,
  tenantId: string,
  params: { consultationId?: string | null; patientId?: string | null; doctorUserId?: string | null; status?: LabOrderStatus | null; search?: string | null } = {},
) {
  const query = new URLSearchParams();
  if (params.consultationId) query.set("consultationId", params.consultationId);
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.doctorUserId) query.set("doctorUserId", params.doctorUserId);
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<LabOrder[]>(`/api/lab/orders${suffix}`, { token, tenantId });
}

export async function getLabOrder(token: string, tenantId: string, id: string) {
  return httpGet<LabOrder>(`/api/lab/orders/${id}`, { token, tenantId });
}

export async function createConsultationLabOrder(token: string, tenantId: string, consultationId: string, body: { patientId: string; testIds: string[]; notes?: string | null }) {
  return httpPost<LabOrder>(`/api/lab/consultations/${consultationId}/orders`, body, { token, tenantId });
}

export async function createLabOrder(token: string, tenantId: string, body: {
  patientId: string;
  orderOrigin: LabOrderOrigin;
  requestedByInternalDoctorId?: string | null;
  externalDoctorName?: string | null;
  externalDoctorMobile?: string | null;
  externalClinicName?: string | null;
  referralSource?: string | null;
  testIds: string[];
  notes?: string | null;
}) {
  return httpPost<LabOrder>("/api/lab/orders", {
    patientId: body.patientId,
    orderOrigin: body.orderOrigin,
    requestedByInternalDoctorId: body.requestedByInternalDoctorId ?? null,
    externalDoctorName: body.externalDoctorName ?? null,
    externalDoctorMobile: body.externalDoctorMobile ?? null,
    externalClinicName: body.externalClinicName ?? null,
    referralSource: body.referralSource ?? null,
    testIds: body.testIds,
    notes: body.notes ?? null,
  }, { token, tenantId });
}

export async function collectLabOrderPayment(token: string, tenantId: string, id: string, body: {
  paymentDate?: string | null;
  paymentDateTime?: string | null;
  amount: number;
  paymentMode: PaymentMode;
  referenceNumber?: string | null;
  notes?: string | null;
  receivedBy?: string | null;
}) {
  return httpPost<LabOrder>(`/api/lab/orders/${id}/payments`, {
    paymentDate: body.paymentDate ?? null,
    paymentDateTime: body.paymentDateTime ?? null,
    amount: body.amount,
    paymentMode: body.paymentMode,
    referenceNumber: body.referenceNumber ?? null,
    notes: body.notes ?? null,
    receivedBy: body.receivedBy ?? null,
  }, { token, tenantId });
}

export async function collectLabOrderSample(token: string, tenantId: string, id: string, body: {
  sampleType?: string | null;
  collectedAt?: string | null;
  notes?: string | null;
}) {
  return httpPost<LabOrder>(`/api/lab/orders/${id}/sample-collection`, {
    sampleType: body.sampleType ?? null,
    collectedAt: body.collectedAt ?? null,
    notes: body.notes ?? null,
  }, { token, tenantId });
}

export async function getLabOrderSamples(token: string, tenantId: string, orderId: string) {
  return httpGet<LabSample[]>(`/api/lab/orders/${orderId}/samples`, { token, tenantId });
}

export async function collectLabOrderSamples(token: string, tenantId: string, orderId: string, body: {
  samples: Array<{
    labOrderItemId?: string | null;
    specimenType: string;
    containerType?: string | null;
    collectedAt?: string | null;
    notes?: string | null;
  }>;
}) {
  return httpPost<LabSample[]>(`/api/lab/orders/${orderId}/samples/collect`, {
    samples: body.samples.map((sample) => ({
      labOrderItemId: sample.labOrderItemId ?? null,
      specimenType: sample.specimenType,
      containerType: sample.containerType ?? null,
      collectedAt: sample.collectedAt ?? null,
      notes: sample.notes ?? null,
    })),
  }, { token, tenantId });
}

export async function receiveLabSample(token: string, tenantId: string, sampleId: string, body?: {
  receivedAt?: string | null;
  receivedBy?: string | null;
}) {
  return httpPost<LabSample>(`/api/lab/samples/${sampleId}/receive`, {
    receivedAt: body?.receivedAt ?? null,
    receivedBy: body?.receivedBy ?? null,
  }, { token, tenantId });
}

export async function rejectLabSample(token: string, tenantId: string, sampleId: string, body: {
  rejectionReason: string;
  recollectionRequired?: boolean;
  notes?: string | null;
}) {
  return httpPost<LabSample>(`/api/lab/samples/${sampleId}/reject`, {
    rejectionReason: body.rejectionReason,
    recollectionRequired: body.recollectionRequired ?? false,
    notes: body.notes ?? null,
  }, { token, tenantId });
}

export async function enterLabOrderResults(token: string, tenantId: string, id: string, body: {
  comments?: string | null;
  items: Array<{
    labOrderItemId: string;
    resultValue?: string | null;
    unit?: string | null;
    referenceRange?: string | null;
    componentResults?: Array<{
      parameterName?: string | null;
      componentName?: string | null;
      resultValue?: string | null;
      unit?: string | null;
      referenceRange?: string | null;
    }>;
  }>;
}) {
  return httpPost<LabOrder>(`/api/lab/orders/${id}/results`, body, { token, tenantId });
}

export async function reviewLabOrder(token: string, tenantId: string, id: string, body: { decision: "APPROVE" | "SEND_BACK"; reason?: string | null; comments?: string | null }) {
  return httpPost<LabOrder>(`/api/lab/orders/${id}/doctor-review`, { decision: body.decision, reason: body.reason ?? null, comments: body.comments ?? null }, { token, tenantId });
}

export async function verifyLabOrder(token: string, tenantId: string, id: string, body: { decision: "APPROVE" | "SEND_BACK"; reason?: string | null; comments?: string | null }) {
  return httpPost<LabOrder>(`/api/lab/orders/${id}/verify`, { decision: body.decision, reason: body.reason ?? null, comments: body.comments ?? null }, { token, tenantId });
}

export async function publishLabOrderReport(token: string, tenantId: string, id: string, body: {
  deliveryChannels?: string[];
  publishNotes?: string | null;
}) {
  return httpPost<LabOrder>(`/api/lab/orders/${id}/publish-report`, {
    deliveryChannels: body.deliveryChannels ?? [],
    publishNotes: body.publishNotes ?? null,
  }, { token, tenantId });
}

export async function recordLabOrderReportDeliveryAction(token: string, tenantId: string, id: string, body: LabOrderReportDeliveryActionInput) {
  return httpPost<LabOrder>(`/api/lab/orders/${id}/report-delivery-actions`, {
    action: body.action,
    channel: body.channel ?? null,
    notes: body.notes ?? null,
  }, { token, tenantId });
}

export async function getLabOrderPdf(token: string, tenantId: string, id: string) {
  const res = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/lab/orders/${id}/pdf`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
      Accept: "application/pdf",
    },
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}: ${res.statusText}`;
    const contentType = res.headers.get("content-type") || "";
    try {
      if (contentType.includes("application/json")) {
        const body = await res.json() as { message?: string };
        if (body?.message) {
          message = body.message;
        }
      } else {
        const text = (await res.text()).trim();
        if (text) {
          message = text;
        }
      }
    } catch {
      // keep fallback
    }
    throw new Error(message);
  }
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") || "";
  const match = disposition.match(/filename="?([^"]+)"?/i);
  return {
    blob,
    filename: match?.[1] || `lab-order-${id}.pdf`,
  };
}

export async function getLabOrderAttachmentBlob(token: string, tenantId: string, orderId: string, attachmentId: string, inline = true) {
  const res = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/lab/orders/${orderId}/attachments/${attachmentId}/${inline ? "view" : "download"}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
      Accept: "*/*",
    },
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}: ${res.statusText}`;
    try {
      const contentType = res.headers.get("content-type") || "";
      if (contentType.includes("application/json")) {
        const body = await res.json() as { message?: string };
        if (body?.message) message = body.message;
      } else {
        const text = (await res.text()).trim();
        if (text) message = text;
      }
    } catch {
      // ignore
    }
    throw new Error(message);
  }
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") || "";
  const match = disposition.match(/filename="?([^"]+)"?/i);
  return {
    blob,
    filename: match?.[1] || `lab-order-attachment-${attachmentId}`,
    mediaType: res.headers.get("content-type") || "application/octet-stream",
  };
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

export async function generateVaccinationPassport(token: string, tenantId: string, patientId: string) {
  return httpPost<GeneratedVaccinationDocumentResponse>(`/api/patients/${patientId}/vaccination-documents/passport`, undefined, { token, tenantId });
}

export async function generateVaccinationCertificate(token: string, tenantId: string, patientId: string, body: VaccinationCertificateInput) {
  return httpPost<GeneratedVaccinationDocumentResponse>(`/api/patients/${patientId}/vaccination-documents/certificates`, body, { token, tenantId });
}

export async function sendVaccinationDocument(token: string, tenantId: string, patientId: string, documentId: string, body: VaccinationDocumentSendInput) {
  return httpPost<void>(`/api/patients/${patientId}/vaccination-documents/${documentId}/send`, body, { token, tenantId });
}

export async function getVaccinationDocumentPdf(token: string, tenantId: string, patientId: string, documentId: string, mode: "VIEW" | "PRINT" | "DOWNLOAD" = "DOWNLOAD") {
  const response = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/patients/${patientId}/vaccination-documents/${documentId}/pdf?mode=${encodeURIComponent(mode)}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
      Accept: "application/pdf",
    },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`HTTP ${response.status}: ${text || response.statusText}`);
  }
  return { blob: await response.blob(), mediaType: response.headers.get("content-type") || "application/pdf" };
}

export async function queueVaccinationReminders(token: string, tenantId: string, patientId: string) {
  return httpPost<number>(`/api/patients/${patientId}/vaccination-documents/reminders/queue`, undefined, { token, tenantId });
}

export async function recordPatientVaccination(token: string, tenantId: string, patientId: string, body: PatientVaccinationInput) {
  return httpPost<PatientVaccination>(`/api/patients/${patientId}/vaccinations`, body, { token, tenantId });
}

export async function billPatientVaccination(token: string, tenantId: string, patientId: string, vaccinationId: string, body: PatientVaccinationBillInput) {
  return httpPost<PatientVaccination>(`/api/patients/${patientId}/vaccinations/${vaccinationId}/bill`, body, { token, tenantId });
}

export async function verifyPatientVaccination(token: string, tenantId: string, patientId: string, vaccinationId: string, body: PatientVaccinationUpdateInput) {
  return httpPost<PatientVaccination>(`/api/patients/${patientId}/vaccinations/${vaccinationId}/verify`, body, { token, tenantId });
}

export async function updatePatientVaccinationAefi(token: string, tenantId: string, patientId: string, vaccinationId: string, body: PatientVaccinationAefiInput) {
  return httpPost<PatientVaccination>(`/api/patients/${patientId}/vaccinations/${vaccinationId}/aefi`, body, { token, tenantId });
}

export async function getDueVaccinations(token: string, tenantId: string) {
  return httpGet<PatientVaccination[]>("/api/vaccinations/due", { token, tenantId });
}

export async function getOverdueVaccinations(token: string, tenantId: string) {
  return httpGet<PatientVaccination[]>("/api/vaccinations/overdue", { token, tenantId });
}

export async function getVaccinationHistory(
  token: string,
  tenantId: string,
  params: {
    patientId?: string | null;
    vaccineId?: string | null;
    fromDate?: string | null;
    toDate?: string | null;
    dueStatus?: "ALL" | "DUE" | "OVERDUE" | "NOT_DUE" | null;
  } = {},
) {
  const query = new URLSearchParams();
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.vaccineId) query.set("vaccineId", params.vaccineId);
  if (params.fromDate) query.set("fromDate", params.fromDate);
  if (params.toDate) query.set("toDate", params.toDate);
  if (params.dueStatus) query.set("dueStatus", params.dueStatus);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<PatientVaccination[]>(`/api/vaccinations/history${suffix}`, { token, tenantId });
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

export async function markNotificationRead(token: string, tenantId: string, id: string) {
  return httpPost<NotificationHistory>(`/api/notifications/${id}/read`, undefined, { token, tenantId });
}

export async function markNotificationUnread(token: string, tenantId: string, id: string) {
  return httpPost<NotificationHistory>(`/api/notifications/${id}/unread`, undefined, { token, tenantId });
}

export async function runVoiceTest(
  token: string,
  tenantId: string,
  input: {
    audio: File;
    context?: string;
    language?: string;
    workflowMode?: VoiceWorkflowMode;
  },
) {
  const formData = new FormData();
  formData.append("audio", input.audio, normalizeVoiceUploadFilename(input.audio));
  if (input.context?.trim()) formData.append("context", input.context.trim());
  if (input.language?.trim()) formData.append("language", input.language.trim());
  if (input.workflowMode?.trim()) formData.append("workflowMode", input.workflowMode.trim());
  return httpPostForm<VoiceTestResponse>("/api/voice/test", formData, { token, tenantId });
}

export async function runVoiceSttDebug(
  token: string,
  tenantId: string,
  input: {
    audio: File;
    language?: string;
  },
) {
  const formData = new FormData();
  formData.append("audio", input.audio, normalizeVoiceUploadFilename(input.audio));
  if (input.language?.trim()) formData.append("language", input.language.trim());
  return httpPostForm<VoiceSttDebugResponse>("/api/voice/debug/stt", formData, { token, tenantId });
}

function normalizeVoiceUploadFilename(audio: File): string {
  const originalName = audio.name?.trim() || "voice-test";
  const lowerName = originalName.toLowerCase();
  const normalizedType = (audio.type || "").toLowerCase().split(";", 1)[0].trim();
  if (normalizedType === "audio/webm" && lowerName.endsWith(".weba")) {
    return originalName.slice(0, -5) + ".webm";
  }
  return originalName;
}

export async function getVoiceTestStatus(token: string, tenantId: string, warmup = false) {
  const suffix = warmup ? "?warmup=true" : "";
  return httpGet<VoiceStatusResponse>(`/api/voice/status${suffix}`, { token, tenantId });
}

export async function getVoiceLiveStatus(token: string, tenantId: string) {
  return httpGet<VoiceLiveStatusResponse>("/api/voice/live-status", { token, tenantId });
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
  patientAgeGender?: string | null;
  allergies?: string | null;
  chronicConditions?: string | null;
  currentPrescriptionDraft?: string | null;
  labOrdersSummary?: string | null;
  doctorNotes?: string | null;
  symptoms?: string | null;
  vitals?: string | null;
  observations?: string | null;
}) {
  return httpPost<AiDraftResponse>("/api/ai/consultation/structure-notes", body, { token, tenantId });
}

export async function aiConsultationAsk(token: string, tenantId: string, body: {
  consultationId: string;
  patientId: string;
  prompt: string;
  patientAgeGender?: string | null;
  vitals?: string | null;
  allergies?: string | null;
  chronicConditions?: string | null;
  currentPrescriptionDraft?: string | null;
  labOrdersSummary?: string | null;
  chiefComplaints?: string | null;
  symptoms?: string | null;
  clinicalNotes?: string | null;
  diagnosis?: string | null;
  advice?: string | null;
}) {
  return httpPost<AiDraftResponse>("/api/ai/consultation/ask", body, { token, tenantId });
}

export async function aiSuggestDiagnosis(token: string, tenantId: string, body: {
  consultationId: string;
  patientId: string;
  patientAgeGender?: string | null;
  vitals?: string | null;
  currentPrescriptionDraft?: string | null;
  labOrdersSummary?: string | null;
  symptoms?: string | null;
  findings?: string | null;
  doctorNotes?: string | null;
  knownConditions?: string | null;
  allergies?: string | null;
}) {
  return httpPost<AiDraftResponse>("/api/ai/consultation/suggest-diagnosis", body, { token, tenantId });
}

export async function generateClinicalReasoning(token: string, tenantId: string, consultationId: string, body: {
  patientId?: string | null;
  chiefComplaint?: string | null;
  symptoms?: string | null;
  findings?: string | null;
  vitals?: string | null;
  diagnosis?: string | null;
  advice?: string | null;
  notes?: string | null;
  currentPrescriptionDraft?: string | null;
  labOrdersSummary?: string | null;
}, options?: { debug?: boolean }) {
  const debugQuery = options?.debug ? "?debug=true" : "";
  return httpPost<ClinicalReasoningResponse>(`/api/consultations/${consultationId}/clinical-reasoning/generate${debugQuery}`, body, { token, tenantId });
}

export async function aiSuggestPrescriptionTemplate(token: string, tenantId: string, body: {
  consultationId: string;
  patientId: string;
  patientAgeGender?: string | null;
  vitals?: string | null;
  currentPrescriptionDraft?: string | null;
  labOrdersSummary?: string | null;
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
  return httpPost<ClinicalDocument>(`/api/clinical-documents/${documentId}/ai/reprocess`, undefined, { token, tenantId });
}

export async function repairClinicalMemory(token: string, tenantId: string, documentId: string) {
  return httpPost<ClinicalMemoryRepairResult>(`/api/clinical-documents/${documentId}/clinical-memory/repair`, undefined, { token, tenantId });
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
  paymentMode?: string | null;
  source?: string | null;
  search?: string | null;
} = {}) {
  const query = new URLSearchParams();
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  if (params.doctorUserId) query.set("doctorUserId", params.doctorUserId);
  if (params.patientId) query.set("patientId", params.patientId);
  if (params.status) query.set("status", params.status);
  if (params.paymentMode) query.set("paymentMode", params.paymentMode);
  if (params.source) query.set("source", params.source);
  if (params.search) query.set("search", params.search);
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
  params?: { from?: string | null; to?: string | null; doctorUserId?: string | null; patientId?: string | null; paymentMode?: string | null; source?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/revenue${buildReportQuery(params)}`, { token, tenantId });
}

export async function getDailySalesReport(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; paymentMode?: string | null; source?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/daily-sales${buildReportQuery(params)}`, { token, tenantId });
}

export async function getMedicineSalesReport(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; paymentMode?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/medicine-sales${buildReportQuery(params)}`, { token, tenantId });
}

export async function getPaymentModesReport(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; paymentMode?: string | null; source?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/payment-modes${buildReportQuery(params)}`, { token, tenantId });
}

export async function getCashierShiftsReport(token: string, tenantId: string, params?: { from?: string | null; to?: string | null }) {
  return httpGet<ReportRow[]>(`/api/reports/cashier-shifts${buildReportQuery(params)}`, { token, tenantId });
}

export async function getCashCounterSummary(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; paymentMode?: string | null; source?: string | null; search?: string | null },
) {
  return httpGet<CashCounterSummary>(`/api/reports/cash-counter-summary${buildReportQuery(params)}`, { token, tenantId });
}

export async function getCashCounterLedger(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null; paymentMode?: string | null; source?: string | null; search?: string | null },
) {
  return httpGet<CashCounterLedgerRow[]>(`/api/reports/cash-counter-ledger${buildReportQuery(params)}`, { token, tenantId });
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

export async function getLabOperationsReport(
  token: string,
  tenantId: string,
  params?: { from?: string | null; to?: string | null },
) {
  return httpGet<ReportRow[]>(`/api/reports/lab-operations${buildReportQuery(params)}`, { token, tenantId });
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

export async function searchMedicines(token: string, tenantId: string, query?: string | null) {
  const suffix = query ? `?q=${encodeURIComponent(query)}` : "";
  return httpGet<Medicine[]>(`/api/medicines/search${suffix}`, { token, tenantId });
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

export async function importMedicinesCsv(token: string, tenantId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return httpPostForm<MedicineImportResult>("/api/pharmacy/medicines/import-csv", formData, { token, tenantId });
}

export async function getMedicineImportTemplate(token: string, tenantId: string) {
  return httpGetText("/api/pharmacy/medicines/import-template", { token, tenantId });
}

export async function getStocks(token: string, tenantId: string) {
  return httpGet<Stock[]>("/api/inventory/stocks", { token, tenantId });
}

export async function getInventoryLocations(token: string, tenantId: string) {
  return httpGet<InventoryLocation[]>("/api/inventory/locations", { token, tenantId });
}

export async function createInventoryLocation(token: string, tenantId: string, body: InventoryLocationInput) {
  return httpPost<InventoryLocation>("/api/inventory/locations", body, { token, tenantId });
}

export async function updateInventoryLocation(token: string, tenantId: string, id: string, body: InventoryLocationInput) {
  return httpPut<InventoryLocation>(`/api/inventory/locations/${id}`, body, { token, tenantId });
}

export async function transferInventoryStock(token: string, tenantId: string, body: InventoryTransferInput) {
  return httpPost("/api/inventory/transfers", body, { token, tenantId });
}

export async function searchStocks(token: string, tenantId: string, query?: string | null) {
  const suffix = query ? `?q=${encodeURIComponent(query)}` : "";
  return httpGet<Stock[]>(`/api/inventory/stocks/search${suffix}`, { token, tenantId });
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

export async function getPharmacyDashboard(token: string, tenantId: string) {
  return httpGet<PharmacyDashboard>("/api/pharmacy/dashboard", { token, tenantId });
}

export async function getPharmacyAnalytics(token: string, tenantId: string) {
  return httpGet<PharmacyAnalytics>("/api/pharmacy/analytics", { token, tenantId });
}

export async function listSuppliers(token: string, tenantId: string) {
  return httpGet<Supplier[]>("/api/pharmacy/suppliers", { token, tenantId });
}

export async function createSupplier(token: string, tenantId: string, body: SupplierInput) {
  return httpPost<Supplier>("/api/pharmacy/suppliers", body, { token, tenantId });
}

export async function updateSupplier(token: string, tenantId: string, id: string, body: SupplierInput) {
  return httpPut<Supplier>(`/api/pharmacy/suppliers/${id}`, body, { token, tenantId });
}

export async function listReconciliations(token: string, tenantId: string) {
  return httpGet<PharmacyReconciliation[]>("/api/pharmacy/reconciliations", { token, tenantId });
}

export async function createReconciliation(token: string, tenantId: string, body: PharmacyReconciliationInput) {
  return httpPost<PharmacyReconciliation>("/api/pharmacy/reconciliations", body, { token, tenantId });
}

export async function updateReconciliation(token: string, tenantId: string, id: string, body: PharmacyReconciliationInput) {
  return httpPut<PharmacyReconciliation>(`/api/pharmacy/reconciliations/${id}`, body, { token, tenantId });
}

export async function submitReconciliation(token: string, tenantId: string, id: string) {
  return httpPost<PharmacyReconciliation>(`/api/pharmacy/reconciliations/${id}/submit`, undefined, { token, tenantId });
}

export async function approveReconciliation(token: string, tenantId: string, id: string, body: PharmacyReconciliationDecisionInput = { reason: null }) {
  return httpPost<PharmacyReconciliation>(`/api/pharmacy/reconciliations/${id}/approve`, body, { token, tenantId });
}

export async function rejectReconciliation(token: string, tenantId: string, id: string, body: PharmacyReconciliationDecisionInput) {
  return httpPost<PharmacyReconciliation>(`/api/pharmacy/reconciliations/${id}/reject`, body, { token, tenantId });
}

export async function postReconciliation(token: string, tenantId: string, id: string, body: PharmacyReconciliationPostInput = { physicalQuantity: null, reason: null }) {
  return httpPost<PharmacyReconciliation>(`/api/pharmacy/reconciliations/${id}/post`, body, { token, tenantId });
}

export async function confirmReconciliation(token: string, tenantId: string, id: string, body: PharmacyReconciliationConfirmInput) {
  return httpPost<PharmacyReconciliation>(`/api/pharmacy/reconciliations/${id}/confirm`, body, { token, tenantId });
}

export async function reviewReconciliationSheet(token: string, tenantId: string, id: string, body: { rows: OcrExtractionRow[]; reviewNote?: string | null }) {
  return httpPost<PharmacyReconciliation>(`/api/pharmacy/reconciliations/${id}/review`, body, { token, tenantId });
}

export async function uploadReconciliationSheet(token: string, tenantId: string, id: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return httpPostForm<ReconciliationUploadResponse>(
    `/api/pharmacy/reconciliations/${id}/sheet`,
    formData,
    { token, tenantId },
  );
}

export async function createStockInward(token: string, tenantId: string, body: StockInwardInput) {
  return httpPost<Stock>("/api/pharmacy/inward", body, { token, tenantId });
}

export async function getPurchaseOrders(token: string, tenantId: string) {
  return httpGet<PurchaseOrder[]>("/api/pharmacy/purchase-orders", { token, tenantId });
}

export async function getPurchaseOrder(token: string, tenantId: string, id: string) {
  return httpGet<PurchaseOrder>(`/api/pharmacy/purchase-orders/${id}`, { token, tenantId });
}

export async function createPurchaseOrder(token: string, tenantId: string, body: PurchaseOrderInput) {
  return httpPost<PurchaseOrder>("/api/pharmacy/purchase-orders", body, { token, tenantId });
}

export async function cancelPurchaseOrder(token: string, tenantId: string, id: string, reason: string) {
  return httpPost<PurchaseOrder>(`/api/pharmacy/purchase-orders/${id}/cancel`, reason, { token, tenantId });
}

export async function getSupplierInvoices(token: string, tenantId: string) {
  return httpGet<SupplierInvoice[]>("/api/pharmacy/supplier-invoices", { token, tenantId });
}

export async function createSupplierInvoice(token: string, tenantId: string, body: SupplierInvoiceInput) {
  return httpPost<SupplierInvoice>("/api/pharmacy/supplier-invoices", body, { token, tenantId });
}

export async function updateSupplierInvoice(token: string, tenantId: string, id: string, body: SupplierInvoiceInput) {
  return httpPut<SupplierInvoice>(`/api/pharmacy/supplier-invoices/${id}`, body, { token, tenantId });
}

export async function matchSupplierInvoice(token: string, tenantId: string, id: string) {
  return httpPost<SupplierInvoice>(`/api/pharmacy/supplier-invoices/${id}/match`, {}, { token, tenantId });
}

export async function approveSupplierInvoiceForPayment(token: string, tenantId: string, id: string) {
  return httpPost<SupplierInvoice>(`/api/pharmacy/supplier-invoices/${id}/approve-for-payment`, {}, { token, tenantId });
}

export async function cancelSupplierInvoice(token: string, tenantId: string, id: string, reason: string) {
  return httpPost<SupplierInvoice>(`/api/pharmacy/supplier-invoices/${id}/cancel`, { reason }, { token, tenantId });
}

export async function uploadSupplierInvoiceAttachment(token: string, tenantId: string, id: string, file: File) {
  const formData = new FormData();
  formData.set("file", file);
  return httpPostForm<SupplierInvoiceAttachment>(`/api/pharmacy/supplier-invoices/${id}/attachment`, formData, { token, tenantId });
}

export async function getGoodsReceipts(token: string, tenantId: string) {
  return httpGet<GoodsReceipt[]>("/api/pharmacy/goods-receipts", { token, tenantId });
}

export async function createGoodsReceipt(token: string, tenantId: string, body: GoodsReceiptInput) {
  return httpPost<GoodsReceipt>("/api/pharmacy/goods-receipts", body, { token, tenantId });
}

export async function confirmGoodsReceipt(token: string, tenantId: string, id: string, approvalNote?: string | null) {
  return httpPost<GoodsReceipt>(`/api/pharmacy/goods-receipts/${id}/confirm`, approvalNote ?? "", { token, tenantId });
}

export async function getSubstituteSuggestions(token: string, tenantId: string, medicineId: string) {
  return httpGet<SubstituteSuggestion[]>(`/api/pharmacy/substitutes?medicineId=${encodeURIComponent(medicineId)}`, { token, tenantId });
}

export type DispenseLine = {
  itemId: string | null;
  prescribedMedicineName: string;
  medicineId: string | null;
  prescribedQuantity: number;
  dispensedQuantity: number;
  pendingQuantity: number;
  status: "NOT_DISPENSED" | "PARTIALLY_DISPENSED" | "DISPENSED" | string;
  availableQuantity: number | null;
  availabilityStatus: "AVAILABLE" | "LOW_STOCK" | "PARTIAL_AVAILABLE" | "OUT_OF_STOCK" | string;
  expiryStatus: "NONE" | "OK" | "NEAR_EXPIRY" | "EXPIRED" | string;
  nearestExpiryDate: string | null;
  lastBatchId: string | null;
};

export type PrescriptionDispense = {
  prescriptionId: string;
  prescriptionNumber: string;
  patientId: string;
  patientName: string | null;
  doctorName: string | null;
  prescriptionTimestamp: string | null;
  status: "NOT_DISPENSED" | "READY_FOR_DISPENSE" | "PARTIALLY_DISPENSED" | "FULLY_DISPENSED" | "BOUGHT_EXTERNALLY" | "PATIENT_DECLINED" | "UNAVAILABLE_CLOSED" | "CANCELLED" | "EXPIRED" | string;
  billingStatus: "NOT_BILLED" | "BILLED" | "PAID" | string;
  billedBillId: string | null;
  lines: DispenseLine[];
};

export type DispenseInput = {
  medicineLineId: string | null;
  prescribedMedicineName: string;
  medicineId: string | null;
  quantity: number | null;
  batchOverride: string | null;
  allowBatchOverride: boolean;
  action: string | null;
  reason: string | null;
  remarks: string | null;
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
  return httpGet<PlatformTenant[]>("/api/platform/tenants", { token, platformOperation: true });
}

export async function getPlatformTenant(token: string, tenantId: string) {
  return httpGet<PlatformTenantDetail>(`/api/platform/tenants/${tenantId}`, { token, platformOperation: true });
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
  return httpPost<PlatformTenantDetail>("/api/platform/tenants", body, { token, platformOperation: true });
}

export async function updatePlatformTenantStatus(token: string, tenantId: string, active: boolean) {
  return httpPatch<PlatformTenant>(`/api/platform/tenants/${tenantId}/status`, { active }, { token, platformOperation: true });
}

export async function updatePlatformTenantPlan(token: string, tenantId: string, planId: string) {
  return httpPut<PlatformTenant>(`/api/platform/tenants/${tenantId}/plan`, { planId }, { token, platformOperation: true });
}

export async function getPlatformPlans(token: string) {
  return httpGet<PlatformPlan[]>("/api/platform/plans", { token, platformOperation: true });
}

export async function getPlatformTenantModules(token: string, tenantId: string) {
  return httpGet<Record<string, boolean>>(`/api/platform/tenants/${tenantId}/modules`, { token, platformOperation: true });
}

export async function updatePlatformTenantModules(token: string, tenantId: string, modules: Record<string, boolean>) {
  return httpPut<PlatformTenant>(`/api/platform/tenants/${tenantId}/modules`, modules, { token, platformOperation: true });
}

export async function createPlatformTenantAdminUser(token: string, tenantId: string, body: {
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  tempPassword?: string | null;
}) {
  return httpPost(`/api/platform/tenants/${tenantId}/admin-user`, body, { token, platformOperation: true });
}

type PatientDocumentListFilters = {
  documentType?: ClinicalDocumentType | null;
  reportDateFrom?: string | null;
  reportDateTo?: string | null;
  uploadSource?: ClinicalDocumentUploadSource | null;
  consultationId?: string | null;
  search?: string | null;
};

function buildPatientDocumentFilters(filters?: PatientDocumentListFilters): string {
  if (!filters) return "";
  const params = new URLSearchParams();
  if (filters.documentType) params.set("documentType", filters.documentType);
  if (filters.reportDateFrom) params.set("reportDateFrom", filters.reportDateFrom);
  if (filters.reportDateTo) params.set("reportDateTo", filters.reportDateTo);
  if (filters.uploadSource) params.set("uploadSource", filters.uploadSource);
  if (filters.consultationId) params.set("consultationId", filters.consultationId);
  if (filters.search) params.set("search", filters.search);
  const query = params.toString();
  return query ? `?${query}` : "";
}

export async function getPatientDocuments(token: string, tenantId: string, patientId: string, filters?: PatientDocumentListFilters) {
  return httpGet<ClinicalDocument[]>(`/api/patients/${patientId}/documents${buildPatientDocumentFilters(filters)}`, { token, tenantId });
}

export async function getClinicalDocument(token: string, tenantId: string, documentId: string) {
  return httpGet<ClinicalDocument>(`/api/patient-documents/${documentId}`, { token, tenantId });
}

export async function uploadPatientDocument(token: string, tenantId: string, patientId: string, body: {
  file: File;
  documentType: ClinicalDocumentType;
  title: string;
  reportDate?: string | null;
  consultationId?: string | null;
  notes?: string | null;
  uploadSource?: ClinicalDocumentUploadSource | null;
  sourceModule?: string | null;
  sourceEntityId?: string | null;
  visibility?: ClinicalDocumentVisibility | null;
}) {
  const formData = new FormData();
  formData.append("file", body.file);
  formData.append("documentType", body.documentType);
  formData.append("title", body.title);
  if (body.reportDate) formData.append("reportDate", body.reportDate);
  if (body.consultationId) formData.append("consultationId", body.consultationId);
  if (body.notes) formData.append("notes", body.notes);
  if (body.uploadSource) formData.append("uploadSource", body.uploadSource);
  if (body.sourceModule) formData.append("sourceModule", body.sourceModule);
  if (body.sourceEntityId) formData.append("sourceEntityId", body.sourceEntityId);
  if (body.visibility) formData.append("visibility", body.visibility);
  return httpPostForm<ClinicalDocument>(`/api/patients/${patientId}/documents`, formData, { token, tenantId });
}

export async function getPatientDocumentDownloadUrl(token: string, tenantId: string, documentId: string) {
  return httpGet<{ url: string; expiresInSeconds: string }>(`/api/patient-documents/${documentId}/download-url`, { token, tenantId });
}

export async function getPatientDocumentViewUrl(token: string, tenantId: string, patientId: string, documentId: string) {
  const response = await fetch(`${(import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "")}/api/patients/${patientId}/documents/${documentId}/view`, {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-Id": tenantId,
      Accept: "application/pdf",
    },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`HTTP ${response.status}: ${text || response.statusText}`);
  }
  return { blob: await response.blob() };
}

export async function generateConsultationDocument(token: string, tenantId: string, consultationId: string, body: {
  title: string;
  documentType: ClinicalDocumentType;
  body: string;
  language?: string | null;
  notes?: string | null;
  visibility?: ClinicalDocumentVisibility | null;
}) {
  return httpPost<ConsultationGeneratedDocumentResponse>(`/api/consultations/${consultationId}/generated-documents`, body, { token, tenantId });
}

export async function deletePatientDocument(token: string, tenantId: string, patientId: string, documentId: string) {
  return httpDelete<void>(`/api/patients/${patientId}/documents/${documentId}`, { token, tenantId });
}

export async function patchPatientDocument(token: string, tenantId: string, patientId: string, documentId: string, body: {
  documentType?: ClinicalDocumentType | null;
  title: string;
  description?: string | null;
  reportDate?: string | null;
  visibility?: ClinicalDocumentVisibility | null;
  verificationStatus?: ClinicalDocumentVerificationStatus | null;
}) {
  return httpPatch<ClinicalDocument>(`/api/patients/${patientId}/documents/${documentId}`, body, { token, tenantId });
}

export async function getPatientTimeline(token: string, tenantId: string, patientId: string) {
  return httpGet<PatientTimelineItem[]>(`/api/patients/${patientId}/timeline`, { token, tenantId });
}

export async function getClinicalIntake(token: string, tenantId: string, patientId: string, appointmentId?: string | null) {
  const query = new URLSearchParams();
  if (appointmentId) {
    query.set("appointmentId", appointmentId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<ClinicalIntakeResponse | null>(`/api/patients/${patientId}/clinical-intake/latest${suffix}`, { token, tenantId });
}

export async function saveClinicalIntake(token: string, tenantId: string, patientId: string, body: {
  appointmentId?: string | null;
  consultationId?: string | null;
  chiefComplaint?: string | null;
  heightCm?: number | null;
  weightKg?: number | null;
  bloodPressureSystolic?: number | null;
  bloodPressureDiastolic?: number | null;
  pulseRate?: number | null;
  temperature?: number | null;
  temperatureUnit?: TemperatureUnit | null;
  spo2?: number | null;
  respiratoryRate?: number | null;
  randomBloodSugar?: number | null;
  painScore?: number | null;
  notes?: string | null;
  complete: boolean;
}) {
  return httpPost<ClinicalIntakeResponse>(`/api/patients/${patientId}/clinical-intake`, body, { token, tenantId });
}

export async function getClinicalContext(token: string, tenantId: string, patientId: string, consultationId?: string | null) {
  const query = new URLSearchParams();
  query.set("patientId", patientId);
  if (consultationId) {
    query.set("consultationId", consultationId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGet<ClinicalContextResponse>(`/api/ai/clinical-context${suffix}`, { token, tenantId });
}

export type CareAiReceptionistTaskType = "HUMAN_HANDOFF" | "APPOINTMENT_HANDOFF" | "CALLBACK_REQUEST" | "ESCALATION";
export type CareAiReceptionistTaskStatus = "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED" | "CANCELLED";
export type CareAiReceptionistTaskPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type CareAiReceptionistTaskSlaStatus = "ON_TIME" | "DUE_SOON" | "OVERDUE" | "BREACHED";
export type CareAiReceptionistTaskHandlingMode = "AI_HANDLING" | "STAFF_HANDLING" | "RETURNED_TO_AI";

export type CareAiReceptionistTask = {
  id: string;
  tenantId: string;
  conversationId: string | null;
  workflowId: string | null;
  patientId: string | null;
  leadId: string | null;
  appointmentId: string | null;
  taskType: CareAiReceptionistTaskType;
  status: CareAiReceptionistTaskStatus;
  priority: CareAiReceptionistTaskPriority;
  channel: string | null;
  reason: string | null;
  latestUserMessage: string | null;
  callbackTimePref: string | null;
  callbackDueAt: string | null;
  dueAt: string | null;
  slaStatus: CareAiReceptionistTaskSlaStatus;
  handlingMode: CareAiReceptionistTaskHandlingMode;
  assignedUserId: string | null;
  assignedAt: string | null;
  firstResponseAt: string | null;
  breachedAt: string | null;
  lastNotificationAt: string | null;
  lastStaffMessageAt: string | null;
  resolvedAt: string | null;
  resolvedByUserId: string | null;
  resolutionNotes: string | null;
  metadataJson: string;
  createdAt: string;
  updatedAt: string;
};

export type CareAiReceptionistTaskMessage = {
  id: string;
  speaker: string;
  channel: string;
  content: string;
  createdAt: string;
};

export type CareAiReceptionistTaskDetail = {
  task: CareAiReceptionistTask;
  messages: CareAiReceptionistTaskMessage[];
  resumeContext: CareAiReceptionistTaskResumeContext;
};

export type CareAiConversationSummary = {
  id: string;
  channel: string;
  status: string;
  patientId: string | null;
  leadId: string | null;
  appointmentId: string | null;
  currentWorkflowId: string | null;
  summary: string | null;
  externalSessionId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CareAiWorkflowSummary = {
  id: string;
  workflowType: string;
  state: string;
  lastQuestionKey: string | null;
  repeatedQuestionCount: number;
  context: Record<string, unknown>;
};

export type CareAiReceptionistTaskResumeContext = {
  task: CareAiReceptionistTask;
  conversation: CareAiConversationSummary | null;
  workflow: CareAiWorkflowSummary | null;
  recommendedNextPrompt: string | null;
};

export type CareAiReceptionistTaskConversation = {
  task: CareAiReceptionistTask;
  conversation: CareAiConversationSummary | null;
  workflow: CareAiWorkflowSummary | null;
  messages: CareAiReceptionistTaskMessage[];
};

export type CareAiReceptionistTaskEvent = {
  id: string;
  taskId: string;
  eventType: string;
  actorUserId: string | null;
  payloadJson: string;
  createdAt: string;
};

export type CareAiConversationMessage = {
  id: string;
  conversationId: string;
  speaker: string;
  channel: string;
  content: string;
  intent: string | null;
  entitiesJson: string;
  metadataJson: string;
  createdAt: string;
};

function buildCareAiReceptionistTaskQuery(params: {
  status?: CareAiReceptionistTaskStatus | "";
  type?: CareAiReceptionistTaskType | "";
  priority?: CareAiReceptionistTaskPriority | "";
  assignedToMe?: boolean;
  overdueOnly?: boolean;
  dueSoonOnly?: boolean;
  patientId?: string | null;
} = {}) {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.type) query.set("type", params.type);
  if (params.priority) query.set("priority", params.priority);
  if (params.assignedToMe) query.set("assignedToMe", "true");
  if (params.overdueOnly) query.set("overdueOnly", "true");
  if (params.dueSoonOnly) query.set("dueSoonOnly", "true");
  if (params.patientId) query.set("patientId", params.patientId);
  return query.toString() ? `?${query.toString()}` : "";
}

export async function listCareAiReceptionistTasks(
  token: string,
  tenantId: string,
  params: {
    status?: CareAiReceptionistTaskStatus | "";
    type?: CareAiReceptionistTaskType | "";
    priority?: CareAiReceptionistTaskPriority | "";
    assignedToMe?: boolean;
    overdueOnly?: boolean;
    dueSoonOnly?: boolean;
    patientId?: string | null;
  } = {},
) {
  return httpGet<CareAiReceptionistTask[]>(`/api/careai/receptionist-tasks${buildCareAiReceptionistTaskQuery(params)}`, { token, tenantId });
}

export async function listCareAiCallbackTasks(
  token: string,
  tenantId: string,
  params: Parameters<typeof listCareAiReceptionistTasks>[2] = {},
) {
  return httpGet<CareAiReceptionistTask[]>(`/api/careai/receptionist-tasks/callbacks${buildCareAiReceptionistTaskQuery(params)}`, { token, tenantId });
}

export async function listCareAiEscalationTasks(
  token: string,
  tenantId: string,
  params: Parameters<typeof listCareAiReceptionistTasks>[2] = {},
) {
  return httpGet<CareAiReceptionistTask[]>(`/api/careai/receptionist-tasks/escalations${buildCareAiReceptionistTaskQuery(params)}`, { token, tenantId });
}

export async function listCareAiHandoffTasks(
  token: string,
  tenantId: string,
  params: Parameters<typeof listCareAiReceptionistTasks>[2] = {},
) {
  return httpGet<CareAiReceptionistTask[]>(`/api/careai/receptionist-tasks/handoffs${buildCareAiReceptionistTaskQuery(params)}`, { token, tenantId });
}

export async function listCareAiAppointmentHandoffTasks(
  token: string,
  tenantId: string,
  params: Parameters<typeof listCareAiReceptionistTasks>[2] = {},
) {
  return httpGet<CareAiReceptionistTask[]>(`/api/careai/receptionist-tasks/appointment-handoffs${buildCareAiReceptionistTaskQuery(params)}`, { token, tenantId });
}

export async function getCareAiReceptionistTask(token: string, tenantId: string, taskId: string) {
  return httpGet<CareAiReceptionistTaskDetail>(`/api/careai/receptionist-tasks/${taskId}`, { token, tenantId });
}

export async function getCareAiReceptionistTaskConversation(token: string, tenantId: string, taskId: string) {
  return httpGet<CareAiReceptionistTaskConversation>(`/api/careai/receptionist-tasks/${taskId}/conversation`, { token, tenantId });
}

export async function getCareAiReceptionistTaskResumeContext(token: string, tenantId: string, taskId: string) {
  return httpGet<CareAiReceptionistTaskResumeContext>(`/api/careai/receptionist-tasks/${taskId}/resume-context`, { token, tenantId });
}

export async function listCareAiReceptionistTaskEvents(token: string, tenantId: string, taskId: string) {
  return httpGet<CareAiReceptionistTaskEvent[]>(`/api/careai/receptionist-tasks/${taskId}/events`, { token, tenantId });
}

export async function assignCareAiReceptionistTaskToMe(token: string, tenantId: string, taskId: string) {
  return httpPost<CareAiReceptionistTask>(`/api/careai/receptionist-tasks/${taskId}/assign-me`, undefined, { token, tenantId });
}

export async function claimCareAiReceptionistTaskAssignment(token: string, tenantId: string, taskId: string) {
  return httpPost<CareAiReceptionistTask>(`/api/careai/receptionist-tasks/${taskId}/assignments/claim`, undefined, { token, tenantId });
}

export async function markCareAiReceptionistTaskInProgress(token: string, tenantId: string, taskId: string) {
  return httpPost<CareAiReceptionistTask>(`/api/careai/receptionist-tasks/${taskId}/in-progress`, undefined, { token, tenantId });
}

export async function resumeCareAiReceptionistTask(token: string, tenantId: string, taskId: string) {
  return httpPost<CareAiReceptionistTask>(`/api/careai/receptionist-tasks/${taskId}/resume`, undefined, { token, tenantId });
}

export async function addCareAiReceptionistStaffNote(token: string, tenantId: string, taskId: string, note: string) {
  return httpPost<CareAiReceptionistTask>(`/api/careai/receptionist-tasks/${taskId}/staff-note`, { note }, { token, tenantId });
}

export async function returnCareAiReceptionistTaskToAi(token: string, tenantId: string, taskId: string) {
  return httpPost<CareAiReceptionistTask>(`/api/careai/receptionist-tasks/${taskId}/return-to-ai`, undefined, { token, tenantId });
}

export async function scheduleCareAiReceptionistTaskCallback(
  token: string,
  tenantId: string,
  taskId: string,
  callbackTimePreference?: string | null,
  callbackDueAt?: string | null,
) {
  return httpPost<CareAiReceptionistTask>(
    `/api/careai/receptionist-tasks/${taskId}/schedule-callback`,
    { callbackTimePreference: callbackTimePreference ?? null, callbackDueAt: callbackDueAt ?? null },
    { token, tenantId },
  );
}

export async function resolveCareAiReceptionistTask(token: string, tenantId: string, taskId: string, resolutionNotes?: string | null) {
  return httpPost<CareAiReceptionistTask>(`/api/careai/receptionist-tasks/${taskId}/resolve`, { resolutionNotes: resolutionNotes ?? null }, { token, tenantId });
}

export async function cancelCareAiReceptionistTask(token: string, tenantId: string, taskId: string, resolutionNotes?: string | null) {
  return httpPost<CareAiReceptionistTask>(`/api/careai/receptionist-tasks/${taskId}/cancel`, { resolutionNotes: resolutionNotes ?? null }, { token, tenantId });
}

export async function listCareAiConversations(token: string, tenantId: string) {
  return httpGet<CareAiConversationSummary[]>("/api/careai/conversations", { token, tenantId });
}

export async function listActiveCareAiConversations(token: string, tenantId: string, patientId?: string | null) {
  const query = patientId ? `?patientId=${encodeURIComponent(patientId)}` : "";
  return httpGet<CareAiConversationSummary[]>(`/api/careai/conversations/active${query}`, { token, tenantId });
}

export async function getCareAiConversation(token: string, tenantId: string, conversationId: string) {
  return httpGet<CareAiConversationSummary>(`/api/careai/conversations/${conversationId}`, { token, tenantId });
}

export async function listCareAiConversationMessages(token: string, tenantId: string, conversationId: string) {
  return httpGet<CareAiConversationMessage[]>(`/api/careai/conversations/${conversationId}/messages`, { token, tenantId });
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

export type CarePilotCampaignTriggerResult = {
  campaignId: string;
  campaignName: string;
  audienceType: CarePilotAudienceType;
  templateId: string | null;
  channelType: CarePilotChannelType;
  queued: boolean;
  eligibleRecipients: number;
  queuedExecutions: number;
  skippedRecipients: number;
  message: string;
  queuedAt: string;
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
export type CarePilotLeadSource = "WEBSITE" | "WEBINAR" | "WALK_IN" | "PHONE_CALL" | "WHATSAPP" | "FACEBOOK" | "GOOGLE_ADS" | "REFERRAL" | "CAMPAIGN" | "MANUAL" | "AI_RECEPTIONIST" | "OTHER";

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

export type CarePilotLeadCsvImportRowError = {
  rowNumber: number;
  message: string;
};

export type CarePilotLeadCsvImportResult = {
  importedCount: number;
  skippedDuplicateCount: number;
  failedCount: number;
  rowErrors: CarePilotLeadCsvImportRowError[];
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
  campaignId: string | null;
  campaignName: string | null;
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
  leadName: string | null;
  campaignId: string | null;
  campaignName: string | null;
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

export type CarePilotAiCallCampaignUpsertInput = {
  name: string;
  description?: string | null;
  callType: CarePilotAiCallType;
  status?: CarePilotAiCallCampaignStatus | null;
  templateId?: string | null;
  channel?: CarePilotChannelType | null;
  retryEnabled?: boolean | null;
  maxAttempts?: number | null;
  escalationEnabled?: boolean | null;
};

export type CarePilotAiCallTriggerTargetInput = {
  patientId?: string | null;
  leadId?: string | null;
  phoneNumber?: string | null;
  script?: string | null;
  scheduledAt?: string | null;
};

export type CarePilotAiCallManualCallInput = {
  patientId?: string | null;
  leadId?: string | null;
  phoneNumber: string;
  templateId?: string | null;
  callType: CarePilotAiCallType;
  script?: string | null;
  scheduledAt?: string | null;
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
  clinicTimeZone: string | null;
  clinicNow: string;
  serverNowUtc: string;
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

export type ClinicClock = {
  clinicTimeZone: string;
  clinicNow: string;
  serverNowUtc: string;
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

export async function getCarePilotLeadImportTemplate(token: string, tenantId: string) {
  return httpGetText("/api/carepilot/leads/import-template", { token, tenantId, accept: "text/csv, */*" });
}

export async function importCarePilotLeadsCsv(token: string, tenantId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return httpPostForm<CarePilotLeadCsvImportResult>("/api/carepilot/leads/import-csv", formData, { token, tenantId });
}

export async function exportCarePilotLeadsCsv(
  token: string,
  tenantId: string,
  filters: {
    status?: CarePilotLeadStatus;
    source?: CarePilotLeadSource;
    priority?: CarePilotLeadPriority;
    search?: string;
    followUpDue?: boolean;
  } = {},
) {
  const query = new URLSearchParams();
  if (filters.status) query.set("status", filters.status);
  if (filters.source) query.set("source", filters.source);
  if (filters.priority) query.set("priority", filters.priority);
  if (filters.search) query.set("search", filters.search);
  if (filters.followUpDue) query.set("followUpDue", "true");
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return httpGetText(`/api/carepilot/leads/export${suffix}`, { token, tenantId, accept: "text/csv, */*" });
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

export async function triggerCarePilotCampaign(token: string, tenantId: string, campaignId: string) {
  return httpPost<CarePilotCampaignTriggerResult>(`/api/carepilot/campaigns/${campaignId}/trigger`, {}, { token, tenantId });
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

export async function getClinicClock(token: string, tenantId: string) {
  return httpGet<ClinicClock>("/api/clinic/clock", { token, tenantId });
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

export async function getCarePilotAiCallCampaign(token: string, tenantId: string, campaignId: string) {
  return httpGet<CarePilotAiCallCampaign>(`/api/carepilot/ai-calls/campaigns/${campaignId}`, { token, tenantId });
}

export async function createCarePilotAiCallCampaign(
  token: string,
  tenantId: string,
  body: CarePilotAiCallCampaignUpsertInput
) {
  return httpPost<CarePilotAiCallCampaign>("/api/carepilot/ai-calls/campaigns", body, { token, tenantId });
}

export async function updateCarePilotAiCallCampaign(
  token: string,
  tenantId: string,
  campaignId: string,
  body: CarePilotAiCallCampaignUpsertInput
) {
  return httpPut<CarePilotAiCallCampaign>(`/api/carepilot/ai-calls/campaigns/${campaignId}`, body, { token, tenantId });
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
  body: { targets: CarePilotAiCallTriggerTargetInput[] }
) {
  return httpPost<CarePilotAiCallExecution[]>(`/api/carepilot/ai-calls/campaigns/${campaignId}/trigger`, body, { token, tenantId });
}

export async function createCarePilotAiCallManualCall(
  token: string,
  tenantId: string,
  body: CarePilotAiCallManualCallInput
) {
  return httpPost<CarePilotAiCallExecution>("/api/carepilot/ai-calls/manual-call", body, { token, tenantId });
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

export async function getCarePilotAiCallExecution(token: string, tenantId: string, executionId: string) {
  return httpGet<CarePilotAiCallExecution>(`/api/carepilot/ai-calls/executions/${executionId}`, { token, tenantId });
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

export async function sendReceptionistTestMessage(token: string, tenantId: string, input: {
  sessionId: string;
  text: string;
  patientContextJson?: string | null;
}) {
  return httpPost<{ userTranscript: RealtimeVoiceTranscript; aiTranscript: RealtimeVoiceTranscript; escalationReason: string | null; aiProvider: string; aiLatencyMs: number }>(
    "/api/realtime-ai/receptionist/test-message",
    input,
    { token, tenantId }
  );
}
